// The settings file is the entry point of every Gradle build.
// Its primary purpose is to define the subprojects.
// It is also used for some aspects of project-wide configuration, like managing plugins, dependencies, etc.

rootProject.name = "HuHoBotArchitectury"

// 配置依赖解析
dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
        maven { url = uri("https://maven.fabricmc.net/") }
        maven { url = uri("https://maven.architectury.dev/") }
        maven { url = uri("https://maven.minecraftforge.net/") }
    }
}

// 配置插件管理
pluginManagement {
    repositories {
        maven { url = uri("https://maven.fabricmc.net/") }
        maven { url = uri("https://maven.architectury.dev/") }
        maven { url = uri("https://maven.minecraftforge.net/") }
        gradlePluginPortal()
    }
}

// 使用Foojay Toolchains插件自动下载JDK
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

// 包含主项目的子模块
include(":common")
include(":fabric")
include(":forge")
include(":neoforge")

// 包含botSdk目录作为子项目
include(":botSdk")
project(":botSdk").projectDir = file("./botSdk")

// 包含botSdk/common/Bot模块
include(":botSdk:common-Bot")
project(":botSdk:common-Bot").projectDir = file("./botSdk/common/Bot")