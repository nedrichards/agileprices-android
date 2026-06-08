# Stable Android debug signing

Android preserves app data across updates only when the replacement APK is signed
with the same certificate as the installed package. If different agents or tool
environments generate different debug keystores, `adb install -r` and
`connectedDebugAndroidTest` can fail with:

```text
INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package ... signatures do not match newer version
```

Use one stable debug keystore for local development builds. In a normal Android
development environment, use:

```text
$HOME/.android/debug.keystore
```

Verify the keystore:

```sh
keytool -list -v \
  -keystore "$HOME/.android/debug.keystore" \
  -storepass android \
  -alias androiddebugkey
```

Keep the same keystore file across local agents and sessions. The fingerprint
does not need to be checked into the repository; compare APK output against the
local keystore when diagnosing install mismatches.

## Per-project setup

Add these entries to the project's ignored `local.properties`:

```properties
androidDebugSigning.storeFile=$HOME/.android/debug.keystore
androidDebugSigning.storePassword=android
androidDebugSigning.keyAlias=androiddebugkey
androidDebugSigning.keyPassword=android
```

The Gradle snippet below expands `~`, `$HOME` and `${user.home}` for configured
keystore paths.

In the app module `build.gradle.kts`, load `local.properties` if the project
does not already do so:

```kotlin
import java.io.File
import java.util.Properties

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) {
        file.inputStream().use(::load)
    }
}
```

Add an optional debug signing config:

```kotlin
fun debugSigningProperty(name: String): String? =
    providers.gradleProperty("androidDebugSigning.$name").orNull
        ?: localProperties.getProperty("androidDebugSigning.$name")

val debugStoreFile = debugSigningProperty("storeFile")
val debugStorePassword = debugSigningProperty("storePassword") ?: "android"
val debugKeyAlias = debugSigningProperty("keyAlias") ?: "androiddebugkey"
val debugKeyPassword = debugSigningProperty("keyPassword") ?: debugStorePassword
val hasStableDebugSigning = !debugStoreFile.isNullOrBlank()

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
    signingConfigs {
        if (hasStableDebugSigning) {
            create("stableDebug") {
                storeFile = configuredFile(debugStoreFile!!)
                storePassword = debugStorePassword
                keyAlias = debugKeyAlias
                keyPassword = debugKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            if (hasStableDebugSigning) {
                signingConfig = signingConfigs.getByName("stableDebug")
            }
        }
    }
}
```

This is deliberately optional. If `androidDebugSigning.storeFile` is absent, the
project falls back to the Android Gradle Plugin's normal debug signing behavior.

## Verification

Build and verify the debug APK:

```sh
./gradlew assembleDebug
"$ANDROID_HOME/build-tools/<version>/apksigner" verify --print-certs \
  app/build/outputs/apk/debug/app-debug.apk
```

The APK signer SHA-256 should match the local stable debug keystore fingerprint.

If an emulator or watch already has the same package installed with a different
certificate, one uninstall is unavoidable:

```sh
adb -s <device-serial> uninstall <applicationId>
adb -s <device-serial> install app/build/outputs/apk/debug/app-debug.apk
```

After that, normal replacement installs and connected tests should update in
place without clearing app setup.
