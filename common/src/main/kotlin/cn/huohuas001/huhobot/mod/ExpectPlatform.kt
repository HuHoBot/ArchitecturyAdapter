package cn.huohuas001.huhobot.mod

import java.nio.file.Path

interface PlatformHooks {
    fun getConfigDirectory(): Path

    fun getModVersion(modId: String): String
}

object ExpectPlatform {
    private var platformHooks: PlatformHooks? = null

    @JvmStatic
    fun register(hooks: PlatformHooks) {
        platformHooks = hooks
    }

    @JvmStatic
    fun getConfigDirectory(): Path {
        return hooks().getConfigDirectory()
    }

    @JvmStatic
    fun getModVersion(modId: String): String {
        return hooks().getModVersion(modId)
    }

    private fun hooks(): PlatformHooks {
        return platformHooks ?: error("Platform hooks are not registered")
    }
}
