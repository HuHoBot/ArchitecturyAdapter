package cn.huohuas001.huhobot.mod.neoforge

import cn.huohuas001.huhobot.mod.ExpectPlatform
import cn.huohuas001.huhobot.mod.PlatformHooks
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.fml.ModList
import java.nio.file.Path

object ExpectPlatformImpl : PlatformHooks {
    /**
     * This is our actual method to [ExpectPlatform.getConfigDirectory].
     */
    override fun getConfigDirectory(): Path {
        return FMLPaths.CONFIGDIR.get()
    }

    /**
     * This is our actual method to [ExpectPlatform.getModVersion].
     */
    override fun getModVersion(modId: String): String {
        val container = ModList.get().getModContainerById(modId)
        return if (container.isPresent) {
            container.get().modInfo.version.toString()
        } else {
            "unknown"
        }
    }
}
