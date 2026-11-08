plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.osfans.trime.theme.editor"
    compileSdk = 37

    defaultConfig {
        minSdk = 21
    }
}

dependencies {
    api(project(":theme-runtime"))
    implementation(libs.kotlinx.coroutines)
    implementation(libs.timber)
}
