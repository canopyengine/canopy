plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    `java-library`
    `maven-publish`
}

dependencies {
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
}
