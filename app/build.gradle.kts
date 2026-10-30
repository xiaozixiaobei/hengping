plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.hengping.standby"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hengping.standby"
        minSdk = 26
        targetSdk = 34
        versionCode = providers.gradleProperty("versionCode").orElse("4").get().toInt()
        versionName = "0.3.1"
    }

    val releaseKeystore = System.getenv("ANDROID_KEYSTORE_PATH")
    if (!releaseKeystore.isNullOrBlank()) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = "hengping"
                keyPassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
            }
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("release")
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
