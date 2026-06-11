# Installation

## Building from source

You can build the code using:

```console
./gradlew build
```

This builds both product flavors: `foss` (this is what F-Droid ships, free of proprietary Google
dependencies) and `gplay` (adds the Google Drive backup destination). To build or install only the
Drive-capable flavor, use e.g. `./gradlew assembleGplayRelease` or `./gradlew installGplayDebug`.

You can run the tests using:

```console
./gradlew test
./gradlew connectedAndroidTest
```

## Installing a binary

Get it on F-Droid:

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/en/packages/hu.vmiklos.plees_tracker/)
