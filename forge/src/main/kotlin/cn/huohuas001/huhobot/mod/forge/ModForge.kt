package cn.huohuas001.huhobot.mod.forge

import cn.huohuas001.huhobot.mod.HuHoBotMod
import dev.architectury.platform.forge.EventBuses
import net.minecraftforge.fml.common.Mod
import thedarkcolour.kotlinforforge.forge.MOD_BUS
import thedarkcolour.kotlinforforge.forge.MOD_CONTEXT

@Mod(HuHoBotMod.MOD_ID)
class ModForge {
    init {

        // Submit our event bus to let architectury register our content on the right time
        EventBuses.registerModEventBus(HuHoBotMod.MOD_ID, MOD_BUS)
        HuHoBotMod.init()
    }
}