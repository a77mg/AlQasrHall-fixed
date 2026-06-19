// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.roborazzi) apply false
  alias(libs.plugins.secrets) apply false
  alias(libs.plugins.google.services) apply false
  alias(libs.plugins.ktlint) apply false
  alias(libs.plugins.detekt) apply false
}

tasks.register<Zip>("packageProject") {
    archiveFileName.set("qasr-al-daery-booking-system.zip")
    destinationDirectory.set(file("${rootDir}/.build-outputs"))
    
    // Explicit white-list to pack immediately
    from(rootDir) {
        include("app/src/**")
        include("app/build.gradle.kts")
        include("gradle/**")
        include("build.gradle.kts")
        include("settings.gradle.kts")
        include("gradle.properties")
        include("metadata.json")
        include(".env.example")
        include("APK_DOWNLOAD/**")
        include(".build-outputs/app-debug.apk")
    }
}
