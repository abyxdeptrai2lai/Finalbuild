plugins {
    id("com.android.library") version "8.2.0"
    id("org.jetbrains.kotlin.android") version "1.9.20"
    id("com.aliucord.gradle") version "2.3.1"
}

android {
    namespace = "com.github.yournamehere"
    compileSdk = 34
    defaultConfig {
        minSdk = 26
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
