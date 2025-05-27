---
title: Installation
outline: deep
---

This page guides you on how to add Krescent to your Kotlin project.

## Adding Krescent to Your Project

Krescent artifacts are automatically built using jitpack.

### Gradle Kotlin DSL (`build.gradle.kts`)

Add the following to your `build.gradle.kts` file:

```kotlin
// build.gradle.kts
repositories {
    maven { url = uri("https://jitpack.io") } // [!code ++]
}

dependencies {
    implementation("com.github.helightdev.krescent:krescent-core:main-SNAPSHOT") // [!code ++]
}
```

You may also add additional supporting dependencies depending on your use case. The following modules are available:

- `krescent-kurrent`: Support for KurrentDB (formerly EventStoreDB) as an event source.
- `krescent-mongo`: Support for MongoDB as a projection and checkpoint store.