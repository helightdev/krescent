---
title: Installation
outline: deep
---

This page guides you on how to add Krescent to your Kotlin project.

## Adding Krescent to Your Project

Krescent artifacts are published to Maven Central. You can add Krescent to your project using Gradle.

### Gradle Kotlin DSL (`build.gradle.kts`)

Add the following to your `build.gradle.kts` file:

```kotlin
// build.gradle.kts
dependencies {
    // Core Krescent library (required)
    implementation("dev.helight.krescent:krescent-core:LATEST_VERSION")

    // For specific event store implementations, add them as needed:
    // implementation("dev.helight.krescent:krescent-mongo:LATEST_VERSION")
    // implementation("dev.helight.krescent:krescent-kurrent:LATEST_VERSION")

    // If you need other utilities or extensions, include them here.
}
```

**Important:**

-   Replace `LATEST_VERSION` with the actual latest release version number of Krescent.
-   You can find the latest version number on [Maven Central](https://search.maven.org/artifact/dev.helight.krescent/krescent-core) or by checking the project's releases page on its repository.

## Modules

Krescent is designed to be modular, allowing you to include only the parts you need. Here are some of the primary modules available:

-   **`krescent-core`**: This is the heart of Krescent, containing all the essential classes and interfaces for event definition, event sourcing, event models, and the event processing pipeline. This module is **required** for any Krescent application.

-   **`krescent-mongo`**: Provides an implementation of `StreamingEventSource` and `EventPublisher` that uses MongoDB as a backing event store. Include this if you plan to store your events in a MongoDB database.

-   **`krescent-kurrent`**: Offers an implementation of `StreamingEventSource` and `EventPublisher` based on Kurrent, a specialized event store. Choose this if Kurrent is your preferred event storage solution.

When setting up your project, include `krescent-core` and then add any additional modules for specific event stores or other functionalities you intend to use. This helps keep your application lean and reduces unnecessary dependencies.
