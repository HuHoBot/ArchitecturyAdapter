plugins {
    id("com.gradleup.shadow")
}

// 设置项目版本为根项目的版本
version = rootProject.version

// 检查当前Minecraft版本是否大于等于1.20.4，只有在兼容的版本上才配置NeoForge
val minecraftVersion = project.properties["minecraft_version"]?.toString() ?: ""
// 使用简单的字符串比较，避免复杂正则表达式的转义问题
val isAtLeast1204 = try {
    val parts = minecraftVersion.split(".").map { it.toIntOrNull() ?: 0 }
    if (parts.size >= 3) {
        parts[0] > 1 || 
        (parts[0] == 1 && parts[1] > 20) || 
        (parts[0] == 1 && parts[1] == 20 && parts[2] >= 4)
    } else {
        false
    }
} catch (e: Exception) {
    false
}

if (isAtLeast1204) {
    architectury {
        platformSetupLoomIde()
        neoForge()
    }

    loom {
        accessWidenerPath.set(project(":common").loom.accessWidenerPath)
    }

    val common: Configuration by configurations.creating
    val shadowCommon: Configuration by configurations.creating
    val developmentNeoForge: Configuration by configurations.getting

    configurations {
        getByName("compileClasspath").extendsFrom(common)
        getByName("runtimeClasspath").extendsFrom(common)
        getByName("developmentNeoForge").extendsFrom(common)
    }

    repositories {
        // KFF
        mavenCentral()
        maven {
            name = "NeoForge"
            setUrl("https://maven.neoforged.net/releases/")
        }
        maven {
            name = "Kotlin for Forge"
            setUrl("https://thedarkcolour.github.io/KotlinForForge/")
        }
    }

    dependencies {
        neoForge("net.neoforged:neoforge:${project.property("neoforge_version")}")
        // Remove the next line if you don't want to depend on the API
        modApi("dev.architectury:architectury-neoforge:${project.property("architectury_version")}")

        common(project(":common", "namedElements")) { isTransitive = false }
        shadowCommon(project(":common", "transformProductionNeoForge")) { isTransitive = false }
        shadowCommon("org.yaml:snakeyaml:2.5")

        shadowCommon("io.ktor:ktor-client-websockets:1.6.8")
        shadowCommon("io.ktor:ktor-client-cio:1.6.8")
        shadowCommon("io.ktor:ktor-client-core:1.6.8") {
            exclude(group = "org.slf4j")
        }

        shadowCommon("com.alibaba.fastjson2:fastjson2:2.0.52")
        shadowCommon(project(":botSdk:common-Bot")) { isTransitive = false }

        // Kotlin For Forge
        implementation("thedarkcolour:kotlinforforge:${project.property("kotlin_for_forge_version")}")
    }

    tasks.processResources {
        inputs.property("group", project.property("maven_group"))
        inputs.property("version", project.version)
        inputs.property("neoforge_version", project.property("neoforge_version"))

        filesMatching("META-INF/neoforge.mods.toml") {
            expand(mutableMapOf(
                Pair("group", project.property("maven_group")),
                Pair("version", project.version),

                Pair("mod_id", project.property("mod_id")),
                Pair("minecraft_version", project.property("minecraft_version")),
                Pair("neoforge_version", project.property("neoforge_version")),
                Pair("architectury_version", project.property("architectury_version")),
                Pair("kotlin_for_forge_version", project.property("kotlin_for_forge_version"))
            ))
        }
    }

    tasks.shadowJar {
        exclude("fabric.mod.json")
        exclude("architectury.common.json")
        exclude("kotlin/**")
        exclude("org/jetbrains/**")
        exclude("META-INF/*.kotlin_module")
        exclude("org/slf4j/**")
        exclude("META-INF/services/org.slf4j.*")
        configurations = listOf(shadowCommon)
        
        relocate("kotlinx.coroutines", "huhobot.shadow.kotlinx.coroutines")
        relocate("kotlinx.serialization", "huhobot.shadow.kotlinx.serialization")
        
        archiveFileName.set("${base.archivesName.get()}-${project.version}-NeoForge_devShadow.jar")
    }

    tasks.remapJar {
        injectAccessWidener.set(true)
        inputFile.set(tasks.shadowJar.flatMap { it.archiveFile })
        dependsOn(tasks.shadowJar)
        // 更新输出文件名格式为 HuHoBot-{version}-{Platform}-{MinecraftVersion}.jar
        archiveFileName.set("HuHoBot-${version}-NeoForge-${project.property("minecraft_version")}.jar")
    }

    tasks.jar {
        archiveClassifier.set("NeoForgeDev")
    }

    tasks.sourcesJar {
        val commonSources = project(":common").tasks.getByName<Jar>("sourcesJar")
        dependsOn(commonSources)
        from(commonSources.archiveFile.map { zipTree(it) })
    }
}
