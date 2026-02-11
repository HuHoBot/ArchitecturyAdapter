package cn.huohuas001.huhobot.mod.events

import cn.huohuas001.bot.events.AbstractQueryAllowList
import cn.huohuas001.huhobot.mod.HuHoBotMod

class QueryAllowList(val plugin: HuHoBotMod) : AbstractQueryAllowList() {

    override fun getWhiteList(): Set<String> {
        return plugin.serverInstance.playerList.whiteList.userList.toSet()
    }
}
