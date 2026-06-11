package cn.huohuas001.huhobot.mod.managers

import cn.huohuas001.huhobot.mod.commands.CommandOutputAppender
import cn.huohuas001.huhobot.mod.HuHoBotMod
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import org.apache.logging.log4j.Logger
import java.util.function.Consumer


class CommandManager(private val plugin: HuHoBotMod) {
    private var logger: Logger = plugin.LOGGER
    private val appender = CommandOutputAppender.getInstance()

    fun literal(name: String): LiteralArgumentBuilder<CommandSourceStack> {
        return LiteralArgumentBuilder.literal<CommandSourceStack>(name)
    }

    fun <T> argument(name: String?, type: ArgumentType<T?>?): RequiredArgumentBuilder<CommandSourceStack?, T?> {
        return RequiredArgumentBuilder.argument<CommandSourceStack?, T?>(name, type)
    }

    fun registerCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(literal("huhobot")
                .requires { source -> source.entity == null } // 只允许控制台执行
                .then(
                    literal("reload")
                    .executes(this::onReload)
                )
                .then(
                    literal("reconnect")
                    .executes(this::onReconnect)
                )
                .then(
                    literal("disconnect")
                    .executes(this::onDisconnect)
                )
                .then(
                    literal("bind")
                    .then(
                        argument("bindCode", StringArgumentType.string())
                        .executes(this::onBind)
                    )
                )
                .then(
                    literal("help")
                    .executes(this::onHelp)
                )
        )
    }

    private fun onReload(context: CommandContext<CommandSourceStack>): Int {
        plugin.reloadBotConfig()
        logger.info("§b重载机器人配置文件成功.")

        return 1
    }

    private fun onReconnect(context: CommandContext<CommandSourceStack>): Int {

        val success = plugin.reconnect()
        if (success) {
            logger.info("§6重连机器人成功.")
        } else {
            logger.warn("§c重连机器人失败：已在连接状态.")
        }

        return 1
    }

    private fun onDisconnect(context: CommandContext<CommandSourceStack>): Int {

        val success = plugin.disConnectServer()
        if (success) {
            logger.warn("§6已断开机器人连接.")
        }

        return 1
    }

    private fun onBind(context: CommandContext<CommandSourceStack?>): Int {
        val code = StringArgumentType.getString(context, "bindCode")
        val obj = plugin.bindRequestObj
        val success = obj.confirmBind(code)

        if (success) {
            logger.info("§6已向服务器发送确认绑定请求，请等待服务端下发配置文件.")
        } else {
            logger.error("§c绑定码错误，请重新输入.")
        }

        return 1
    }

    private fun onHelp(context: CommandContext<CommandSourceStack>): Int {
        logger.info("§bHuHoBot 操作相关命令")
        logger.info("§6> §7/huhobot reload - 重载配置文件")
        logger.info("§6> §7/huhobot reconnect - 重新连接服务器")
        logger.info("§6> §7/huhobot disconnect - 断开服务器连接")
        logger.info("§6> §7/huhobot bind <bindCode:string> - 确认绑定")
        return 1
    }

    // 获取执行者名字（玩家名或控制台）
    private fun getName(): String {
        return "HuHoBot"
    }

    fun cleanup() {
        CommandOutputAppender.removeInstance()
    }

    // 外部执行命令并提供回调
    fun executeCommand(command: String, callback: Consumer<CommandResult>) {
        val server = plugin.serverInstance

        server.execute {
            appender.startCapture()
            try {
                val source = server.createCommandSourceStack()
                val result = server.commands.dispatcher.execute(command,source)
                plugin.submitLater(40L) {
                    val output = appender.stopCapture()
                        .map(::filterLogOutput)
                        .distinct()
                        .joinToString("\n")
                    val resultText = output.ifBlank { "Command executed" }
                    callback.accept(CommandResult(command, resultText, "System", result > 0))
                }
            } catch (e: Exception) {
                appender.stopCapture()
                callback.accept(CommandResult(command, "Error: ${e.message}", "System", false))
            }
        }
    }

    private fun filterLogOutput(message: String): String {
        return plugin.config.getFilterRegex().fold(message) { filtered, regex ->
            regex.replace(filtered, "")
        }
    }

    // 命令结果类
    class CommandResult(
        val command: String,
        val output: String,
        val sender: String,
        val success: Boolean
    )
}
