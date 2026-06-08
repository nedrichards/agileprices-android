import java.io.File
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) {
        file.inputStream().use(::load)
    }
}

val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.isFile) {
        file.inputStream().use(::load)
    }
}

fun releaseProperty(name: String, environmentName: String): String? =
    providers.gradleProperty("agilePrices.$name").orNull
        ?: localProperties.getProperty("agilePrices.$name")
        ?: keystoreProperties.getProperty(name)
        ?: providers.environmentVariable(environmentName).orNull

fun debugSigningProperty(name: String): String? =
    providers.gradleProperty("androidDebugSigning.$name").orNull
        ?: localProperties.getProperty("androidDebugSigning.$name")

val debugStoreFile = debugSigningProperty("storeFile")
val debugStorePassword = debugSigningProperty("storePassword") ?: "android"
val debugKeyAlias = debugSigningProperty("keyAlias") ?: "androiddebugkey"
val debugKeyPassword = debugSigningProperty("keyPassword") ?: debugStorePassword
val hasStableDebugSigning = !debugStoreFile.isNullOrBlank()

val releaseStoreFile = releaseProperty("storeFile", "AGILE_PRICES_KEYSTORE_FILE")
val releaseStorePassword = releaseProperty("storePassword", "AGILE_PRICES_KEYSTORE_PASSWORD")
val releaseKeyAlias = releaseProperty("keyAlias", "AGILE_PRICES_KEY_ALIAS")
val releaseKeyPassword = releaseProperty("keyPassword", "AGILE_PRICES_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

fun configuredFile(path: String): File {
    val home = System.getProperty("user.home")
    val expanded = when {
        path == "~" -> home
        path.startsWith("~/") -> "$home/${path.removePrefix("~/")}"
        path.startsWith("\$HOME/") -> "$home/${path.removePrefix("\$HOME/")}"
        path.startsWith("\${user.home}/") -> "$home/${path.removePrefix("\${user.home}/")}"
        else -> path
    }
    return file(expanded)
}

android {
    namespace = "com.nedrichards.agileprices"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.nedrichards.agileprices"
        minSdk = 30
        targetSdk = 36
        versionCode = (
            providers.gradleProperty("agilePrices.versionCode").orNull
                ?: localProperties.getProperty("agilePrices.versionCode")
                ?: "1"
            ).toInt()
        versionName = providers.gradleProperty("agilePrices.versionName").orNull
            ?: localProperties.getProperty("agilePrices.versionName")
            ?: "0.1.0"
    }

    signingConfigs {
        if (hasStableDebugSigning) {
            create("stableDebug") {
                storeFile = configuredFile(debugStoreFile!!)
                storePassword = debugStorePassword
                keyAlias = debugKeyAlias
                keyPassword = debugKeyPassword
            }
        }
        if (hasReleaseSigning) {
            create("release") {
                storeFile = configuredFile(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            if (hasStableDebugSigning) {
                signingConfig = signingConfigs.getByName("stableDebug")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.concurrent.futures)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.work.runtime.ktx)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose and Wear OS
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.wear.compose.foundation)
  implementation(libs.androidx.wear.compose.material3)
  implementation(libs.androidx.wear.tiles)
  implementation(libs.androidx.wear.protolayout)
  implementation(libs.androidx.wear.protolayout.material)
  implementation(libs.androidx.wear.protolayout.material3)
  implementation(libs.androidx.wear.protolayout.expression)
  implementation(libs.androidx.watchface.complications.data.source.ktx)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.okhttp)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

}
