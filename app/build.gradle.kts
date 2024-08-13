/*
 * Copyright (c) 2021 2bllw8
 * SPDX-License-Identifier: GPL-3.0-only
 */
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    id("org.jetbrains.kotlin.android")
    id("com.diffplug.spotless") version "6.5.1"
}

android {
    compileSdk = rootProject.extra["targetSdkVersion"] as Int
    defaultConfig {
        minSdk = rootProject.extra["minSdkVersion"] as Int
        targetSdk = rootProject.extra["targetSdkVersion"] as Int
        versionCode = 1723581000
        versionName = "2024.08.13"
        applicationId = "alt.nainapps.aer"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildFeatures {
        buildConfig = false
        compose = true
    }

    compileOptions {
        sourceCompatibility = rootProject.extra["sourceCompatibilityVersion"] as JavaVersion
        targetCompatibility = rootProject.extra["targetCompatibilityVersion"] as JavaVersion
    }

    dependenciesInfo {
        includeInApk = false
    }

    signingConfigs {
        if (rootProject.ext.get("keyStoreFile") != null && (rootProject.ext.get("keyStoreFile") as File).exists()) {
            create("anemo") {
                storeFile = file(rootProject.ext.get("keyStoreFile") as String)
                storePassword = rootProject.ext.get("keyStorePassword") as String
                keyAlias = rootProject.ext.get("keyAlias") as String
                keyPassword = rootProject.ext.get("keyPassword") as String
            }
        }
    }

    buildTypes {
        val useAnemoConfig = rootProject.ext.get("keyStoreFile") != null && (rootProject.ext.get("keyStoreFile") as File).exists()

        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")

            if (useAnemoConfig) {
                signingConfig = signingConfigs.getByName("anemo")
            }
        }
        getByName("debug") {
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("debug")
            if (useAnemoConfig) {
                signingConfig = signingConfigs.getByName("anemo")
            }
        }
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    namespace = "alt.nainapps.aer"
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.annotation)
    implementation(libs.eitherLib)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

afterEvaluate {
    val spotlessCheck = tasks.named("spotlessCheck")
    if (spotlessCheck.isPresent) {
        tasks.withType<JavaCompile>().configureEach {
            finalizedBy(spotlessCheck)
        }
    }
}
