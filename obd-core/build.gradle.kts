plugins { kotlin("jvm") }
kotlin { jvmToolchain(17) }
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("io.cucumber:cucumber-java:7.29.0")
    testImplementation("io.cucumber:cucumber-junit:7.29.0")
}

tasks.test {
    useJUnit()
    outputs.dir(layout.buildDirectory.dir("reports/cucumber"))
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
    }
}
