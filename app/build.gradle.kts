plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// On GitHub Actions these come from the build itself: "owner/repo" and the pushed tag (v0.2.0 -> 0.2.0).
val repoSlug = System.getenv("GITHUB_REPOSITORY") ?: "KyleTheCat/KyleMCTools"
val tagVersion = if (System.getenv("GITHUB_REF_TYPE") == "tag") System.getenv("GITHUB_REF_NAME")?.removePrefix("v") else null
val appVersion = tagVersion ?: "0.4.0"
val appCode = appVersion.substringBefore('-').split('.').let { p ->
    (p.getOrNull(0)?.toIntOrNull() ?: 0) * 10000 + (p.getOrNull(1)?.toIntOrNull() ?: 0) * 100 + (p.getOrNull(2)?.toIntOrNull() ?: 0)
}

android {
    namespace = "dev.kyle.mctools"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.kyle.mctools"
        minSdk = 26
        targetSdk = 35
        versionCode = appCode
        versionName = appVersion
        buildConfigField("String", "GITHUB_REPO", "\"$repoSlug\"")
    }
    // One fixed key for every build, so a new APK installs over the old one as an update.
    signingConfigs {
        create("kmt") {
            storeFile = file("kmt.keystore")
            storePassword = "kylemctools"
            keyAlias = "kmt"
            keyPassword = "kylemctools"
        }
    }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("kmt") }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("kmt")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":ui"))
    implementation(project(":tools:geyser-converter"))
    implementation(project(":tools:plugin-config"))
    implementation(project(":tools:pack-optimizer"))
    implementation(project(":tools:pack-protector"))
    implementation(project(":tools:meg-converter"))
    implementation(project(":tools:version-converter"))
    implementation(project(":tools:deluxemenus-editor"))
    implementation(project(":tools:shop-editor"))
}
