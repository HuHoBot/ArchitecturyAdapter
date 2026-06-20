package cn.huohuas001.huhobot.mod.events

import cn.huohuas001.bot.ClientManager
import cn.huohuas001.huhobot.mod.HuHoBotMod
import cn.huohuas001.huhobot.mod.HuHoBotMod.log_info
import dev.architectury.event.EventResult
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

class PlayerEvents {
    fun onChatEvent(player:ServerPlayer , component:Component): EventResult{
        val componentString = component.string
        log_info("Received message: $componentString")
        ClientManager.postChat(player.name.string,componentString)
        return EventResult.pass()
    }

    fun onPlayerJoin(player: ServerPlayer) {
        val config = HuHoBotMod.config
        if (config.getPostEventEnable("onJoin")) {
            val format = config.getPostEventFormat("onJoin")
                .replace("{playerName}", player.name.string)
            ClientManager.postCustomChat(format, "进服")
        }
    }

    fun onPlayerLeft(player: ServerPlayer) {
        val config = HuHoBotMod.config
        if (config.getPostEventEnable("onLeft")) {
            val format = config.getPostEventFormat("onLeft")
                .replace("{playerName}", player.name.string)
            ClientManager.postCustomChat(format, "退服")
        }
    }
}
