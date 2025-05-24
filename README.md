# krescent

Krescent is a Kotlin library for working with event-sourced systems in an extendable and easy to use and
slightly opinionated manner.

The following event sources and projector targets are supported:

- KurrentDB (Event Source)
- MongoDB (Projection, CheckpointStore)

## Installation

Add jitpack as a repository in your `build.gradle.kts` file:

```kotlin
repositories {
    maven { url = uri("https://jitpack.io") }
}
```

Then, add the dependency to your `build.gradle.kts` file:

```kotlin
dependencies {
    implementation("com.github.helightdev.krescent:krescent-core:main-SNAPSHOT")
}
```