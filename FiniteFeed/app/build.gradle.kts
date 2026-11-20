plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.feedcap.app"
    compileSdk = 37

    // CI passes its run number, so every published build has a higher versionCode and installs as an update.
    val ciBuild = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()

    defaultConfig {
        applicationId = "com.feedcap.app"
        minSdk = 26
        targetSdk = 36
        versionCode = ciBuild ?: 1
        versionName = "0.1.0"
    }

    // CI signs with one fixed key (from repo secrets) so each new APK can update the last one.
    // Local builds keep Android's normal debug key.
    val ciKeystore = System.getenv("FEEDCAP_KEYSTORE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (ciKeystore != null) {
            create("ci") {
                storeFile = ciKeystore
                storePassword = System.getenv("FEEDCAP_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("FEEDCAP_KEY_ALIAS")
                keyPassword = System.getenv("FEEDCAP_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        // Test build: Pro features unlocked, installs next to a future store build.
        debug {
            if (ciKeystore != null) signingConfig = signingConfigs.getByName("ci")
            applicationIdSuffix = ".test"
            versionNameSuffix = "-pro-test"
            buildConfigField("boolean", "PRO_UNLOCKED", "true")
            resValue("string", "app_name", "Finite Feed Pro Test")
        }
        // The finished app, published on GitHub. Free until billing exists, so Pro is unlocked.
        release {
            if (ciKeystore != null) signingConfig = signingConfigs.getByName("ci")
            buildConfigField("boolean", "PRO_UNLOCKED", "true")
            resValue("string", "app_name", "Finite Feed")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
