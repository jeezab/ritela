import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

val appVersion = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}.getProperty("versionName")
require(appVersion.matches(Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)")))
val versionParts = appVersion.split('.').map(String::toInt)
require(versionParts[0] in 0..2000 && versionParts.drop(1).all { it in 0..999 })
val appVersionCode = versionParts[0] * 1_000_000 + versionParts[1] * 1000 + versionParts[2]
require(appVersionCode > 0)

val signingVariables = listOf(
    "ANDROID_KEYSTORE_PATH",
    "ANDROID_KEYSTORE_PASSWORD",
    "ANDROID_KEY_ALIAS",
    "ANDROID_KEY_PASSWORD"
)
val signingValues = signingVariables.map { providers.environmentVariable(it).orNull }
require(signingValues.all { it.isNullOrBlank() } || signingValues.all { !it.isNullOrBlank() }) {
    "Release signing requires all four ANDROID_KEYSTORE_* / ANDROID_KEY_* variables"
}

android {
    namespace = "app.ritela"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.ritela"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersion
    }

    if (signingValues.all { !it.isNullOrBlank() }) {
        signingConfigs {
            create("distribution") {
                storeFile = file(signingValues[0]!!)
                storePassword = signingValues[1]
                keyAlias = signingValues[2]
                keyPassword = signingValues[3]
            }
        }
        buildTypes.named("release") {
            signingConfig = signingConfigs.getByName("distribution")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        generateLocaleConfig = true
        localeFilters += listOf("en", "ru")
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty(
                "ritela.skipScreenshots",
                providers.gradleProperty("ritela.skipScreenshots").getOrElse("false")
            )
            // PNGs must be restored together with test reports on FROM-CACHE runs.
            if (it.name == "testDebugUnitTest") {
                it.outputs.dir(layout.buildDirectory.dir("reports/screenshots"))
                    .withPropertyName("uiScreenshots")
            }
            it.systemProperty(
                "ritela.screenshotDir",
                layout.buildDirectory.dir("reports/screenshots").get().asFile.absolutePath
            )
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        // Pinned toolchain updates are advisory; correctness/privacy warnings still fail.
        informational.addAll(listOf("AndroidGradlePluginVersion", "NewerVersionAvailable"))
    }
}

dependencies {
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}

room {
    schemaDirectory("$projectDir/schemas")
}
