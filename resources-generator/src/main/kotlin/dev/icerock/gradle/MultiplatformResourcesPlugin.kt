/*
 * Copyright 2019 IceRock MAG Inc. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.icerock.gradle

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.HasAndroidTest
import com.android.build.api.variant.HasUnitTest
import com.android.build.api.variant.Sources
import com.android.build.api.variant.Variant
import dev.icerock.gradle.extra.getOrRegisterGenerateResourcesTask
import dev.icerock.gradle.extra.getOrRegisterGenerateTask
import dev.icerock.gradle.generator.platform.apple.setupAppleKLibResources
import dev.icerock.gradle.generator.platform.apple.setupCopyXCFrameworkResourcesTask
import dev.icerock.gradle.generator.platform.apple.setupExecutableResources
import dev.icerock.gradle.generator.platform.apple.setupFatFrameworkTasks
import dev.icerock.gradle.generator.platform.apple.setupFrameworkResources
import dev.icerock.gradle.generator.platform.apple.setupTestsResources
import dev.icerock.gradle.generator.platform.js.setupJsKLibResources
import dev.icerock.gradle.generator.platform.js.setupJsResources
import dev.icerock.gradle.tasks.GenerateMultiplatformResourcesTask
import dev.icerock.gradle.utils.capitalize
import dev.icerock.gradle.utils.kotlinSourceSetsObservable
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.sources.android.androidSourceSetInfoOrNull
import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile

open class MultiplatformResourcesPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val mrExtension: MultiplatformResourcesPluginExtension = project.extensions.create(
            name = "multiplatformResources",
            type = MultiplatformResourcesPluginExtension::class
        ).apply { setupConvention(project) }

        listOf(
            "com.android.application",
            "com.android.library"
        ).forEach {
            project.plugins.withId(it) {
                configureAndroidPlugin(project, mrExtension)
            }
        }

        project.plugins.withType(KotlinMultiplatformPluginWrapper::class) {
            val kmpExtension: KotlinMultiplatformExtension = project.extensions.getByType()

            configureKotlinTargetGenerator(
                project = project,
                mrExtension = mrExtension,
                kmpExtension = kmpExtension
            )

            setupCopyXCFrameworkResourcesTask(project = project)
            setupFatFrameworkTasks(project = project)
            registerGenerateAllResources(project = project)
        }
    }

    @Suppress("LongMethod")
    private fun configureKotlinTargetGenerator(
        project: Project,
        mrExtension: MultiplatformResourcesPluginExtension,
        kmpExtension: KotlinMultiplatformExtension,
    ) {
        kmpExtension.sourceSets.configureEach { kotlinSourceSet: KotlinSourceSet ->
            kotlinSourceSet.getOrRegisterGenerateResourcesTask(mrExtension)
        }

        kmpExtension.targets.configureEach { target ->
            if (target is KotlinNativeTarget) {
                setupExecutableResources(target = target)
                setupFrameworkResources(target = target)
                setupTestsResources(target = target)
            }

            target.compilations.configureEach { compilation ->
                compilation.kotlinSourceSetsObservable.forAll { sourceSet: KotlinSourceSet ->
                    val genTaskProvider = sourceSet.getOrRegisterGenerateResourcesTask(mrExtension)

                    genTaskProvider.configure {
                        it.platformType.set(target.platformType.name)

                        if (target is KotlinNativeTarget) {
                            it.konanTarget.set(target.konanTarget.name)
                        }
                    }

                    // Setup generated sourceSets, assets, resources as sourceSet of target
                    setupSourceSets(
                        target = target,
                        sourceSet = sourceSet,
                        genTaskProvider = genTaskProvider,
                        compilation = compilation
                    )

                    // Setup android specific tasks
                    setupAndroidTasks(
                        target = target,
                        sourceSet = sourceSet,
                        genTaskProvider = genTaskProvider,
                    )

                    compilation.compileTaskProvider.configure { compileTask: KotlinCompilationTask<*> ->
                        compileTask.dependsOn(genTaskProvider)

                        if (compileTask is Kotlin2JsCompile) {
                            setupJsResources(
                                compileTask = compileTask,
                                resourcesGenerationDir = genTaskProvider.flatMap {
                                    it.outputResourcesDir.asFile
                                },
                                projectDir = project.provider { project.projectDir }
                            )
                            setupJsKLibResources(
                                compileTask = compileTask,
                                resourcesGenerationDir = genTaskProvider.flatMap {
                                    it.outputResourcesDir.asFile
                                }
                            )
                        }
                    }

                    if (target is KotlinNativeTarget && target.konanTarget.family.isAppleFamily) {
                        val appleIdentifier: Provider<String> = mrExtension.resourcesPackage
                            .map { it + "." + compilation.name }

                        genTaskProvider.configure {
                            it.appleBundleIdentifier.set(appleIdentifier)
                        }

                        compilation.compileTaskProvider.configure { compileTask: KotlinCompilationTask<*> ->
                            compileTask as KotlinNativeCompile

                            setupAppleKLibResources(
                                compileTask = compileTask,
                                assetsDirectory = genTaskProvider.flatMap {
                                    it.outputAssetsDir.asFile
                                },
                                resourcesGenerationDir = genTaskProvider.flatMap {
                                    it.outputResourcesDir.asFile
                                },
                                iosLocalizationRegion = mrExtension.iosBaseLocalizationRegion,
                                acToolMinimalDeploymentTarget = mrExtension.acToolMinimalDeploymentTarget,
                                appleBundleIdentifier = appleIdentifier
                            )
                        }
                    }
                }
            }
        }
    }

    private fun registerGenerateAllResources(project: Project) {
        project.tasks.register("generateMR") {
            it.group = "moko-resources"
            it.dependsOn(project.tasks.withType<GenerateMultiplatformResourcesTask>())
        }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    private fun setupSourceSets(
        target: KotlinTarget,
        sourceSet: KotlinSourceSet,
        genTaskProvider: TaskProvider<GenerateMultiplatformResourcesTask>,
        compilation: KotlinCompilation<*>,
    ) {
        val project: Project = target.project

        sourceSet.kotlin.srcDir(genTaskProvider.map { it.outputSourcesDir })

        when (target.platformType) {
            KotlinPlatformType.jvm, KotlinPlatformType.js -> {
                sourceSet.resources.srcDir(genTaskProvider.map { it.outputResourcesDir })
                sourceSet.resources.srcDir(genTaskProvider.map { it.outputAssetsDir })
            }

            KotlinPlatformType.androidJvm -> Unit
            KotlinPlatformType.common, KotlinPlatformType.native, KotlinPlatformType.wasm -> Unit
        }
    }

    private fun configureAndroidPlugin(
        project: Project,
        mrExtension: MultiplatformResourcesPluginExtension,
    ) {
        project.extensions.configure(CommonExtension::class.java) {
            val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)
            androidComponents.onVariants { variant: Variant ->
                val mainGenTaskProvider = getOrRegisterGenerateTask(
                    kotlinSourceSetName = "androidMain",
                    project = project,
                    mrExtension = mrExtension,
                    setupOutputDirs = false
                )
                variant.sources.addTaskAsSource(mainGenTaskProvider)

                val variantGenTaskProvider = getOrRegisterGenerateTask(
                    kotlinSourceSetName = "android" + variant.name.capitalize(),
                    project = project,
                    mrExtension = mrExtension,
                    setupOutputDirs = false
                )
                variant.sources.addTaskAsSource(variantGenTaskProvider)

                (variant as? HasUnitTest)?.unitTest?.let { unitTestComponent ->
                    val variantGenTestTaskProvider = getOrRegisterGenerateTask(
                        kotlinSourceSetName = "androidUnitTest" + variant.name.capitalize(),
                        project = project,
                        mrExtension = mrExtension,
                        setupOutputDirs = false
                    )
                    unitTestComponent.sources.addTaskAsSource(variantGenTestTaskProvider)
                }
                (variant as? HasAndroidTest)?.androidTest?.let { androidTestComponent ->
                    val variantGenTestTaskProvider = getOrRegisterGenerateTask(
                        kotlinSourceSetName = "androidInstrumentedTest" + variant.name.capitalize(),
                        project = project,
                        mrExtension = mrExtension,
                        setupOutputDirs = false
                    )
                    androidTestComponent.sources.addTaskAsSource(variantGenTestTaskProvider)
                }
            }
        }
    }

    private fun Sources.addTaskAsSource(
        taskProvider: TaskProvider<GenerateMultiplatformResourcesTask>,
    ) {
        assets?.addGeneratedSourceDirectory(
            taskProvider,
            GenerateMultiplatformResourcesTask::outputAssetsDir
        )
        res?.addGeneratedSourceDirectory(
            taskProvider,
            GenerateMultiplatformResourcesTask::outputResourcesDir
        )
        java?.addGeneratedSourceDirectory(
            taskProvider,
            GenerateMultiplatformResourcesTask::outputSourcesDir
        )
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    private fun setupAndroidTasks(
        target: KotlinTarget,
        sourceSet: KotlinSourceSet,
        genTaskProvider: TaskProvider<GenerateMultiplatformResourcesTask>,
    ) {
        if (target !is KotlinAndroidTarget) return

        val androidSourceSetName = sourceSet.androidSourceSetInfoOrNull?.androidSourceSetName
            ?: throw GradleException("can't find android source set name for $sourceSet")

        // save android sourceSet name to skip build type specific tasks
        genTaskProvider.configure { it.androidSourceSetName.set(androidSourceSetName) }
    }
}
