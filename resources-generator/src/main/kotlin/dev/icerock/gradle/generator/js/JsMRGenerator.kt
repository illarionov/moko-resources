/*
 * Copyright 2022 IceRock MAG Inc. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.icerock.gradle.generator.js

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec
import dev.icerock.gradle.generator.MRGenerator
import dev.icerock.gradle.tasks.GenerateMokoKarmaConfig
import dev.icerock.gradle.tasks.GenerateMokoWebpackConfig
import dev.icerock.gradle.tasks.MergeMokoJsResourcesTask
import dev.icerock.gradle.utils.calculateResourcesHash
import dev.icerock.gradle.utils.capitalizeAscii
import dev.icerock.gradle.utils.dependsOnProcessResources
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrCompilation
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget
import org.jetbrains.kotlin.gradle.targets.js.npm.npmProject
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack
import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile
import java.io.File

class JsMRGenerator(
    generatedDir: Provider<Directory>,
    sourceSet: SourceSet,
    mrSettings: MRSettings,
    generators: List<Generator>,
    private val compilation: KotlinJsIrCompilation,
) : MRGenerator(
    generatedDir = generatedDir,
    sourceSet = sourceSet,
    mrSettings = mrSettings,
    generators = generators
) {
    private val flattenClassName: Provider<String> = mrSettings.packageName
        .map { it.replace(".", "") }
    override val resourcesGenerationDir: Provider<Directory> = outputDir.zip(flattenClassName) { outputDir, className ->
        outputDir.dir(className).dir("res")
    }
    private val executableResourcesDir: Provider<Directory> = generatedDir.zip(flattenClassName) { root, className ->
        root.dir(sourceSet.name + "-executable").dir(className).dir("res")
    }

    private val project: Project get() = compilation.project
    private val projectLayout: ProjectLayout get() = project.layout
    private val tasks: TaskContainer get() = project.tasks

    override fun getMRClassModifiers(): Array<KModifier> = arrayOf(KModifier.ACTUAL)

    override fun processMRClass(mrClass: TypeSpec.Builder) {
        val resourcesGenerationDir = resourcesGenerationDir.get().asFile

        mrClass.addProperty(
            PropertySpec.builder("contentHash", STRING, KModifier.PRIVATE)
                .initializer("%S", resourcesGenerationDir.calculateResourcesHash())
                .build()
        )

        val stringsLoaderInitializer = buildList {
            val stringsObjectLoader = mrClass
                .typeSpecs
                .find { it.name == "strings" }
                ?.propertySpecs
                ?.find { it.name == "stringsLoader" }

            val pluralsObjectLoader = mrClass
                .typeSpecs
                .find { it.name == "plurals" }
                ?.propertySpecs
                ?.find { it.name == "stringsLoader" }

            if (stringsObjectLoader != null) {
                add("strings.stringsLoader")
            }
            if (pluralsObjectLoader != null) {
                add("plurals.stringsLoader")
            }
        }.takeIf(List<*>::isNotEmpty)
            ?.joinToString(separator = " + ")

        if (stringsLoaderInitializer != null) {
            mrClass.addProperty(
                PropertySpec.builder(
                    "stringsLoader",
                    ClassName("dev.icerock.moko.resources.provider", "RemoteJsStringLoader"),
                ).initializer(stringsLoaderInitializer)
                    .build()
            )
        }
    }

    override fun apply(generationTask: Task, project: Project) {
        tasks.withType<Kotlin2JsCompile>().configureEach {
            it.dependsOn(generationTask)
        }
        setupKLibResources(generationTask)

        // Declare task ':web-app:generateMRcommonMain' as an input of ':web-app:jsSourcesJar'.
        tasks.withType<Jar>().configureEach {
            it.dependsOn(generationTask)
        }

        val mergeResourcesTask = registerMergeResourcesTask()
        val generateWebpackConfigTask = GenerateMokoWebpackConfig.registerTask(
            tasks = tasks,
            projectLayout = projectLayout,
            npmProject = compilation.npmProject,
            resourcesDir = executableResourcesDir,
        )
        val generateKarmaConfigTask = GenerateMokoKarmaConfig.registerTask(tasks, projectLayout)
        project.tasks.withType<KotlinWebpack>().configureEach {
            it.dependsOn(generateWebpackConfigTask, mergeResourcesTask)
        }
        project.tasks.withType<KotlinJsTest>().configureEach {
            it.dependsOn(generateWebpackConfigTask, generateKarmaConfigTask, mergeResourcesTask)
        }

        dependsOnProcessResources(
            project = project,
            sourceSet = sourceSet,
            task = generationTask,
            shouldExcludeGenerated = true
        )
    }

    private fun setupKLibResources(generationTask: Task) {
        val taskProvider = compilation.compileTaskProvider
        taskProvider.configure { compileTask ->
            compileTask.dependsOn(generationTask)
            val action = CopyResourcesToKLibAction(resourcesGenerationDir)
            @Suppress("UNCHECKED_CAST")
            compileTask.doLast(action as Action<in Task>)
        }
    }

    private fun registerMergeResourcesTask(): TaskProvider<MergeMokoJsResourcesTask> {
        val mergeResourcesTaskName = "merge${sourceSet.name.capitalizeAscii()}MokoResources"
        return tasks.register<MergeMokoJsResourcesTask>(mergeResourcesTaskName) {
            this.outputDirectory.set(executableResourcesDir)
            this.inputResourcesDirs.from(resourcesGenerationDir)

            val kotlinTarget: KotlinJsIrTarget = compilation.target as KotlinJsIrTarget
            kotlinTarget.compilations
                .all { compilation ->
                    this.inputKlibs.from(
                        compilation.runtimeDependencyFiles
                            .filter { file -> file.isFile && file.path.endsWith(".klib") }
                    )
                }
        }
    }

    class CopyResourcesToKLibAction(
        private val resourcesDirProvider: Provider<Directory>,
    ) : Action<Kotlin2JsCompile> {
        override fun execute(task: Kotlin2JsCompile) {
            val unpackedKLibDir: File = task.destinationDirectory.asFile.get()
            val defaultDir = File(unpackedKLibDir, "default")
            val resRepackDir = File(defaultDir, "resources")
            if (resRepackDir.exists().not()) return

            val resDir = File(resRepackDir, "moko-resources-js")
            resourcesDirProvider.get().asFile.copyRecursively(
                resDir,
                overwrite = true
            )
        }
    }

    companion object {
        const val STRINGS_JSON_NAME = "stringsJson"
        const val PLURALS_JSON_NAME = "pluralsJson"

        const val SUPPORTED_LOCALES_PROPERTY_NAME = "supportedLocales"
        const val STRINGS_FALLBACK_FILE_URL_PROPERTY_NAME = "stringsFallbackFileUrl"
        const val PLURALS_FALLBACK_FILE_URL_PROPERTY_NAME = "stringsFallbackFileUrl"
        const val LOCALIZATION_DIR = "localization"
    }
}
