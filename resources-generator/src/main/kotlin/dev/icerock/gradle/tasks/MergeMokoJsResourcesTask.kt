/*
 * Copyright 2023 IceRock MAG Inc. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.icerock.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.library.impl.KotlinLibraryLayoutImpl
import java.io.File

abstract class MergeMokoJsResourcesTask : DefaultTask() {

    @get:InputFiles
    @get:Classpath
    abstract val inputKlibs: ConfigurableFileCollection

    @get:InputFiles
    abstract val inputResourcesDirs: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val outputDir = outputDirectory.get().asFile
        outputDir.mkdirs()
        inputKlibs.forEach { copyResourcesFromLibraries(it, outputDir) }
        inputResourcesDirs.forEach {
            it.copyRecursively(
                outputDir,
                overwrite = true
            )
        }
    }

    private fun copyResourcesFromLibraries(
        inputFile: File,
        outputDir: File
    ) {
        if (inputFile.extension != "klib") return
        if (inputFile.exists().not()) return

        logger.info("copy resources from $inputFile into $outputDir")
        val klibKonan = org.jetbrains.kotlin.konan.file.File(inputFile.path)
        val klib = KotlinLibraryLayoutImpl(klib = klibKonan, component = "default")
        val layout = klib.extractingToTemp

        try {
            File(layout.resourcesDir.path, "moko-resources-js").copyRecursively(
                target = outputDir,
                overwrite = true
            )
        } catch (@Suppress("SwallowedException") exc: kotlin.io.NoSuchFileException) {
            project.logger.info("resources in $inputFile not found")
        } catch (@Suppress("SwallowedException") exc: java.nio.file.NoSuchFileException) {
            project.logger.info("resources in $inputFile not found (empty lib)")
        }
    }
}
