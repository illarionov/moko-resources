/*
 * Copyright 2024 IceRock MAG Inc. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("multiplatform-library-convention")
}

kotlin {
    watchosX64()
    watchosArm32()
    watchosArm64()
    watchosSimulatorArm64()

    sourceSets {
        val commonMain by getting

        val watchosMain by creating {
            dependsOn(commonMain)
        }
        val watchosX64Main by getting {
            dependsOn(watchosMain)
        }
        val watchosArm32Main by getting{
            dependsOn(watchosMain)
        }
        val watchosArm64Main by getting{
            dependsOn(watchosMain)
        }
        val watchosSimulatorArm64Main by getting{
            dependsOn(watchosMain)
        }
    }
}

