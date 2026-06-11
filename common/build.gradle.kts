// 获取当前的enabled_platforms，确保使用正确的平台配置
val enabledPlatforms = project.properties["enabled_platforms"].toString().split(",")
val usesMojangMappings = project.property("mojang_mappings").toString().toBoolean()

if (usesMojangMappings) {
    architectury {
        common(enabledPlatforms)
    }
}

fun dependencyConfiguration(preferred: String, fallback: String): String {
    return if (configurations.findByName(preferred) != null) preferred else fallback
}

dependencies {
    // We depend on fabric loader here to use the fabric @Environment annotations and get the mixin dependencies
    // Do NOT use other classes from fabric loader
    add(dependencyConfiguration("modImplementation", "compileOnly"), "net.fabricmc:fabric-loader:${project.property("fabric_loader_version")}")
    // Remove the next line if you don't want to depend on the API
    add(dependencyConfiguration("modApi", "implementation"), "dev.architectury:architectury:${project.property("architectury_version")}")
    if (!usesMojangMappings) {
        compileOnly("dev.architectury:architectury-injectables:1.0.10")
    }
}
