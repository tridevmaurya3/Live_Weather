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

val prepareRadarLeafletRuntime by tasks.registering(Copy::class) {
    val webJarPrefix = "META-INF/resources/webjars/leaflet/1.9.4/dist/"

    inputs.files(radarLeafletRuntime)
    outputs.dir(generatedRadarLeafletAssets)

    from({ radarLeafletRuntime.files.map { zipTree(it) } }) {
        include("${webJarPrefix}**")
        eachFile {
            path = "radar/vendor/leaflet/" + path.removePrefix(webJarPrefix)
        }
        includeEmptyDirs = false
    }

    into(generatedRadarLeafletAssets)

    doLast {
        val leafletJs = generatedRadarLeafletAssets.resolve("radar/vendor/leaflet/leaflet.js")
        val leafletCss = generatedRadarLeafletAssets.resolve("radar/vendor/leaflet/leaflet.css")
        if (!leafletJs.isFile || !leafletCss.isFile) {
            throw GradleException(
                "Leaflet 1.9.4 WebJar layout changed; Radar local runtime assets were not generated."
            )
        }
    }
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

    // Radar Pro 20B.8: resolved only at build time. The task above extracts
    // Leaflet's distributable JS/CSS/images into generated APK assets, so the
    // Radar map engine itself has no runtime CDN dependency.
    add(radarLeafletRuntime.name, "org.webjars.npm:leaflet:1.9.4")

    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
