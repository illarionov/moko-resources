/*
 * Copyright 2020 IceRock MAG Inc. Use of this source code is governed by the Apache 2.0 license.
 */

import dev.icerock.gradle.MRVisibility

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.multiplatform")
    id("dev.icerock.mobile.multiplatform-resources")
}

android {
    namespace = "com.icerockdev.library.nested"
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "17"
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    commonMainApi(moko.resources)
}

multiplatformResources {
    multiplatformResourcesPackage = "com.icerockdev.library.nested"
    multiplatformResourcesClassName = "NestedMR"
    multiplatformResourcesVisibility = MRVisibility.Internal
}
