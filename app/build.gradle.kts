import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Release signing stays out of the repo: a properties file with storeFile, storePassword,
 * keyAlias and keyPassword, from -Pandaime.signing=<file> or ~/.andaime-release/keystore.properties.
 * Without one, the release APK is built unsigned.
 */
val releaseSigning: Properties? =
    ((findProperty("andaime.signing") as String?)?.let(::file)
        ?: File(System.getProperty("user.home"), ".andaime-release/keystore.properties"))
        .takeIf { it.exists() }
        ?.let { f -> Properties().apply { f.inputStream().use { load(it) } } }

android {
    namespace = "org.techarea.andaime"
    // Compose 1.12 (BOM 2026.09) requires compiling against 37; targetSdk stays 36
    compileSdk = 37

    defaultConfig {
        applicationId = "org.techarea.andaime"
        // 24 so the Xperia Z4 Tablet (Android 7.1.1, API 25) can run it
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        releaseSigning?.let { p ->
            create("release") {
                storeFile = file(p.getProperty("storeFile"))
                storePassword = p.getProperty("storePassword")
                keyAlias = p.getProperty("keyAlias")
                keyPassword = p.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":engine"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime)
    implementation(libs.savedstate)

    // AGP runs unit tests on JUnit 4
    testImplementation(kotlin("test-junit"))
}
