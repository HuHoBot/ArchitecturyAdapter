import net.fabricmc.loom.api.LoomGradleExtensionAPI

plugins {
    java
    kotlin("jvm") version "2.2.0"
    id("architectury-plugin") version "3.4-SNAPSHOT"
    id("dev.architectury.loom") version "1.10-SNAPSHOT" apply false
    id("com.gradleup.shadow") version "9.2.2"
}

// 设置项目版本为gradle.properties中的mod_version
version = property("mod_version").toString()

// 解析版本号，用于比较
fun parseMinecraftVersion(version: String): IntArray {
    // 确保版本号是字符串，避免被解析为数字
    val versionStr = version.trim()
    return versionStr.split(".").map { it.toInt() }.toIntArray()
}

// 比较Minecraft版本是否小于1.20.4
fun isBefore1204(version: String): Boolean {
    val current = parseMinecraftVersion(version)
    val target = intArrayOf(1, 20, 4)
    
    for (i in 0 until minOf(current.size, target.size)) {
        if (current[i] < target[i]) return true
        if (current[i] > target[i]) return false
    }
    return current.size < target.size
}

// 读取版本矩阵文件
fun readVersionMatrix(): Map<String, Map<String, String>> {
    val matrixFile = file("versions-matrix.yaml")
    if (!matrixFile.exists()) {
        throw GradleException("Version matrix file 'versions-matrix.yaml' not found")
    }
    
    // 使用Java Properties类解析简化的YAML格式
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

// 获取命令行传入的minecraft_version，优先读取系统属性，然后是命令行项目属性，最后是gradle.properties文件中的值
val systemMinecraftVersion = System.getProperty("minecraft_version")
val commandLineProps = project.gradle.startParameter.projectProperties
val minecraftVersion = systemMinecraftVersion ?: commandLineProps["minecraft_version"] ?: project.properties["minecraft_version"]?.toString() ?: "1.20.4"
val fullMinecraftVersion = minecraftVersion.trim()

// 读取版本矩阵
val versionMatrix = readVersionMatrix()

// 检查minecraft_version是否在矩阵中
if (!versionMatrix.containsKey(fullMinecraftVersion)) {
    throw GradleException("Minecraft version '$fullMinecraftVersion' not found in versions-matrix.yaml")
}

// 获取对应版本的配置
val versionConfig = versionMatrix[fullMinecraftVersion]!!

// 根据minecraft_version设置enabled_platforms
val enabledPlatforms = if (isBefore1204(fullMinecraftVersion)) {
    "fabric,forge"
} else {
    "fabric,neoforge"
}

// 从矩阵中获取版本参数
val forgeVersion = versionConfig["forge_version"] ?: ""
val neoforgeVersion = versionConfig["neoforge_version"] ?: ""
val architecturyVersion = versionConfig["architectury_version"] ?: ""
val kotlinForForgeVersion = versionConfig["kotlin_for_forge_version"] ?: ""
val fabricLoaderVersion = versionConfig["fabric_loader_version"] ?: ""
val fabricApiVersion = versionConfig["fabric_api_version"] ?: ""
val fabricKotlinVersion = versionConfig["fabric_kotlin_version"] ?: ""
val jvmVersion = versionConfig["jvm_version"] ?: "17" // 默认使用17

// 打印调试信息
println("Using minecraft_version: '$fullMinecraftVersion'")
println("Enabled platforms: $enabledPlatforms")
println("Forge version: $forgeVersion")
println("NeoForge version: $neoforgeVersion")
println("Architectury version: $architecturyVersion")
println("Kotlin for Forge version: $kotlinForForgeVersion")
println("Fabric Loader version: $fabricLoaderVersion")
println("Fabric API version: $fabricApiVersion")
println("Fabric Kotlin version: $fabricKotlinVersion")
println("JVM version: $jvmVersion")

// 修改gradle.properties中的配置
val gradlePropertiesFile = file("gradle.properties")
if (gradlePropertiesFile.exists()) {
    var content = gradlePropertiesFile.readText()
    
    // 更新版本配置
    val updates = mapOf(
        "minecraft_version" to fullMinecraftVersion,
        "enabled_platforms" to enabledPlatforms,
        "forge_version" to forgeVersion,
        "neoforge_version" to neoforgeVersion,
        "architectury_version" to architecturyVersion,
        "kotlin_for_forge_version" to kotlinForForgeVersion,
        "fabric_loader_version" to fabricLoaderVersion,
        "fabric_api_version" to fabricApiVersion,
        "fabric_kotlin_version" to fabricKotlinVersion
    )
    
    var newContent = content
    updates.forEach { (key, value) ->
        if (value.isNotEmpty()) {
            newContent = newContent.replace(Regex("$key=.*"), "$key=$value")
        }
    }
    
    if (content != newContent) {
        gradlePropertiesFile.writeText(newContent)
        println("Updated gradle.properties with versions from matrix")
        // 由于settings.gradle.kts已经在早期阶段更新了gradle.properties，子项目会使用正确的版本信息
    }
}

// 设置architectury插件的minecraft版本
architectury {
    minecraft = fullMinecraftVersion
}

// 只对主项目的子项目应用配置，不包括botSdk子项目
val mainSubprojects = mutableListOf(":common", ":fabric")

// 配置公共项目
project(":common") {
    apply(plugin = "java")
    apply(plugin = "kotlin")
    apply(plugin = "architectury-plugin")
    apply(plugin = "maven-publish")
    apply(plugin = "dev.architectury.loom")

    base.archivesName.set("${property("archives_base_name").toString()}-$fullMinecraftVersion")
    group = property("maven_group").toString()

    repositories {
        mavenCentral()
    }

    // 设置Java工具链
    configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(17)
        }
    }

    // 设置loom配置
    val loom = extensions.getByName<LoomGradleExtensionAPI>("loom")
    dependencies {
        "minecraft"("com.mojang:minecraft:$fullMinecraftVersion")
        "mappings"(loom.officialMojangMappings())
    }

    dependencies {
        compileOnly("org.jetbrains.kotlin:kotlin-stdlib")
        implementation(project(":botSdk:common-Bot"))
        implementation("org.yaml:snakeyaml:2.5")
        implementation(group = "com.alibaba.fastjson2", name = "fastjson2", version = "2.0.52")
        implementation("io.ktor:ktor-client-websockets:1.6.8")
        implementation("io.ktor:ktor-client-cio:1.6.8")
        implementation("io.ktor:ktor-client-core:1.6.8") {
            exclude(group = "org.slf4j")
            exclude(group = "org.yaml")
        }
        implementation("com.alibaba.fastjson2:fastjson2:2.0.52") {
            exclude(group = "org.jetbrains")
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(jvmVersion.toInt())
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(jvmVersion))
        }
    }

    java {
        withSourcesJar()
    }
}

// 配置Fabric项目
project(":fabric") {
    apply(plugin = "java")
    apply(plugin = "kotlin")
    apply(plugin = "architectury-plugin")
    apply(plugin = "maven-publish")
    apply(plugin = "dev.architectury.loom")

    base.archivesName.set("${property("archives_base_name").toString()}-$fullMinecraftVersion")
    group = property("maven_group").toString()

    repositories {
        mavenCentral()
    }

    // 设置Java工具链
    configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(jvmVersion.toInt())
        }
    }

    // 设置loom配置
    val loom = extensions.getByName<LoomGradleExtensionAPI>("loom")
    dependencies {
        "minecraft"("com.mojang:minecraft:$fullMinecraftVersion")
        "mappings"(loom.officialMojangMappings())
    }

    dependencies {
        compileOnly("org.jetbrains.kotlin:kotlin-stdlib")
        implementation(project(":botSdk:common-Bot"))
        implementation("org.yaml:snakeyaml:2.5")
        implementation(group = "com.alibaba.fastjson2", name = "fastjson2", version = "2.0.52")
        implementation("io.ktor:ktor-client-websockets:1.6.8")
        implementation("io.ktor:ktor-client-cio:1.6.8")
        implementation("io.ktor:ktor-client-core:1.6.8") {
            exclude(group = "org.slf4j")
            exclude(group = "org.yaml")
        }
        implementation("com.alibaba.fastjson2:fastjson2:2.0.52") {
            exclude(group = "org.jetbrains")
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(jvmVersion.toInt())
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(jvmVersion))
        }
    }

    java {
        withSourcesJar()
    }
}

// 根据enabled_platforms条件配置Forge或NeoForge项目，但只在项目存在时才配置
// 检查Forge项目是否存在
if (enabledPlatforms.contains("forge") && project.findProject(":forge") != null) {
    project(":forge") { 
        apply(plugin = "java")
        apply(plugin = "kotlin")
        apply(plugin = "architectury-plugin")
        apply(plugin = "maven-publish")
        apply(plugin = "dev.architectury.loom")

        base.archivesName.set(property("archives_base_name").toString())
        group = property("maven_group").toString()

        repositories {
            mavenCentral()
        }

        // 设置Java工具链
        configure<JavaPluginExtension> {
            toolchain {
                languageVersion = JavaLanguageVersion.of(jvmVersion.toInt())
            }
        }

        // 设置loom配置
        val loom = extensions.getByName<LoomGradleExtensionAPI>("loom")
        dependencies {
            "minecraft"("com.mojang:minecraft:$fullMinecraftVersion")
            "mappings"(loom.officialMojangMappings())
        }

        dependencies {
            compileOnly("org.jetbrains.kotlin:kotlin-stdlib")
            implementation(project(":botSdk:common-Bot"))
            implementation("org.yaml:snakeyaml:2.5")
            implementation(group = "com.alibaba.fastjson2", name = "fastjson2", version = "2.0.52")
            implementation("io.ktor:ktor-client-websockets:1.6.8")
            implementation("io.ktor:ktor-client-cio:1.6.8")
            implementation("io.ktor:ktor-client-core:1.6.8") {
                exclude(group = "org.slf4j")
                exclude(group = "org.yaml")
            }
            implementation("com.alibaba.fastjson2:fastjson2:2.0.52") {
                exclude(group = "org.jetbrains")
            }
        }

        tasks.withType<JavaCompile> {
            options.encoding = "UTF-8"
            options.release.set(jvmVersion.toInt())
        }

        // 设置Kotlin编译任务的JVM目标版本
        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
            compilerOptions {
                jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(jvmVersion))
            }
        }

        java {
            withSourcesJar()
        }
    }
}

// 检查NeoForge项目是否存在
if (enabledPlatforms.contains("neoforge") && project.findProject(":neoforge") != null) {
    project(":neoforge") { 
        apply(plugin = "java")
        apply(plugin = "kotlin")
        apply(plugin = "architectury-plugin")
        apply(plugin = "maven-publish")
        apply(plugin = "dev.architectury.loom")

        base.archivesName.set("${property("archives_base_name").toString()}-$fullMinecraftVersion")
        group = property("maven_group").toString()

        repositories {
            mavenCentral()
        }

        // 设置Java工具链
        configure<JavaPluginExtension> {
            toolchain {
                languageVersion = JavaLanguageVersion.of(jvmVersion.toInt())
            }
        }

        // 设置loom配置
        val loom = extensions.getByName<LoomGradleExtensionAPI>("loom")
        dependencies {
            "minecraft"("com.mojang:minecraft:$fullMinecraftVersion")
            "mappings"(loom.officialMojangMappings())
        }

        dependencies {
            compileOnly("org.jetbrains.kotlin:kotlin-stdlib")
            implementation(project(":botSdk:common-Bot"))
            implementation("org.yaml:snakeyaml:2.5")
            implementation(group = "com.alibaba.fastjson2", name = "fastjson2", version = "2.0.52")
            implementation("io.ktor:ktor-client-websockets:1.6.8")
            implementation("io.ktor:ktor-client-cio:1.6.8")
            implementation("io.ktor:ktor-client-core:1.6.8") {
                exclude(group = "org.slf4j")
                exclude(group = "org.yaml")
            }
            implementation("com.alibaba.fastjson2:fastjson2:2.0.52") {
                exclude(group = "org.jetbrains")
            }
        }

        tasks.withType<JavaCompile> {
            options.encoding = "UTF-8"
            options.release.set(jvmVersion.toInt())
        }

        // 设置Kotlin编译任务的JVM目标版本
        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
            compilerOptions {
                jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(jvmVersion))
            }
        }

        java {
            withSourcesJar()
        }
    }
}

// 添加自定义构建任务
tasks.register("customBuild") {
    group = "build"
    description = "Build with dynamic platform selection based on Minecraft version"
    
    // 依赖默认构建任务
    dependsOn(tasks.named("build"))
}

// 添加收集jar文件的任务
tasks.register<Copy>("gatherJars") {
    group = "build"
    description = "Gather all built jars from platforms and copy to outputs directory"
    
    // 使用outputs目录代替build/gathered-jars，避免被clean命令删除
    val targetDir = file("outputs")
    mkdir(targetDir)
    
    // 收集所有平台的remapJar任务输出
    // 添加fabric平台的jar
    if (project.findProject(":fabric") != null) {
        from(project(":fabric").tasks.named("remapJar"))
    }
    // 添加forge平台的jar（如果存在）
    if (project.findProject(":forge") != null) {
        from(project(":forge").tasks.named("remapJar"))
    }
    // 添加neoforge平台的jar（如果存在）
    if (project.findProject(":neoforge") != null) {
        from(project(":neoforge").tasks.named("remapJar"))
    }
    
    // 拷贝到目标目录
    into(targetDir)
    println("Gathered jars copied to: $targetDir")
}

// 修改customBuild任务，使其依赖于gatherJars任务
tasks.named("customBuild") {
    dependsOn(tasks.named("gatherJars"))
}