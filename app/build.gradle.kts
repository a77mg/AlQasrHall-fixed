import java.security.MessageDigest
import java.time.Instant

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.secrets)
    alias(libs.plugins.google.services)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.alqasrhall.booking"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.alqasrhall.booking"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH") ?: "$rootDir/my-upload-key.jks"
            val kFile = file(keystorePath)
            if (kFile.exists() && !System.getenv("STORE_PASSWORD").isNullOrEmpty()) {
                storeFile = kFile
                storePassword = System.getenv("STORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
                keyPassword = System.getenv("KEY_PASSWORD")
            } else {
                // Fallback to debug keystore for easy local/CI test builds when secrets are not set
                val debugKeystore = file("$rootDir/debug.keystore")
                if (debugKeystore.exists()) {
                    storeFile = debugKeystore
                    storePassword = "android"
                    keyAlias = "androiddebugkey"
                    keyPassword = "android"
                }
            }
        }
    }

    buildTypes {
        release {
            isCrunchPngs = false
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
    propertiesFileName = ".env"
    defaultPropertiesFileName = ".env"
}

ktlint {
    android.set(true)
    ignoreFailures.set(false)
}

detekt {
    ignoreFailures = false
    buildUponDefaultConfig = true
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    // implementation(libs.accompanist.permissions)
    implementation(libs.androidx.activity.compose)
    // implementation(libs.androidx.camera.camera2)
    // implementation(libs.androidx.camera.core)
    // implementation(libs.androidx.camera.lifecycle)
    // implementation(libs.androidx.camera.view)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    // implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    // implementation(libs.coil.compose)
    implementation(libs.converter.moshi)
    // implementation(libs.firebase.ai)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.logging.interceptor)
    implementation(libs.moshi.kotlin)
    implementation(libs.okhttp)
    // implementation(libs.play.services.location)
    implementation(libs.retrofit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    "ksp"(libs.androidx.room.compiler)
    "ksp"(libs.moshi.kotlin.codegen)
}

tasks.register("copyApks") {
    dependsOn("assembleDebug")
    val buildDir = project.layout.buildDirectory.get().asFile
    doLast {
        val srcFile = file("$buildDir/outputs/apk/debug/app-debug.apk")
        if (srcFile.exists()) {
            val dest1 = file("$rootDir/APK_DOWNLOAD/app-debug.apk")
            val dest2 = file("$rootDir/.build-outputs/app-debug.apk")
            dest1.parentFile.mkdirs()
            dest2.parentFile.mkdirs()
            srcFile.copyTo(dest1, overwrite = true)
            srcFile.copyTo(dest2, overwrite = true)
            val sizeInBytes = srcFile.length()
            val sizeInMB = sizeInBytes.toDouble() / (1024.0 * 1024.0)
            val sizeFormatted = String.format("%.2f", sizeInMB)
            println("APKs copied to targets successfully! SIZE: " + sizeFormatted + " MB")
        } else {
            println("Source debug APK not found!")
        }
    }
}

tasks.register("printApkSha") {
    val buildDir = project.layout.buildDirectory.get().asFile
    doLast {
        val apkFile = file("$buildDir/outputs/apk/debug/app-debug.apk")
        if (apkFile.exists()) {
            val md = MessageDigest.getInstance("SHA-256")
            val bytes = apkFile.readBytes()
            val digest = md.digest(bytes)
            val sb = StringBuilder()
            for (b in digest) {
                val hex = String.format("%02x", b)
                sb.append(hex)
            }
            val sha256 = sb.toString()
            val lastModified = Instant.ofEpochMilli(apkFile.lastModified()).toString()
            println("APK_NAME: app-debug.apk")
            println("APK_SIZE_BYTES: ${apkFile.length()}")
            println("APK_SIZE_MB: ${String.format("%.2f", apkFile.length().toDouble() / (1024.0 * 1024.0))} MB")
            println("APK_DATE: $lastModified")
            println("APK_SHA256: $sha256")
        } else {
            println("APK File not found!")
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
