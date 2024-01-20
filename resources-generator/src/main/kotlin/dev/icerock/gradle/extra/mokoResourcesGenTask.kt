/*
 * Copyright 2024 IceRock MAG Inc. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("Filename")

package dev.icerock.gradle.extra

import dev.icerock.gradle.MultiplatformResourcesPluginExtension
import dev.icerock.gradle.tasks.GenerateMultiplatformResourcesTask
import dev.icerock.gradle.utils.dependsOnObservable
import dev.icerock.gradle.utils.getAndroidRClassPackage
import dev.icerock.gradle.utils.isStrictLineBreaks
import org.gradle.api.Project
import org.gradle.api.UnknownTaskException
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.named
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.tooling.core.extrasKeyOf
import java.io.File

private fun mokoResourcesGenTaskKey() =
    extrasKeyOf<TaskProvider<GenerateMultiplatformResourcesTask>>("moko-resources-generate-task")

internal fun KotlinSourceSet.getOrRegisterGenerateResourcesTask(
    mrExtension: MultiplatformResourcesPluginExtension
): TaskProvider<GenerateMultiplatformResourcesTask> {
    val currentProvider: TaskProvider<GenerateMultiplatformResourcesTask>? =
        this.extras[mokoResourcesGenTaskKey()]
    val resourcesSourceDirectory: SourceDirectorySet = getOrCreateResourcesSourceDirectory()

    if (currentProvider != null) {
        currentProvider.configure {
            it.ownResources.setFrom(resourcesSourceDirectory.sourceDirectories)
        }
        return currentProvider
    }

    val genTask: TaskProvider<GenerateMultiplatformResourcesTask> = getOrRegisterGenerateTask(
        kotlinSourceSetName = this.name,
        project = project,
        mrExtension = mrExtension
    )
    genTask.configure {
        it.ownResources.setFrom(resourcesSourceDirectory.sourceDirectories)
    }

    configureLowerDependencies(
        kotlinSourceSet = this,
        genTask = genTask
    )

    configureUpperDependencies(
        kotlinSourceSet = this,
        resourcesSourceSetName = this.name,
        resourcesSourceDirectory = resourcesSourceDirectory,
        mrExtension = mrExtension
    )

    configureTaskDependencies(
        kotlinSourceSet = this,
        genTask = genTask,
        mrExtension = mrExtension
    )

    this.extras[mokoResourcesGenTaskKey()] = genTask

    return genTask
}

internal fun getOrRegisterGenerateTask(
    kotlinSourceSetName: String,
    project: Project,
    mrExtension: MultiplatformResourcesPluginExtension,
    setupOutputDirs: Boolean = true,
): TaskProvider<GenerateMultiplatformResourcesTask> {
    val generateTaskName = getGenerateTaskName(kotlinSourceSetName)
    return try {
        project.tasks.named<GenerateMultiplatformResourcesTask>(generateTaskName)
    } catch (@Suppress("SwallowedException") ex: UnknownTaskException) {
        registerGenerateTask(kotlinSourceSetName, project, mrExtension, setupOutputDirs)
    }
}

private fun registerGenerateTask(
    kotlinSourceSetName: String,
    project: Project,
    mrExtension: MultiplatformResourcesPluginExtension,
    setupOutputDirs: Boolean = true,
): TaskProvider<GenerateMultiplatformResourcesTask> {
    val generateTaskName = getGenerateTaskName(kotlinSourceSetName)
    val generatedMokoResourcesDir = File(
        project.layout.buildDirectory.get().asFile,
        "generated/moko-resources"
    )

    val taskProvider: TaskProvider<GenerateMultiplatformResourcesTask> = project.tasks.register(
        generateTaskName,
        GenerateMultiplatformResourcesTask::class.java
    ) { generateTask ->
        generateTask.sourceSetName.set(kotlinSourceSetName)

        generateTask.iosBaseLocalizationRegion.set(mrExtension.iosBaseLocalizationRegion)
        generateTask.resourcesClassName.set(mrExtension.resourcesClassName)
        generateTask.resourcesPackageName.set(mrExtension.resourcesPackage)
        generateTask.resourcesVisibility.set(mrExtension.resourcesVisibility)
        generateTask.androidRClassPackage.set(project.getAndroidRClassPackage())
        generateTask.strictLineBreaks.set(project.provider { project.isStrictLineBreaks })
        generateTask.outputMetadataFile.set(
            File(
                File(generatedMokoResourcesDir, "metadata"),
                "${kotlinSourceSetName}-metadata.json"
            )
        )
        if (setupOutputDirs) {
            val sourceSetResourceDir = File(generatedMokoResourcesDir, kotlinSourceSetName)
            generateTask.outputAssetsDir.set(File(sourceSetResourceDir, "assets"))
            generateTask.outputResourcesDir.set(File(sourceSetResourceDir, "res"))
            generateTask.outputSourcesDir.set(File(sourceSetResourceDir, "src"))
        }

        // by default source set will be common
        generateTask.platformType.set(KotlinPlatformType.common.name)

        generateTask.onlyIf("generation on Android supported only for main flavor") { task ->
            task as GenerateMultiplatformResourcesTask

            val platform: String = task.platformType.get()
            if (platform != KotlinPlatformType.androidJvm.name) return@onlyIf true

            val flavor: String = task.androidSourceSetName.get()
            flavor in listOf("main", "test", "androidTest")
        }
    }

    return taskProvider
}

private fun getGenerateTaskName(kotlinSourceSetName: String): String = "generateMR$kotlinSourceSetName"

private fun configureLowerDependencies(
    kotlinSourceSet: KotlinSourceSet,
    genTask: TaskProvider<GenerateMultiplatformResourcesTask>,
) {
    kotlinSourceSet.dependsOnObservable.forAll { dependsSourceSet ->
        val resourcesDir: SourceDirectorySet = dependsSourceSet
            .getOrCreateResourcesSourceDirectory()

        genTask.configure {
            val files: Set<File> = resourcesDir.srcDirs
            it.lowerResources.from(files)
        }

        configureLowerDependencies(
            kotlinSourceSet = dependsSourceSet,
            genTask = genTask
        )
    }
}

private fun configureUpperDependencies(
    kotlinSourceSet: KotlinSourceSet,
    resourcesSourceSetName: String,
    resourcesSourceDirectory: SourceDirectorySet,
    mrExtension: MultiplatformResourcesPluginExtension,
) {
    kotlinSourceSet.dependsOnObservable.forAll { dependsSourceSet ->
        val dependsGenTask: TaskProvider<GenerateMultiplatformResourcesTask> = dependsSourceSet
            .getOrRegisterGenerateResourcesTask(mrExtension)

        dependsGenTask.configure {
            val files: Set<File> = resourcesSourceDirectory.srcDirs
            it.upperSourceSets.put(resourcesSourceSetName, kotlinSourceSet.project.files(files))
        }

        configureUpperDependencies(
            kotlinSourceSet = dependsSourceSet,
            resourcesSourceSetName = resourcesSourceSetName,
            resourcesSourceDirectory = resourcesSourceDirectory,
            mrExtension = mrExtension
        )
    }
}

private fun configureTaskDependencies(
    kotlinSourceSet: KotlinSourceSet,
    genTask: TaskProvider<GenerateMultiplatformResourcesTask>,
    mrExtension: MultiplatformResourcesPluginExtension,
) {
    kotlinSourceSet.dependsOnObservable.forAll { dependsSourceSet ->
        val dependsGenTask: TaskProvider<GenerateMultiplatformResourcesTask> = dependsSourceSet
            .getOrRegisterGenerateResourcesTask(mrExtension)

        genTask.configure { resourceTask ->
            resourceTask.inputMetadataFiles.from(dependsGenTask.flatMap { it.outputMetadataFile })
        }

        configureTaskDependencies(
            kotlinSourceSet = dependsSourceSet,
            genTask = genTask,
            mrExtension = mrExtension
        )
    }
}
