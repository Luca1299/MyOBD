plugins {
    base
    id("com.diffplug.spotless") version "8.3.0"
    id("com.android.application") version "8.13.2" apply false
    kotlin("android") version "2.2.20" apply false
    kotlin("jvm") version "2.2.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20" apply false
}

spotless {
    lineEndings = com.diffplug.spotless.LineEnding.UNIX
    kotlin {
        target("app/src/**/*.kt", "obd-core/src/**/*.kt")
        targetExclude("**/build/**", "**/generated/**", "**/.gradle/**", "**/.kotlin/**")
        ktlint("1.8.0")
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts", "obd-core/*.gradle.kts")
        ktlint("1.8.0")
    }
}

tasks.named("check") {
    dependsOn("spotlessCheck", ":obd-core:test", ":app:testDebugUnitTest")
}
