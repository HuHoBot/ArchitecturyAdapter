package cn.huohuas001.huhobot.mod.fabric

import cn.huohuas001.huhobot.mod.ExpectPlatform
import cn.huohuas001.huhobot.mod.HuHoBotMod
import net.fabricmc.api.ModInitializer

class ModFabric: ModInitializer {
    override fun onInitialize() {
        ExpectPlatform.register(ExpectPlatformImpl)
        HuHoBotMod.init()
    }
}
