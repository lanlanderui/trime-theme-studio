plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.trime.editor.ui"
    compileSdk = 37

    defaultConfig {
        minSdk = 21
    }

    buildFeatures {
        viewBinding = true
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

dependencies {
    api(project(":theme-editor"))
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    // api 而不是 implementation：StyleEditorFragment 是 PreferenceFragmentCompat，
    // 宿主（:app）要把它挂进 FragmentTransaction，就必然要在自己的编译类路径上看得见
    // 这个基类，否则 replace() 会报「实参是 StyleEditorFragment 但期望 Fragment」。
    api(libs.androidx.preference)
    implementation(libs.androidx.recyclerview)
    implementation(libs.flexbox)
    implementation(libs.splitties.views.dsl)
    implementation(libs.splitties.views.dsl.constraintlayout)
    implementation(libs.splitties.views.dsl.recyclerview)
    implementation(libs.splitties.views.recyclerview)
    implementation(libs.timber)
    // 工具条按钮的图标：与输入法同一个库、同一套 cmd_ 前缀
    implementation(libs.iconics.core)
    implementation(libs.community.material.typeface) {
        artifact { type = "aar" }
    }

    // ProofFormat 是纯字符串规则，能在 JVM 上跑：颜色写法一改就有一条用例
    // 跟着变，不用开模拟器看颜色对不对。
    testImplementation(libs.junit)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
}
