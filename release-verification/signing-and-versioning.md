# Signing and versioning

Agile Prices uses a conditional release signing setup so normal debug and
unsigned local builds continue to work without a keystore. A release build is
signed only when all release signing values are present, unless local
debug-signed release testing is explicitly enabled.

## Signing inputs

Create an upload keystore for Play App Signing and keep it outside version
control. Android's app signing guidance recommends an upload key for Android
App Bundles, with Google Play using Play App Signing for the final distributed
APKs.

Copy `keystore.properties.example` to `keystore.properties`, then fill in:

```properties
storeFile=/absolute/path/to/agile-prices-upload.jks
storePassword=...
keyAlias=upload
keyPassword=...
```

`keystore.properties`, `*.jks` and `*.keystore` are ignored by Git.

The same values can also be provided without a properties file:

```sh
AGILE_PRICES_KEYSTORE_FILE=/absolute/path/to/agile-prices-upload.jks \
AGILE_PRICES_KEYSTORE_PASSWORD=... \
AGILE_PRICES_KEY_ALIAS=upload \
AGILE_PRICES_KEY_PASSWORD=... \
./gradlew bundlePhoneRelease bundleWearRelease
```

Gradle project properties are supported too:

```sh
./gradlew bundlePhoneRelease bundleWearRelease \
  -PagilePrices.storeFile=/absolute/path/to/agile-prices-upload.jks \
  -PagilePrices.storePassword=... \
  -PagilePrices.keyAlias=upload \
  -PagilePrices.keyPassword=...
```

## Build commands

Unsigned development build:

```sh
./gradlew assembleDebug
```

Unsigned release APKs for smoke testing:

```sh
./gradlew assemblePhoneRelease assembleWearRelease
```

Debug-signed release APKs for local device performance testing:

```properties
agilePrices.debugSignRelease=true
```

Add that entry to ignored `local.properties`. Release signing values still take
precedence when present. Do not use a debug-signed release artifact for Play
upload.

Signed phone and Wear Android App Bundles:

```sh
./gradlew bundlePhoneRelease bundleWearRelease
```

The signed AABs are written to:

```text
app/build/outputs/bundle/phoneRelease/app-phone-release.aab
app/build/outputs/bundle/wearRelease/app-wear-release.aab
```

## Versioning

The default base version is currently:

```text
agilePrices.versionCode=1
agilePrices.versionName=0.1.0
```

The phone artifact turns that base into version code `10`; the Wear artifact
uses `11`, keeping the two form-factor uploads distinct under one Play listing.

Override it for a release with Gradle properties or ignored local properties:

```sh
./gradlew bundlePhoneRelease bundleWearRelease \
  -PagilePrices.versionCode=2 \
  -PagilePrices.versionName=0.1.1
```

Each Play release must increase the base `agilePrices.versionCode`; the build
then derives the distinct phone and Wear version codes from it.
