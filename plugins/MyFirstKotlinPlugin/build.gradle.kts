plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.aliucord.plugin")
}

android {
    namespace = "com.github.yournamehere"
    compileSdk = 34
    defaultConfig {
        minSdk = 26
    }
    
    // ĐÂY LÀ CHỖ SỬA LỖI LỆCH JAVA/KOTLIN
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
}

aliucord {
    author("Freddy")
    description = "Paste button and Spam menu"
    version = "1.0.0"
    deploy.set(true)
}

dependencies {
    compileOnly("com.discord:discord:126021")
}
