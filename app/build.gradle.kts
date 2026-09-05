import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val releaseStoreFile = providers.gradleProperty("SNIPPET_STORE_FILE").orNull
val releaseStorePassword = providers.gradleProperty("SNIPPET_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.gradleProperty("SNIPPET_KEY_ALIAS").orNull
val releaseKeyPassword = providers.gradleProperty("SNIPPET_KEY_PASSWORD").orNull
val hasReleaseKeystore =
    listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
        .all { !it.isNullOrBlank() }

android {
    namespace = "dev.snippet.tv"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.snippet.tv"
        minSdk = 25
        targetSdk = 34
        versionCode = 2
        versionName = "1.1.0"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            // Minification stays off: nothing here is size-critical for a sideload,
            // and it removes any risk of R8 stripping kotlinx-serialization adapters.
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Falls back to the debug keystore so `./gradlew assembleRelease` always
            // produces an installable APK; set the SNIPPET_* properties to ship.
            signingConfig =
                if (hasReleaseKeystore) signingConfigs.getByName("release")
                else signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.tv.material)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
}
