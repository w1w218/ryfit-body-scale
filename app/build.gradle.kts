import java.util.Properties
import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.wcgarden.bodyscale"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.wcgarden.bodyscale"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // 正式签名：仅在 local.properties 提供签名配置时生效
    // （该文件已被 .gitignore 排除；他人 clone 后 release 保持无签名，可自行配置）
    signingConfigs {
        create("release") {
            val props = Properties().apply {
                val f = File(rootProject.rootDir, "local.properties")
                if (f.exists()) load(f.inputStream())
            }
            val sf = props.getProperty("ryfit.storeFile")
            if (sf != null) {
                storeFile = file(sf)
                storePassword = props.getProperty("ryfit.storePassword")
                keyAlias = props.getProperty("ryfit.keyAlias")
                keyPassword = props.getProperty("ryfit.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val sc = signingConfigs.getByName("release")
            if (sc.storeFile != null) signingConfig = sc
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    testImplementation("junit:junit:4.13.2")
}
