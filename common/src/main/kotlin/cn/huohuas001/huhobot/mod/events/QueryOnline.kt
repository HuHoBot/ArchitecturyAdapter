package cn.huohuas001.huhobot.mod.events

import cn.huohuas001.bot.HuHoBot
import cn.huohuas001.bot.events.AbstractQueryOnline
import cn.huohuas001.huhobot.mod.HuHoBotMod

class QueryOnline(val plugin: HuHoBotMod) : AbstractQueryOnline() {

    override fun getPlugin(): HuHoBot = plugin

    override fun getOnlinePlayerNames(): List<String> {
        return plugin.serverInstance.playerList.players.map { it.name.string }
    }
}
