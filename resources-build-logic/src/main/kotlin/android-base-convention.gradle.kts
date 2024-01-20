import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension

/*
 * Copyright 2021 IceRock MAG Inc. Use of this source code is governed by the Apache 2.0 license.
 */

extensions.configure(CommonExtension::class.java) {
    compileSdk = 33

    defaultConfig {
        minSdk = 16
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

plugins.withType<ApplicationPlugin> {
    extensions.configure<ApplicationExtension>("android") {
        defaultConfig.targetSdk = 33
    }
}
