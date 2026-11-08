plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.osfans.trime.theme.runtime"
    compileSdk = 37

    defaultConfig {
        minSdk = 21
    }
}

dependencies {
    api(project(":theme-core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.splitties.bitflags)
    implementation(libs.splitties.dimensions)
    implementation(libs.timber)
}
