/*
 * Copyright 2019 IceRock MAG Inc. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.multiplatform")
    id("dev.icerock.mobile.multiplatform-resources")
}

allprojects {
    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        kotlin {
            jvmToolchain(11)
            android()
            // ios()
            // iosSimulatorArm64()
            jvm()
            // macosX64()
            // macosArm64()
            js(IR) { browser() }

            explicitApi()

            sourceSets {
            }
        }
    }
}

android {
    namespace = "com.icerockdev.library"

    testOptions.unitTests.isIncludeAndroidResources = true

    lint.disable.add("ImpliedQuantity")
    lint.disable.add("MissingTranslation")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
}

dependencies {
    commonMainApi(moko.resources)
    commonMainImplementation(project(":mpp-library:nested-module"))
    commonMainImplementation(project(":mpp-library:empty-module"))

    commonTestImplementation(moko.resourcesTest)
    commonTestImplementation(project(":mpp-library:test-utils"))
}

multiplatformResources {
    multiplatformResourcesPackage = "com.icerockdev.library"
}
