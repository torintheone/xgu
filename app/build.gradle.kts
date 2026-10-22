plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val appName: String = providers.gradleProperty("petri.appName").get()
val appVersion: String = providers.gradleProperty("petri.version").get()

// Release signing key from the environment (CI fills it from GitHub Secrets, see README).
val keystoreFile: String? = providers.environmentVariable("PETRI_KEYSTORE_FILE").orNull

android {
    namespace = "io.github.torintheone.petri"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.torintheone.petri"
        minSdk = 26
        targetSdk = 37
        versionCode = versionCodeOf(appVersion)
        versionName = appVersion
        resValue("string", "app_name", appName)
    }

    signingConfigs {
        if (keystoreFile != null) {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = requiredEnv("PETRI_KEYSTORE_PASSWORD")
                keyAlias = requiredEnv("PETRI_KEY_ALIAS")
                keyPassword = requiredEnv("PETRI_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: run {
                logger.warn(
                    "ВНИМАНИЕ: ключ подписи не настроен (секреты KEYSTORE_*). APK подписан временным " +
                        "debug-ключом и не установится поверх сборки, подписанной другим ключом.",
                )
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs {
            // SPEC: the APK has no native code. Compose brings androidx.graphics:graphics-path, whose JNI library
            // only backs Path segment iteration, which the app never does (see DECISIONS.md).
            excludes += "**/libandroidx.graphics.path.so"
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
        resValues = true
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    implementation(project(":sim"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
}

// Copies the signed release APK to build/dist/<name>-<version>.apk for GitHub Releases.
tasks.register<Copy>("releaseApk") {
    dependsOn("assembleRelease")
    from(layout.buildDirectory.dir("outputs/apk/release")) {
        include("*.apk")
    }
    into(layout.buildDirectory.dir("dist"))
    val apkName = "${appName.lowercase()}-$appVersion.apk"
    rename { apkName }
}

/** 0.1.0 -> 1001: every new MAJOR.MINOR.PATCH gets a bigger code, so updates install over older versions. */
fun versionCodeOf(version: String): Int {
    val parts = version.substringBefore('-').split('.').map { it.toIntOrNull() }
    require(parts.size == 3 && parts.all { it != null && it in 0..999 }) {
        "petri.version must look like 1.2.3 (each number 0..999), got '$version'"
    }
    val (major, minor, patch) = parts.map { it!! }
    return 1 + major * 1_000_000 + minor * 1_000 + patch
}

fun requiredEnv(name: String): String =
    providers.environmentVariable(name).orNull?.takeIf { it.isNotEmpty() }
        ?: throw GradleException("Не задана переменная $name: добавьте соответствующий секрет KEYSTORE_*/KEY_* (см. README).")
