plugins {
    alias(libs.plugins.multiplatform).apply(false)
    alias(libs.plugins.compose).apply(false)
    alias(libs.plugins.android.application).apply(false)
}

buildscript{
    dependencies {
        classpath(moko.resourcesGradlePlugin)
    }
}