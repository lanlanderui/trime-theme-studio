import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

/**
 * 发布签名。
 *
 * `keystore.properties` 不进 git：换机器要重新生成证书，而换了证书的包不能覆盖
 * 安装旧包（Android 认为签名不同的是另一个应用）。文件不存在时 release 退回
 * debug 签名并打印一行警告——让 `assembleRelease` 在干净检出上也能跑通，但不假装
 * 那是可以发布的包。
 */
val keystoreProperties =
    Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.isFile) file.inputStream().use { load(it) }
    }
val hasReleaseKey = keystoreProperties.getProperty("storeFile") != null
if (!hasReleaseKey) {
    logger.lifecycle("no keystore.properties: release build will be signed with the debug key")
}

android {
    namespace = "com.trime.editor"
    compileSdk = 37
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.trime.editor"
        minSdk = 21
        targetSdk = 37
        versionCode = 5
        versionName = "0.4.1"

        multiDexEnabled = true
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    // theme-core 是纯 JVM 模块，它的 assets 不会进 APK。新建主题要用随包模板，
    // 直接把那一份目录挂成 app 的 assets 源集：不复制文件，模板改一处两边同步。
    sourceSets {
        getByName("main") {
            assets.srcDirs("src/main/assets", "../theme-core/src/main/assets")
        }
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // 混淆留到真有需要保护的代码时；现在打开只会让崩溃堆栈难读。
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    // 版本号是「与输入法配对」的唯一依据，见 README 的配对表。
    androidResources {
        // 打包时不再压缩主题模板之类的文本资源，装到设备上仍是原样。
        noCompress += listOf("yaml", "yml")
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

dependencies {
    implementation(project(":editor-ui"))
    implementation(libs.androidx.activity)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.timber)

    testImplementation(libs.junit)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
}

// 把 release 包放进 dist/，文件名带版本：装到平板上以前，先能认出这是第几版。
tasks.register<Copy>("distApk") {
    group = "distribution"
    description = "Copies the release APK into dist/ under a versioned name."
    dependsOn("assembleRelease")
    from(layout.buildDirectory.dir("outputs/apk/release")) {
        include("*.apk")
    }
    into(rootProject.layout.projectDirectory.dir("dist"))
    rename { "同文编辑器-${android.defaultConfig.versionName}.apk" }
}
