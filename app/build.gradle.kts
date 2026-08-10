plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    // Generates Parcelable implementations for @Parcelize classes. Required by
    // SelectedStation, which is held in rememberSaveable and therefore must be
    // storable in a Bundle. Ships with KGP, so no version is declared.
    id("kotlin-parcelize")
}

/**
 * Release keystore path, resolved once. Null/blank means "no signing material", which is
 * fine for a sideload APK but must NEVER produce a Play bundle — see the bundleRelease
 * guard after the android { } block.
 */
val releaseKeystorePath: String? = System.getenv("KEYSTORE_FILE")?.takeIf { it.isNotEmpty() }

android {
    namespace = "com.atvriders.wsprtxrx"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.atvriders.wsprtxrx"
        minSdk = 26
        targetSdk = 36
        // Driven from CI so each Play upload has a strictly-greater versionCode and a
        // tag-derived versionName; falls back to local defaults for local builds.
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("VERSION_NAME") ?: "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            // Populated from environment in CI when keystore secrets are present.
            if (releaseKeystorePath != null) {
                storeFile = file(releaseKeystorePath)
                storeType = "PKCS12"
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Play warns when a bundle ships native code without debug symbols. The only
            // native code here is MapLibre's prebuilt .so files; SYMBOL_TABLE packages
            // whatever symbols they retain so native crashes/ANRs are readable in Android
            // vitals. (Kotlin/Java traces are already covered — AGP bundles mapping.txt.)
            // SYMBOL_TABLE rather than FULL: function names are enough to triage, and FULL
            // adds debug info that would bloat the upload for no extra triage value.
            ndk { debugSymbolLevel = "SYMBOL_TABLE" }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Use the release keystore if CI provided one, else fall back to debug
            // signing so the sideload APK is always installable without secrets.
            // The AAB is protected separately — see the bundleRelease guard below.
            signingConfig = if (releaseKeystorePath != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

/**
 * B6: make it impossible to produce a debug-signed Play bundle.
 *
 * The buildType selector above falls back to the debug signing config when no keystore
 * is present, which is what put three debug-signed artifacts under the production
 * applicationId on public GitHub Releases. `assembleRelease` keeps that fallback on
 * purpose (a sideload APK must be installable without secrets), but the AAB is what gets
 * uploaded to Play — and Play rejects it with "You uploaded an APK or Android App Bundle
 * that was signed in debug mode", silently, only after a human has already uploaded.
 *
 * tasks.matching { }.configureEach avoids eager task realisation, and doFirst means IDE
 * sync, assembleRelease and testReleaseUnitTest are unaffected — only actually *running*
 * a bundle task fails.
 */
if (releaseKeystorePath == null) {
    tasks.matching { it.name == "bundleRelease" || it.name == "packageReleaseBundle" }
        .configureEach {
            doFirst {
                throw GradleException(
                    "Refusing to build a debug-signed Play bundle: KEYSTORE_FILE is unset. " +
                        "Set KEYSTORE_FILE/KEYSTORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD, or build " +
                        "assembleRelease if you only need a sideload APK.",
                )
            }
        }
}

// Room schema export location (exportSchema = true on AppDatabase). The generated v2
// schema JSON lands under app/schemas; committing it can follow in a later change.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.windowsize)
    implementation(libs.androidx.material3.adaptive.navigation.suite)
    implementation(libs.androidx.adaptive)
    implementation(libs.androidx.adaptive.layout)
    implementation(libs.androidx.adaptive.navigation)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.maplibre)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
