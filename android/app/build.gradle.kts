plugins { id("com.android.application") }

android {
    namespace = "com.padremote"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.padremote"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies { testImplementation("junit:junit:4.13.2") }
