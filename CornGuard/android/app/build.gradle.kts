plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.ksp)
}

android {
    namespace = "com.cornguard.app"
    // compileSdk/targetSdk pinned to the SDK platform verified present on team machines.
    // Raise deliberately (and re-verify on all three developer machines) rather than drifting.
    // AndroidX library versions in gradle/libs.versions.toml are pinned to releases that still
    // target compileSdk 34 (see the versions comment there) so this stays buildable without every
    // developer needing to install a newer SDK Platform first.
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cornguard.app"
        // Android 8.0 (Oreo) is the safer baseline per claude/02_PROJECT_CONTEXT.md until the
        // manuscript's API-24-vs-8.0 wording is formally harmonized.
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0-sprint0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Release signing config is intentionally not wired here. Per
            // claude/10_ENV_GUIDE.md, release keystores/passwords must never be committed;
            // a signing config is injected from a controlled, non-committed location when a
            // release build is actually produced (Sprint 7 / feature/release-build).
        }
    }

    // Sprint 0 ships only debug/release build types (claude/05_DEVELOPMENT_PLAN.md Sprint 0:
    // "Prepare build variants for development and release where practical"). A dev/prod product
    // flavor dimension tied to Firebase environments belongs with the Firebase wiring itself
    // (feature/auth-service, Sprint 1) so the two environments' google-services.json and any
    // Firebase-dependent build config fields land together — see claude/10_ENV_GUIDE.md.

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.fragment.ktx)

    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
