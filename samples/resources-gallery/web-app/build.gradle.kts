import dev.icerock.gradle.tasks.GenerateMokoWebpackConfig
import dev.icerock.gradle.tasks.MergeMokoJsResourcesTask

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("dev.icerock.mobile.multiplatform-resources")
}

kotlin {
    js(IR) {
        browser()
        binaries.executable()
    }
    sourceSets {
        commonMain {
            dependencies {
                implementation(moko.resources)
                implementation(project(":mpp-library"))

                implementation(compose.html.core)
                implementation(compose.runtime)
            }
        }
    }
}

multiplatformResources {
    multiplatformResourcesPackage = "com.icerockdev.app"
}

//tasks.withType<GenerateMokoWebpackConfig>().configureEach {
//    enabled = false
//}