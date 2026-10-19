import java.util.Properties

plugins {
    id("com.android.application")
}

// 本地签名配置放在 release-signing/keystore.properties（已 gitignore）；
// CI 从环境变量读取，密钥通过 GitHub Secrets 注入。
val keystoreProperties = Properties().apply {
    val file = rootProject.file("release-signing/keystore.properties")
        .takeIf { it.isFile }
        ?: rootProject.file("keystore.properties")
    if (file.isFile) file.inputStream().use(::load)
}

android {
    namespace = "io.github.qianchenjun.alipaylite"
    compileSdk = 36

    defaultConfig {
        // 与 GitHub 用户名命名空间一致，LSPosed 模块仓库的自动审核要求 io.github.<username>.*
        applicationId = "io.github.qianchenjun.alipaylite"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "1.0.2"
    }

    signingConfigs {
        create("release") {
            val storePath = System.getenv("RELEASE_KEYSTORE_PATH")
                ?: keystoreProperties.getProperty("storeFile")
            if (!storePath.isNullOrBlank()) {
                val store = rootProject.file(storePath)
                if (store.isFile) {
                    storeFile = store
                    storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                        ?: keystoreProperties.getProperty("storePassword")
                    keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                        ?: keystoreProperties.getProperty("keyAlias")
                    keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
                        ?: keystoreProperties.getProperty("keyPassword")
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 没有签名材料时回退 debug 签名，保证 `assembleRelease` 在本地也能跑通。
            signingConfig = signingConfigs.findByName("release")?.takeIf { it.storeFile != null }
                ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    // 现代 libxposed API（LSPosed 2.x）。运行时由框架提供，绝不打包进 APK。
    compileOnly("io.github.libxposed:api:102.0.0")
}
