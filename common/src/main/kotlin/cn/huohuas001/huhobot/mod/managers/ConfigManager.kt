package cn.huohuas001.huhobot.mod.managers

import cn.huohuas001.bot.provider.BotShared
import cn.huohuas001.bot.provider.ChatFormat
import cn.huohuas001.bot.provider.CustomCommandDetail
import cn.huohuas001.bot.provider.Motd
import cn.huohuas001.bot.provider.WhiteList
import cn.huohuas001.bot.tools.getPackID
import cn.huohuas001.huhobot.mod.ExpectPlatform
import cn.huohuas001.huhobot.mod.HuHoBotMod
import org.apache.logging.log4j.Logger
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.DumperOptions
import java.io.*
import java.nio.file.Files
import java.nio.charset.StandardCharsets
import java.util.*

/**
 * 带Getter和Setter的Fabric配置管理器（保留原有配置项操作）
 */
class ConfigManager(private val mod: HuHoBotMod) {
    private val logger: Logger = mod.LOGGER
    private val configDir: File
    private val configFile: File
    private var config: MutableMap<String, Any> = mutableMapOf()
    companion object {
        private const val CURRENT_CONFIG_VERSION = 2
        private const val DEFAULT_CONFIG_RESOURCE = "huhobot/config.yml"
        private val FALLBACK_DEFAULT_CONFIG = """
            serverId: null
            hashKey: null

            chatFormat:
              from_game: "<{name}> {msg}"
              from_group: "群:<{nick}> {msg}"
              post_chat: true
              post_prefix: ""

            motd:
              server_ip: "play.hypixel.net"
              server_port: 25565
              api: "https://motdbe.blackbe.work/status_img/java?host={server_ip}:{server_port}"
              text: "共{online}人在线"
              output_online_list: true
              post_img: true
              markdown: true
              customMarkdown: false

            whiteList:
              add: "whitelist add {name}"
              del: "whitelist remove {name}"

            callbackConvertImg: 0

            customCommand:
              - key: "加白名"
                command: "whitelist add &1"
                permission: 0
              - key: "管理加白名"
                command: "whitelist add &1"
                permission: 1

            filterRegex:
              - '\u001B\[[;\d]*[ -/]*[@-~]'

            version: 2
        """.trimIndent()
    }

    init {
        // 配置目录：服务器根目录/config/huhobot

        configDir = ExpectPlatform.getConfigDirectory().resolve("huhobot").normalize().toFile()

        if (!configDir.exists() && !configDir.mkdirs()) {
            logger.error("创建配置目录失败！路径：${configDir.absolutePath}")
        }

        // 配置文件路径
        configFile = File(configDir, "config.yml")

        // 加载默认配置
        loadDefaultConfig()

        // 读取配置到内存
        reloadConfig()
    }

    /**
     * 加载默认配置（从resources复制）
     */
    private fun loadDefaultConfig() {
        if (configFile.exists()) {
            return
        }

        try {
            val defaultConfigStream = openDefaultConfigStream()
            if (defaultConfigStream != null) {
                Files.copy(defaultConfigStream, configFile.toPath())
                defaultConfigStream.close()
            } else {
                logger.warn("内置默认配置文件不存在，使用代码内置默认配置")
                configFile.writeText(FALLBACK_DEFAULT_CONFIG, StandardCharsets.UTF_8)
            }
            logger.info("生成默认配置文件：${configFile.absolutePath}")
        } catch (e: IOException) {
            logger.error("生成默认配置失败$e")
        }
    }

    private fun openDefaultConfigStream(): InputStream? {
        return ConfigManager::class.java.classLoader.getResourceAsStream(DEFAULT_CONFIG_RESOURCE)
            ?: mod.javaClass.classLoader.getResourceAsStream(DEFAULT_CONFIG_RESOURCE)
            ?: mod.javaClass.getResourceAsStream("/$DEFAULT_CONFIG_RESOURCE")
    }

    /**
     * 重新加载配置
     */
    fun reloadConfig() {
        if (!configFile.exists()) {
            logger.warn("配置文件不存在，重新生成...")
            loadDefaultConfig()
        }

        val options = DumperOptions()
        options.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        val yaml = Yaml(options)

        try {
            val input = FileInputStream(configFile)
            config = yaml.load<MutableMap<String, Any>>(input) ?: mutableMapOf()
            input.close()

            // 检查并转换配置版本
            checkAndConvertConfig()

            // 补齐缺失默认项，修复旧版本或资源复制失败后只剩 serverId/hashKey 的配置
            ensureDefaultConfigValues()

            logger.info("配置加载完成")
        } catch (e: IOException) {
            logger.error("加载配置失败$e")
            config = mutableMapOf()
        }
    }

    // 添加配置版本检查和转换方法
    private fun checkAndConvertConfig() {
        val configVersion = getInt("version", 0)

        // 如果配置版本小于当前版本，则执行转换
        if (configVersion < CURRENT_CONFIG_VERSION) {
            logger.info("检测到旧版配置文件，正在进行升级转换...")

            // 执行版本转换
            convertConfigToLatestVersion(configVersion)

            // 更新配置版本号
            setConfigValueByPath("version", CURRENT_CONFIG_VERSION)

            // 保存更新后的配置
            saveConfig()

            logger.info("配置文件升级完成，当前版本：$CURRENT_CONFIG_VERSION")
        }
    }

    // 配置转换实现方法
    private fun convertConfigToLatestVersion(oldVersion: Int) {
        if (oldVersion < 1) {
            // 从版本0升级到版本1
            if (getConfigValueByPath("callbackConvertImg") == null) {
                setConfigValueByPath("callbackConvertImg", 0)
            }
        }

        if (oldVersion < 2) {
            // 从版本1升级到版本2
            if (getConfigValueByPath("motd.markdown") == null) {
                setConfigValueByPath("motd.markdown", true)
            }
            if (getConfigValueByPath("motd.customMarkdown") == null) {
                setConfigValueByPath("motd.customMarkdown", false)
            }
            if (getConfigValueByPath("filterRegex") == null) {
                setConfigValueByPath("filterRegex", listOf("\u001B\\[[;\\d]*[ -/]*[@-~]"))
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun ensureDefaultConfigValues() {
        val defaults = loadDefaultConfigMap()
        if (defaults.isEmpty()) {
            return
        }

        if (mergeMissingValues(config, defaults)) {
            saveConfig()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadDefaultConfigMap(): Map<String, Any> {
        val options = DumperOptions()
        options.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        val yaml = Yaml(options)

        openDefaultConfigStream()?.use { input ->
            val reader = InputStreamReader(input, StandardCharsets.UTF_8)
            return yaml.load<MutableMap<String, Any>>(reader) ?: mutableMapOf()
        }

        return yaml.load<MutableMap<String, Any>>(FALLBACK_DEFAULT_CONFIG) ?: mutableMapOf()
    }

    @Suppress("UNCHECKED_CAST")
    private fun mergeMissingValues(target: MutableMap<String, Any>, defaults: Map<String, Any>): Boolean {
        var changed = false

        defaults.forEach { (key, defaultValue) ->
            if (!target.containsKey(key)) {
                target[key] = defaultValue
                changed = true
                return@forEach
            }

            val currentValue = target[key]
            if (currentValue is MutableMap<*, *> && defaultValue is Map<*, *>) {
                changed = mergeMissingValues(
                    currentValue as MutableMap<String, Any>,
                    defaultValue as Map<String, Any>
                ) || changed
            }
        }

        return changed
    }

    /**
     * 保存配置
     */
    fun saveConfig() {
        if (config.isEmpty()) {
            logger.warn("无配置数据可保存")
            return
        }

        val options = DumperOptions()
        options.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        options.indent = 2
        val yaml = Yaml(options)

        try {
            val writer = FileWriter(configFile)
            yaml.dump(config, writer)
            writer.close()
            logger.info("配置已保存")
        } catch (e: IOException) {
            logger.error("保存配置失败$e")
        }
    }

    // ------------------------------ 通用配置读写工具方法 ------------------------------
    private fun getConfigValueByPath(path: String): Any? {
        if (config.isEmpty() || path.isEmpty()) {
            return null
        }

        val segments = path.split("\\.".toRegex())
        var current: Map<String, Any> = config

        for (i in segments.indices) {
            val segment = segments[i]
            if (!current.containsKey(segment)) {
                return null
            }

            val value = current[segment]
            if (i < segments.size - 1) {
                if (value !is Map<*, *>) {
                    return null
                }
                @Suppress("UNCHECKED_CAST")
                current = value as Map<String, Any>
            } else {
                return value
            }
        }
        return null
    }

    @Suppress("UNCHECKED_CAST")
    private fun setConfigValueByPath(path: String, value: Any?) {
        if (config.isEmpty()) {
            config = mutableMapOf()
        }

        val segments = path.split("\\.".toRegex())
        var current: MutableMap<String, Any> = config

        for (i in 0 until segments.size - 1) {
            val segment = segments[i]
            if (!current.containsKey(segment) || current[segment] !is Map<*, *>) {
                current[segment] = mutableMapOf<String, Any>()
            }
            current = current[segment] as MutableMap<String, Any>
        }

        current[segments[segments.size - 1]] = value!!
    }

    // ------------------------------ 原有配置项的Getter和Setter ------------------------------
    // 1. hashKey相关
    fun getHashKey(): String {
        return getString("hashKey", "")
    }

    fun setHashKey(hashKey: String) {
        setConfigValueByPath("hashKey", hashKey)
        saveConfig() // 立即保存
    }

    // 2. serverId相关
    fun getServerId(): String {
        var serverId = getString("serverId", "")
        if(serverId == ""){
            serverId = getPackID()
            setServerId(serverId)
        }
        return serverId
    }

    fun setServerId(serverId: String) {
        setConfigValueByPath("serverId", serverId)
        saveConfig() // 立即保存
    }

    fun getMotd(): Motd{
        val serverIP = getString("motd.server_ip", "play.hypixel.net")
        val serverPort = getInt("motd.server_port", 25565)
        val api = getString("motd.api", "https://motdbe.blackbe.work/status_img/java?host={server_ip}:{server_port}")
        val text = getString("motd.text", "共{online}人在线")
        val outputOnlineList = getBoolean("motd.output_online_list", true)
        val postImg = getBoolean("motd.post_img", true)
        val markdown = getBoolean("motd.markdown", true)
        val customMarkdown = getBoolean("motd.customMarkdown", false)
        return Motd(serverIP, serverPort, api, text, outputOnlineList, postImg, markdown, customMarkdown)
    }

    fun getChatFormat(): ChatFormat {
        val fromGame = getString("chatFormat.from_game", "[游戏] {name}: {msg}")
        val fromGroup = getString("chatFormat.from_group", "[群组] {name}: {msg}")
        val postChat = getBoolean("chatFormat.post_chat", true)
        val postPrefix = getString("chatFormat.post_prefix", "#")
        return ChatFormat(fromGame, fromGroup, postChat, postPrefix)
    }

    fun getWhiteList(): WhiteList{
        val addCommand = getString("whiteList.add", "whitelist add {name}")
        val delCommand = getString("whiteList.del", "whitelist remove {name}")
        return WhiteList(addCommand, delCommand)
    }

    // 5. 服务器名字
    fun getServerName(): String {
        return getString("serverName", "Fabric")
    }

    // 6. 自定义命令
    /**
     * 读取 customCommand 配置（List<Map> 结构）
     * @return 自定义命令列表（每个元素是包含 key/command/permission 的 Map）
     */
    private fun getCustomCommands(): List<Map<String, Any>> {
        // 1. 先读取 customCommand 节点（实际是 List 类型）
        val customCommandObj = getConfigValueByPath("customCommand")

        // 2. 类型判断：若不是 List，返回空列表（避免空指针）
        if (customCommandObj !is List<*>) {
            logger.warn("customCommand 配置格式错误，应为列表结构！")
            return mutableListOf()
        }

        // 3. 强制转换为 List，并过滤无效元素（确保每个元素是 Map）
        val rawList = customCommandObj
        val customCommands = mutableListOf<Map<String, Any>>()

        for (item in rawList) {
            if (item is Map<*, *>) {
                // 转换为 Map<String, Object> 并添加到结果中
                @Suppress("UNCHECKED_CAST")
                val commandMap = item as Map<String, Any>
                customCommands.add(commandMap)
            } else {
                logger.warn("customCommand 中存在无效配置项：$item（应为键值对结构）")
            }
        }

        return customCommands
    }

    fun loadCommandsFromConfig(){
        val customCommands = getCustomCommands()
        val commandMap = HashMap<String, CustomCommandDetail>()
        for (command in customCommands) {
            val key = command["key"] as? String ?: continue
            val commandString = command["command"] as? String ?: continue
            val permission = command["permission"] as? Int ?: 0
            commandMap[key] = CustomCommandDetail(key, commandString, permission)
        }
        BotShared.customCommandMap = commandMap
    }

    fun getCallbackConvertImg(): Int{
        return getInt("callbackConvertImg", 0)
    }

    fun getFilterRegex(): List<Regex> {
        val value = getConfigValueByPath("filterRegex")
        if (value !is List<*>) {
            return emptyList()
        }

        return value.mapNotNull { item ->
            val pattern = item as? String ?: return@mapNotNull null
            try {
                Regex(pattern)
            } catch (e: IllegalArgumentException) {
                logger.warn("filterRegex 配置无效：$pattern")
                null
            }
        }
    }

    // ------------------------------ 通用类型Get方法（供扩展） ------------------------------
    fun getString(path: String, defaultValue: String): String {
        val value = getConfigValueByPath(path)
        return if (value is String) value else defaultValue
    }

    fun getInt(path: String, defaultValue: Int): Int {
        val value = getConfigValueByPath(path)
        return if (value is Int) value else defaultValue
    }

    fun getBoolean(path: String, defaultValue: Boolean): Boolean {
        val value = getConfigValueByPath(path)
        return if (value is Boolean) value else defaultValue
    }

    @Suppress("UNCHECKED_CAST")
    fun getMap(path: String): Map<String, Any> {
        val value = getConfigValueByPath(path)
        return if (value is Map<*, *>) value as Map<String, Any> else mutableMapOf()
    }

    // ------------------------------ Getter ------------------------------
    fun getConfigFile(): File {
        return configFile
    }

    fun getRawConfig(): Map<String, Any> {
        return config.toMap()
    }
}
