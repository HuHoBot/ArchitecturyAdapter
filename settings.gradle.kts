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

// 读取版本矩阵文件的辅助函数
fun readVersionMatrix(): Map<String, Map<String, String>> {
    val matrixFile = file("versions-matrix.yaml")
    if (!matrixFile.exists()) {
        throw GradleException("Version matrix file 'versions-matrix.yaml' not found")
    }
    
    val matrix = mutableMapOf<String, MutableMap<String, String>>()
    var currentMinecraftVersion: String? = null
    
    matrixFile.forEachLine { line ->
        val trimmedLine = line.trim()
        // 跳过注释和空行
        if (trimmedLine.startsWith("#") || trimmedLine.isEmpty()) {
            return@forEachLine
        }
        
        // 检查是否是minecraft版本行（以冒号结尾）
        if (trimmedLine.endsWith(":")) {
            currentMinecraftVersion = trimmedLine.substring(0, trimmedLine.length - 1)
            matrix[currentMinecraftVersion!!] = mutableMapOf()
        } else if (currentMinecraftVersion != null && trimmedLine.contains(":")) {
            // 解析版本属性行
            val parts = trimmedLine.split(":", limit = 2)
            if (parts.size == 2) {
                val key = parts[0].trim()
                val value = parts[1].trim().removeSurrounding("\"")
                matrix[currentMinecraftVersion!!]?.put(key, value)
            }
        }
    }
    
    return matrix
}

// 解析命令行参数，获取minecraft_version，优先读取命令行项目属性，然后是系统属性
// 同时检查gradle.properties文件中的值，确保在所有情况下都能正确获取版本
val gradlePropsFile = file("gradle.properties")
val gradlePropsMinecraftVersion = gradlePropsFile.takeIf { it.exists() }?.readLines()?.find { it.startsWith("minecraft_version=") }?.substringAfter("=")?.trim() ?: ""
val minecraftVersion = providers.gradleProperty("minecraft_version")
    .orElse(providers.systemProperty("minecraft_version"))
    .orElse(gradlePropsMinecraftVersion)
    .orElse("1.20.4")
    .get()

// 读取版本矩阵
val versionMatrix = readVersionMatrix()

// 检查minecraft_version是否在矩阵中
if (!versionMatrix.containsKey(minecraftVersion)) {
    throw GradleException("Minecraft version '$minecraftVersion' not found in versions-matrix.yaml")
}

// 获取对应版本的配置
val versionConfig = versionMatrix[minecraftVersion]!!

// 根据minecraft_version设置enabled_platforms
val isBefore1204 = try {
    val parts = minecraftVersion.split(".").map { it.toIntOrNull() ?: 0 }
    if (parts.size >= 3) {
        parts[0] < 1 || 
        (parts[0] == 1 && parts[1] < 20) || 
        (parts[0] == 1 && parts[1] == 20 && parts[2] < 4)
    } else {
        false
    }
} catch (e: Exception) {
    false
}

val enabledPlatforms = if (isBefore1204) {
    "fabric,forge"
} else {
    "fabric,neoforge"
}

// 更新gradle.properties文件
if (gradlePropsFile.exists()) {
    var content = gradlePropsFile.readText()
    
    // 更新版本配置
    val updates = mapOf(
        "minecraft_version" to minecraftVersion,
        "enabled_platforms" to enabledPlatforms,
        "forge_version" to (versionConfig["forge_version"] ?: ""),
        "neoforge_version" to (versionConfig["neoforge_version"] ?: ""),
        "architectury_version" to (versionConfig["architectury_version"] ?: ""),
        "kotlin_for_forge_version" to (versionConfig["kotlin_for_forge_version"] ?: ""),
        "fabric_loader_version" to (versionConfig["fabric_loader_version"] ?: ""),
        "fabric_api_version" to (versionConfig["fabric_api_version"] ?: ""),
        "fabric_kotlin_version" to (versionConfig["fabric_kotlin_version"] ?: "")
    )
    
    var newContent = content
    updates.forEach { (key, value) ->
        if (value.isNotEmpty()) {
            newContent = newContent.replace(Regex("$key=.*"), "$key=$value")
        }
    }
    
    if (content != newContent) {
        gradlePropsFile.writeText(newContent)
        println("Settings.gradle.kts: Updated gradle.properties with versions from matrix for Minecraft $minecraftVersion")
    }
}

// 打印调试信息，确认版本和包含的项目
println("Settings.gradle.kts: Using minecraft_version: '$minecraftVersion'")
println("Settings.gradle.kts: isBefore1204: $isBefore1204")
println("Settings.gradle.kts: Enabled platforms: $enabledPlatforms")

// 根据Minecraft版本包含对应的平台子项目
if (isBefore1204) {
    println("Settings.gradle.kts: Including forge project")
    include(":forge")
} else {
    println("Settings.gradle.kts: Including neoforge project")
    include(":neoforge")
}

// 包含botSdk目录作为子项目
include(":botSdk")
project(":botSdk").projectDir = file("./botSdk")

// 包含botSdk/common/Bot模块
include(":botSdk:common-Bot")
project(":botSdk:common-Bot").projectDir = file("./botSdk/common/Bot")