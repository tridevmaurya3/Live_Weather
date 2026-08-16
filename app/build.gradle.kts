plugins {
    alias(libs.plugins.android.application)
}

val radarLeafletRuntime by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

/*
 * Keep this as a concrete File, not Provider<Directory>.
 * Newer Android Gradle Plugin versions reject Provider instances passed to the
 * legacy SourceSet API. preBuild explicitly depends on the generation task, so
 * the generated directory is prepared before Android packages main assets.
 */
val generatedRadarLeafletAssets = layout.buildDirectory
    .dir("generated/radarLeafletAssets")
    .get()
    .asFile

/*
 * Configuration-cache-safe Copy task:
 * - no doLast/doFirst task action capturing Kotlin script state
 * - no lazy from { ... } closure that captures the Configuration / Project
 * - CopySpec receives archive trees while the task is configured
 * - eachFile uses Kotlin DSL receiver syntax and only string literals
 */
val prepareRadarLeafletRuntime by tasks.registering(Copy::class) {
    from(radarLeafletRuntime.map { archive -> zipTree(archive) }) {
        include("META-INF/resources/webjars/leaflet/1.9.4/dist/**")
        eachFile {
            path = "radar/vendor/leaflet/" + path.removePrefix(
                "META-INF/resources/webjars/leaflet/1.9.4/dist/"
            )
        }
        includeEmptyDirs = false
    }

    into(generatedRadarLeafletAssets)
}

android {
    namespace = "com.tridev.liveweather"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.tridev.liveweather"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    sourceSets {
        getByName("main").assets.srcDir(generatedRadarLeafletAssets)
    }
}

tasks.named("preBuild").configure {
    dependsOn(prepareRadarLeafletRuntime)
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)

    // Lifecycle foundation for Java + XML MVVM screens.
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)

    // Reliable, battery-aware background weather refresh work.
    implementation(libs.work.runtime)

    // Network + JSON foundation for the weather data layer.
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.gson)

    // Foreground device location used to resolve weather coordinates.
    implementation(libs.play.services.location)

    // Accurate local astronomical calculations for Sun/Moon/sky reality state.
    implementation(libs.astronomy)

    // Radar Pro: resolved only at build time. The Copy task above extracts
    // Leaflet's distributable JS/CSS/images into generated APK assets, so the
    // Radar map engine itself has no runtime CDN dependency.
    add(radarLeafletRuntime.name, "org.webjars.npm:leaflet:1.9.4")

    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
