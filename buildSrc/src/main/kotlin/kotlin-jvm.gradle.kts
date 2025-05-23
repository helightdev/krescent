// The code in this file is a convention plugin - a Gradle mechanism for sharing reusable build logic.
// `buildSrc` is a Gradle-recognized directory and every plugin there will be easily available in the rest of the build.
package buildsrc.convention

import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.plugin.extraProperties

plugins {
    // Apply the Kotlin JVM plugin to add support for Kotlin in JVM projects.
    kotlin("jvm")
    `maven-publish`
}

version = extraProperties["global.version"]!! as String
group = "dev.helight.krescent"

kotlin {
    // Use a specific Java version to make it easier to work in different environments.
    jvmToolchain(11)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            // Use the default Maven artifact coordinates (group, name, version).
            from(components["java"])
            groupId = project.group.toString()
            artifactId = project.name
            version = version.toString()
        }
    }
}


tasks.withType<Test>().configureEach {
    // Configure all test Gradle tasks to use JUnitPlatform.
    useJUnitPlatform()

    // Log information about all test results, not only the failed ones.
    testLogging {
        events(
            TestLogEvent.FAILED,
            TestLogEvent.PASSED,
            TestLogEvent.SKIPPED
        )
    }
}
