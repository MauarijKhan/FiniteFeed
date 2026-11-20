plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.feedcap.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.feedcap.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        // Test build: Pro features unlocked, installs next to a future store build.
        debug {
            applicationIdSuffix = ".test"
            versionNameSuffix = "-pro-test"
            buildConfigField("boolean", "PRO_UNLOCKED", "true")
            resValue("string", "app_name", "FeedCap Pro Test")
        }
        release {
            buildConfigField("boolean", "PRO_UNLOCKED", "false")
            resValue("string", "app_name", "FeedCap")
            isMinifyEnabled = false
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
