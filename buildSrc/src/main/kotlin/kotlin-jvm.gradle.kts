// The code in this file is a convention plugin - a Gradle mechanism for sharing reusable build logic.
// `buildSrc` is a Gradle-recognized directory and every plugin there will be easily available in the rest of the build.
package buildsrc.convention

import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.plugin.extraProperties

plugins {
    // Apply the Kotlin JVM plugin to add support for Kotlin in JVM projects.
    kotlin("jvm")
    id("org.jetbrains.dokka")
    `maven-publish`
}

version = extraProperties["global.version"]!! as String
group = "dev.helight.krescent"

kotlin {
    // Use a specific Java version to make it easier to work in different environments.
    jvmToolchain(11)
}

java {
    withSourcesJar()
}

dokka { }

tasks.register<Jar>("dokkaHtmlJar") {
    dependsOn(tasks.dokkaGenerateHtml)
    from(layout.buildDirectory.dir("dokka/html"))
    archiveClassifier.set("javadoc")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            // Use the default Maven artifact coordinates (group, name, version).
            from(components["java"])
            artifact(tasks["dokkaHtmlJar"])

            groupId = project.group.toString()
            artifactId = project.name

            version = findProperty("build.version")?.toString()
                ?: System.getenv("BUILD_VERSION")
                        ?: extraProperties["global.version"]!! as String
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/helightdev/krescent")
            credentials {
                username = project.findProperty("gpr.user")?.toString() ?: System.getenv("USERNAME")
                password = project.findProperty("gpr.key")?.toString() ?: System.getenv("TOKEN")
            }
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