plugins {
    id("com.gradleup.shadow")
}

// 设置项目版本为根项目的版本
version = rootProject.version

repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.quiltmc.org/repository/release/")
    }
}

architectury {
    platformSetupLoomIde()
    fabric()
}

val common: Configuration by configurations.creating
val shadowCommon: Configuration by configurations.creating
val developmentFabric: Configuration by configurations.getting
val usesMojangMappings = project.property("mojang_mappings").toString().toBoolean()
val commonElements = if (usesMojangMappings) "namedElements" else "runtimeElements"
val commonShadowElements = if (usesMojangMappings) "transformProductionFabric" else "runtimeElements"

fun dependencyConfiguration(preferred: String, fallback: String): String {
    return if (configurations.findByName(preferred) != null) preferred else fallback
}

configurations {
    getByName("compileClasspath").extendsFrom(common)
    getByName("runtimeClasspath").extendsFrom(common)
    getByName("developmentFabric").extendsFrom(common)
}

dependencies {
    add(dependencyConfiguration("modImplementation", "implementation"), "net.fabricmc:fabric-loader:${project.property("fabric_loader_version")}")
    add(dependencyConfiguration("modApi", "implementation"), "net.fabricmc.fabric-api:fabric-api:${project.property("fabric_api_version")}")
    // Remove the next line if you don't want to depend on the API
    add(dependencyConfiguration("modApi", "implementation"), "dev.architectury:architectury-fabric:${project.property("architectury_version")}")

    common(project(":common", commonElements)) { 
        isTransitive = false 
    }
    shadowCommon(project(":common", commonShadowElements)){ isTransitive = false }
    shadowCommon("org.yaml:snakeyaml:2.5")

    shadowCommon("io.ktor:ktor-client-websockets:2.3.12")
    shadowCommon("io.ktor:ktor-client-cio:2.3.12")
    shadowCommon("io.ktor:ktor-client-core:2.3.12") {
        exclude(group = "org.slf4j")
    }

    shadowCommon("com.alibaba.fastjson2:fastjson2:2.0.52")
    shadowCommon(project(":botSdk:common-Bot")){ isTransitive = false }

    // Fabric Kotlin
    add(dependencyConfiguration("modImplementation", "implementation"), "net.fabricmc:fabric-language-kotlin:${project.property("fabric_kotlin_version")}")
}

tasks.processResources {
    inputs.property("group", project.property("maven_group"))
    inputs.property("version", project.version)
    inputs.property("minecraft_version", project.property("minecraft_version"))
    inputs.property("fabric_minecraft_dependency", project.property("fabric_minecraft_dependency"))
    inputs.property("architectury_version", project.property("architectury_version"))
    inputs.property("fabric_loader_version", project.property("fabric_loader_version"))
    inputs.property("fabric_kotlin_version", project.property("fabric_kotlin_version"))

    filesMatching("fabric.mod.json") {
        expand(mutableMapOf(
            Pair("group", project.property("maven_group")),
            Pair("version", project.version),

            Pair("mod_id", project.property("mod_id")),
            Pair("minecraft_version", project.property("minecraft_version")),
            Pair("fabric_minecraft_dependency", project.property("fabric_minecraft_dependency")),
            Pair("architectury_version", project.property("architectury_version")),
            Pair("fabric_loader_version", project.property("fabric_loader_version")),
            Pair("fabric_kotlin_version", project.property("fabric_kotlin_version"))
        ))
    }
}

tasks.shadowJar {
    exclude("kotlin/**")
    exclude("org/jetbrains/**")
    exclude("META-INF/*.kotlin_module")
    exclude("org/slf4j/**")
    exclude("META-INF/services/org.slf4j.*")
    configurations = listOf(shadowCommon)
    
    relocate("kotlinx.coroutines", "huhobot.shadow.kotlinx.coroutines")
    relocate("kotlinx.serialization", "huhobot.shadow.kotlinx.serialization")
    relocate("org.yaml.snakeyaml", "huhobot.shadow.org.yaml.snakeyaml")
    
    archiveFileName.set(
        if (usesMojangMappings) {
            "${base.archivesName.get()}-${project.version}-Fabric_devShadow.jar"
        } else {
            "HuHoBot-${version}-Fabric-${project.property("minecraft_version")}.jar"
        }
    )
}

if (usesMojangMappings) {
    tasks.named<net.fabricmc.loom.task.RemapJarTask>("remapJar") {
        inputFile.set(tasks.shadowJar.flatMap { it.archiveFile })
        dependsOn(tasks.shadowJar)
        // 更新输出文件名格式为 HuHoBot-{version}-{Platform}-{MinecraftVersion}.jar
        archiveFileName.set("HuHoBot-${version}-Fabric-${project.property("minecraft_version")}.jar")
    }
}

tasks.jar {
    archiveClassifier.set("FabricDev")
}

tasks.sourcesJar {
    val commonSources = project(":common").tasks.getByName<Jar>("sourcesJar")
    dependsOn(commonSources)
    from(commonSources.archiveFile.map { zipTree(it) })
}

/*components.getByName("java") {
    this as AdhocComponentWithVariants
    // 安全检查配置是否存在
    project.configurations.findByName("shadowRuntimeElements")?.let { config ->
        this.withVariantsFromConfiguration(config) {
            skip()
        }
    }
}*/
