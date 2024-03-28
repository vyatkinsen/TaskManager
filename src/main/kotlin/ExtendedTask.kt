import org.example.Task
import org.slf4j.LoggerFactory
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.max
import States.RUNNING
import States.WAIT
import States.SUSPENDED
import States.READY

class ExtendedTask protected constructor(
    priority: Priority,
    needRunTime: Long,
    var needRuntimeBeforeWait: Long,
    private val waitTime: Long
) : Task(priority, needRunTime) {
    private var needToWait = true
    private val logger = LoggerFactory.getLogger(javaClass.name)

    override fun waitSomething(): Task {
        if (currentState != WAIT)
            throw IllegalArgumentException("Wait can only when WAIT task state")

        logger.atInfo().log("$this BEGIN TO WAIT ... ")
        try {
            Thread.sleep(waitTime)
        } catch (e: InterruptedException) {
            throw IllegalCallerException("Cant interrupt $this while waiting! ")
        }
        logger.atInfo().log("$this READY TO RUNNING AFTER WAITING")
        needToWait = false
        return this
    }

    override fun call(): Task {
        if (currentState != READY)
            throw IllegalArgumentException("Can run only READY task state")

        currentState = RUNNING
        logger.atInfo().log("$this START EXECUTE")
        try {
            startOrResumeWatcher()
            Thread.sleep(runtime - watcher.time)
        } catch (e: InterruptedException) {
            watcher.suspend()
            val leftTime: Long =
                (if (needToWait) needRunTime + needRuntimeBeforeWait else needRunTime) - watcher.time
            logger.atInfo().log("$this INTERRUPTED. STILL NEED $leftTime RUNTIME")
            return this
        }
        if (needToWait) {
            watcher.reset()
            logger.atInfo().log("$this WANT WAIT")
            currentState = WAIT
        } else {
            watcher.stop()
            logger.atInfo().log("$this END EXECUTE")
            currentState = SUSPENDED
        }
        return this
    }

    override fun toString(): String {
        return "Task{" +
                "priority=" + priority +
                ", needRunTime=" + (needRunTime + needRuntimeBeforeWait) +
                ", uuid='" + uuid + '\'' +
                '}'
    }

    // FIXME
    override val runtime = max(0.0, (if (needToWait) needRuntimeBeforeWait else needRunTime).toDouble()).toLong()

    companion object {
        private const val MAX_RANDOM_LONG_WAIT_TIME: Long = 3000

        fun of(priority: Priority, needRunTime: Long): ExtendedTask {
            val waitAt = ThreadLocalRandom.current().nextLong(needRunTime)
            val waitTime = ThreadLocalRandom.current().nextLong(MAX_RANDOM_LONG_WAIT_TIME)

            return ExtendedTask(priority, needRunTime - waitAt, waitAt, waitTime)
        }

        fun of(priority: Priority, needRunTime: Long, waitAt: Long, waitTime: Long): ExtendedTask {
            return ExtendedTask(priority, needRunTime - waitAt, waitAt, waitTime)
        }
    }
}
