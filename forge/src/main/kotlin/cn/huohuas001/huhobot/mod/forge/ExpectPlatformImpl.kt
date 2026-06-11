package cn.huohuas001.huhobot.mod.forge

import net.minecraftforge.fml.ModList
import cn.huohuas001.huhobot.mod.ExpectPlatform
import cn.huohuas001.huhobot.mod.PlatformHooks
import net.minecraftforge.fml.loading.FMLPaths
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
