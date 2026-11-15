plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.wirefinder.fivecm"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.wirefinder.fivecm"
        minSdk = 23
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
    }
}
