package cn.huohuas001.huhobot.mod.tools

import cn.huohuas001.bot.tools.Cancelable
import cn.huohuas001.huhobot.mod.HuHoBotMod
import kotlinx.coroutines.*

class HuHoBotScheduler(private val plugin: HuHoBotMod) {

    private val logger = plugin.LOGGER

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, throwable ->
            logger.error("调度器任务执行异常", throwable)
        }
    )

    companion object {
        private const val TICK_MS = 50L
    }

    fun shutdown() {
        scope.cancel()
    }

    // 一次性延迟任务
    fun runTaskLater(task: Runnable, delayTicks: Long): Cancelable {
        val job = scope.launch {
            delay(delayTicks * TICK_MS)
            task.run()
        }
        return JobCancelable(job)
    }

    // 循环任务
    fun runDelayedLoop(task: Runnable, delayTicks: Long, intervalTicks: Int): Cancelable {
        val job = scope.launch {
            delay(delayTicks * TICK_MS)
            while (isActive) {
                task.run()
                delay(intervalTicks * TICK_MS)
            }
        }
        return JobCancelable(job)
    }

    fun runDelayedLoop(task: Runnable, delayTicks: Long): Cancelable {
        return runDelayedLoop(task, delayTicks, 20)
    }

    // 立即在子线程执行
    fun runTask(task: Runnable): Cancelable {
        val job = scope.launch {
            task.run()
        }
        return JobCancelable(job)
    }

    private class JobCancelable(private val job: Job) : Cancelable {
        override fun cancel() {
            job.cancel()
        }
    }
}
