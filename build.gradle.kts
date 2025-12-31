import net.fabricmc.loom.api.LoomGradleExtensionAPI

plugins {
    java
    kotlin("jvm") version "2.2.0"
    id("architectury-plugin") version "3.4-SNAPSHOT"
    id("dev.architectury.loom") version "1.10-SNAPSHOT" apply false
    id("com.gradleup.shadow") version "9.2.2"
}

architectury {
    minecraft = project.property("minecraft_version").toString()
}

subprojects {
    apply(plugin = "dev.architectury.loom")

    val loom = project.extensions.getByName<LoomGradleExtensionAPI>("loom")


    dependencies {
        "minecraft"("com.mojang:minecraft:${project.property("minecraft_version")}")
        // The following line declares the mojmap mappings, you may use other mappings as well
        "mappings"(
            loom.officialMojangMappings()
        )
        // The following line declares the yarn mappings you may select this one as well.
        // "mappings"("net.fabricmc:yarn:1.18.2+build.3:v2")
    }
}

// 只对主项目的子项目应用配置，不包括botSdk子项目
val mainSubprojects = listOf(":common", ":fabric", ":forge", ":neoforge")
mainSubprojects.forEach { projectPath ->
    project(projectPath) {
        apply(plugin = "java")
        apply(plugin = "kotlin")
        apply(plugin = "architectury-plugin")
        apply(plugin = "maven-publish")

        base.archivesName.set(property("archives_base_name").toString())
        group = property("maven_group").toString()

        repositories {
            mavenCentral()
        }

        dependencies {
            compileOnly("org.jetbrains.kotlin:kotlin-stdlib")
            implementation(project(":botSdk:common-Bot"))
            // https://mvnrepository.com/artifact/org.yaml/snakeyaml
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
            options.release.set(17)
        }

        java {
            withSourcesJar()
            toolchain {
                languageVersion = JavaLanguageVersion.of(17)
            }
        }
    }
}