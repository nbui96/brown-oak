pluginManagement {
    val kotlinVersion = "1.9.25"
    val springBootVersion = "3.4.5"
    val springDependencyManagementVersion = "1.1.7"

    repositories {
        mavenCentral()
        gradlePluginPortal()
    }

    plugins {
        id("org.jetbrains.kotlin.jvm") version kotlinVersion
        id("org.jetbrains.kotlin.plugin.spring") version kotlinVersion
        id("org.springframework.boot") version springBootVersion
        id("io.spring.dependency-management") version springDependencyManagementVersion
        id("com.google.protobuf") version "0.9.4"
    }
}

rootProject.name = "movie-recommendation-service"
