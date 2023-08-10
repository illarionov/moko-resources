/*
 * Copyright 2023 IceRock MAG Inc. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.icerock.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.targets.js.npm.NpmProject

abstract class GenerateMokoWebpackConfig : DefaultTask() {

    @get:OutputFile
    abstract val outputWebpackConfig: RegularFileProperty

    @get:Input
    abstract val webpackResourcesPath: Property<String>

    @TaskAction
    fun generate() {
        val webpackConfig = outputWebpackConfig.get().asFile
        val webpackResourcesDir = webpackResourcesPath.get().replace("\\", "\\\\")

        webpackConfig.parentFile.mkdirs()
        webpackConfig.writeText(
            // language=js
            """
// noinspection JSUnnecessarySemicolon
;(function(config) {
    const path = require('path');
    const MiniCssExtractPlugin = require('mini-css-extract-plugin');

    const mokoResourcePath = path.resolve("$webpackResourcesDir");

    config.module.rules.push(
        {
            test: /\.(.*)/,
            resource: [
                path.resolve(mokoResourcePath, "files"),
                path.resolve(mokoResourcePath, "images"),
                path.resolve(mokoResourcePath, "localization"),
            ],
            type: 'asset/resource'
        }
    );

    config.plugins.push(new MiniCssExtractPlugin())
    config.module.rules.push(
        {
            test: /\.css${'$'}/,
            resource: [
                path.resolve(mokoResourcePath, "fonts"),
            ],
            use: ['style-loader', 'css-loader']
        }
    )

    config.module.rules.push(
        {
            test: /\.(otf|ttf)?${'$'}/,
            resource: [
                path.resolve(mokoResourcePath, "fonts"),
            ],
            type: 'asset/resource',
        }
    )

    config.resolve.modules.push(mokoResourcePath);
})(config);
            """.trimIndent()
        )
    }

    internal companion object {
        fun registerTask(
            tasks: TaskContainer,
            projectLayout: ProjectLayout,
            npmProject: NpmProject,
            resourcesDir: Provider<Directory>
        ): TaskProvider<GenerateMokoWebpackConfig> = tasks.register<GenerateMokoWebpackConfig>(
            "generateMokoWebpackConfig"
        ) {
            outputWebpackConfig.convention(
                projectLayout.projectDirectory.file("webpack.config.d/moko-resources-generated.js")
            )
            webpackResourcesPath.set(
                resourcesDir.map {
                    val npmWorkDir = npmProject.dir
                    val resourcesFilePath = it.asFile
                    resourcesFilePath.relativeToOrNull(npmWorkDir)?.path ?: resourcesFilePath.canonicalPath
                }
            )
        }
    }
}
