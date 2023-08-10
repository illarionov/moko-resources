/*
 * Copyright 2023 IceRock MAG Inc. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.icerock.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register

abstract class GenerateMokoKarmaConfig : DefaultTask() {
    @get:OutputFile
    abstract val outputKarmaConfig: RegularFileProperty

    @TaskAction
    fun generate() {
        val karmaConfig = outputKarmaConfig.get().asFile

        karmaConfig.parentFile.mkdirs()
        val pattern = "`\${output.path}/**/*`"

        karmaConfig.writeText(
            """
// workaround from https://github.com/ryanclark/karma-webpack/issues/498#issuecomment-790040818

const output = {
  path: require("os").tmpdir() + '/' + '_karma_webpack_' + Math.floor(Math.random() * 1000000),
}

const optimization = {
     runtimeChunk: true
}

config.set(
    {
        webpack: {... createWebpackConfig(), output, optimization},
        files: config.files.concat([{
                pattern: $pattern,
                watched: false,
                included: false,
            }]
        )
    }
)
            """.trimIndent()
        )
    }

    internal companion object {
        fun registerTask(
            tasks: TaskContainer,
            projectLayout: ProjectLayout,
        ): TaskProvider<GenerateMokoKarmaConfig> = tasks.register<GenerateMokoKarmaConfig>(
            "generateMokoKarmaConfig"
        ) {
            outputKarmaConfig.convention(
                projectLayout.projectDirectory.file("karma.config.d/moko-resources-generated.js")
            )
        }
    }
}
