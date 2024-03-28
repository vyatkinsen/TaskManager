package org.example

import Priority
import States
import org.apache.commons.lang3.time.StopWatch
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.Callable
import kotlin.math.max

open class Task protected constructor(val priority: Priority, var needRunTime: Long) : Callable<Task>, Comparable<Task?> {
    private val logger = LoggerFactory.getLogger(javaClass.name)
    var startWaitingTime = System.currentTimeMillis()
    var lastRunStartTime: Long = 0
        protected set
    var currentState: States = States.SUSPENDED
        set(currentState) {
            if (!field.nextStates().contains(currentState)) throw IllegalArgumentException("Illegal transition")
            field = currentState
        }

    val uuid: String = UUID.randomUUID().toString()

    val watcher = StopWatch()

    override fun call(): Task {
        if (currentState != States.READY) throw IllegalArgumentException("Can run only READY tasks")

        currentState = States.RUNNING
        logger.atInfo().log("$this START EXECUTE")

        try {
            startOrResumeWatcher()
            Thread.sleep(runtime - watcher.time)
        } catch (e: InterruptedException) {
            watcher.suspend()
            logger.atInfo().log("$this INTERRUPTED. STILL NEED ${needRunTime - watcher.getTime()} RUNTIME")
            return this
        }
        watcher.stop()

        currentState = States.SUSPENDED

        logger.atInfo().log("$this END EXECUTE")
        return this
    }

    override fun compareTo(other: Task?): Int {
        val comparePriority = other!!.priority.ordinal - priority.ordinal
        if (comparePriority == 0) return java.lang.Long.signum(this.startWaitingTime - other.startWaitingTime)
        return comparePriority
    }

    fun comparePriority(o: Task): Int {
        return o.priority.ordinal - priority.ordinal
    }

    override fun toString(): String {
        return "Task{" + "priority=" + priority + ", needRunTime=" + needRunTime + ", uuid='" + uuid + '\'' + '}'
    }

    protected fun startOrResumeWatcher() {
        if (watcher.isSuspended) {
            watcher.resume()
        } else {
            watcher.start()
        }
    }

    open val runtime = max(needRunTime.toDouble(), 0.0).toLong()

    open fun waitSomething(): Task {
        logger.atInfo().log("BRO ... I DONT NEED IT")
        return this
    }

    companion object {
        fun of(priority: Priority, needRunTime: Long): Task {
            return Task(priority, needRunTime)
        }
    }
}