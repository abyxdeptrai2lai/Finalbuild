plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.aliucord.plugin)
}

android {
    namespace = "com.github.yourusernamehere"
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
    compileOnly(libs.discord)
    compileOnly(libs.aliucord) // Bổ sung thêm dòng này theo template mới
    compileOnly(libs.kotlin.stdlib)
}
