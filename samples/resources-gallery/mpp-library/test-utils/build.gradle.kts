/*
 * Copyright 2020 IceRock MAG Inc. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.multiplatform")
}

android {
    namespace = "com.icerockdev.library.testutils"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// disable android lint for test utils (no need here)
tasks.matching { it.name.startsWith("lint") }.configureEach { enabled = false }

kotlin {
    jvmToolchain(11)
    sourceSets {
        val commonMain by getting {
            dependencies {
                api("org.jetbrains.kotlin:kotlin-test:1.8.10")
                api("org.jetbrains.kotlin:kotlin-test-annotations-common:1.8.10")
                api("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.6.4")
                api(moko.resourcesTest)
            }
        }

        val jvmMain by getting {
            dependencies {
                api("org.jetbrains.kotlin:kotlin-test-junit:1.8.10")
            }
        }

        val androidMain by getting {
            dependencies {
                api("org.jetbrains.kotlin:kotlin-test-junit:1.8.10")
                api("androidx.test:core:1.5.0")
                api("org.robolectric:robolectric:4.10.3")
                api("junit:junit:4.13.2")
            }
        }
    }
}
