import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.ksp)
    // @Serializable DTOs for Convex query results (data/remote/convex).
    alias(libs.plugins.kotlin.serialization)
    // Reads app/google-services.json. Firebase is used for push delivery (FCM) only — the
    // database, auth and file storage are Convex (see backend/README.md).
    alias(libs.plugins.google.services)
}

// Convex deployment URL, per developer: set `convex.url=https://<name>.convex.cloud` in
// android/local.properties (or the CONVEX_URL env var). Left blank, the app still builds and the
// offline scan/history/treatment path works; online features report that it's not configured.
val convexUrl: String = Properties().run {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    getProperty("convex.url") ?: System.getenv("CONVEX_URL") ?: ""
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

        buildConfigField("String", "CONVEX_URL", "\"$convexUrl\"")
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

    // Only debug/release, no dev/prod product flavor dimension yet. app/google-services.json is
    // the dev Firebase project (used for FCM push only); the Convex URL comes from local.properties above.

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
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

    // TFLite's Interpreter mmaps the model file directly out of the APK — it must stay
    // uncompressed or that fails at load time.
    androidResources {
        noCompress += "tflite"
    }

    // Exported Room schemas, read by MigrationTestHelper in AppDatabaseMigrationTest.
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
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
    implementation(libs.androidx.swiperefreshlayout)
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

    // Real on-device inference — TfliteCornLeafClassifier. The bundled assets/model.tflite needs
    // TFLite 2.17+; see "Disease model" in android/README.md.
    implementation(libs.tensorflow.lite)
    // Reads camera photo EXIF orientation so scans are classified upright (ScanImageDecoder).
    implementation(libs.androidx.exifinterface)

    // Convex: database, auth and file storage. The AAR must be pulled with its transitive
    // dependencies (JNA for the native client core).
    implementation("dev.convex:android-convexmobile:${libs.versions.convexMobile.get()}@aar") {
        isTransitive = true
    }
    implementation(libs.kotlinx.serialization.json)

    // Image loading: community post photos (Convex storage URLs) and scan thumbnails (local files).
    implementation(libs.coil)

    // Outbreak Heatmap: serves the bundled Leaflet page and boundary GeoJSON to the WebView.
    implementation(libs.androidx.webkit)

    // Firebase is kept for push delivery only (FCM). The BoM pins its version.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging.ktx)
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
