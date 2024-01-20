/*
 * Copyright 2024 IceRock MAG Inc. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.icerock.gradle.utils

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.api.provider.Provider

internal fun Project.getAndroidRClassPackage(): Provider<String> {
    return provider {
        // before call android specific classes we should ensure that android plugin in classpath at all
        // it's required to support gradle projects without android target
        val isAndroidEnabled = listOf(
            "com.android.library",
            "com.android.application"
        ).any { project.plugins.findPlugin(it) != null }
        if (!isAndroidEnabled) return@provider null

        extensions.getByType(CommonExtension::class.java).namespace
    }
}

