import LogicExceptionType.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import Priority.*
import Task.State.*
import java.awt.Color


class MQ {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val _completedTasks = mutableListOf<Task>()
    val completedTasks: List<Task>
        get() = _completedTasks

    private val lock = Any()

    private val _queueStateFlow = MutableStateFlow(
        QueuePool(
            ArrayDeque(),
            ArrayDeque(),
            ArrayDeque(),
            ArrayDeque()
        )
    )
    val queueStateFlow = _queueStateFlow.asStateFlow()

    init {
        runBlocking { queueStateFlow.value.printWithColors() }
    }

    fun addTask(task: Task, priority: Priority) {
        logger.atInfo().log("Adding new task, uuid:${task.uuid}, priority:$priority")
        synchronized(lock) {
            val stateSnapshot = queueStateFlow.value
            when (priority) {
                LOWEST -> {
                    _queueStateFlow.value = stateSnapshot.copy(
                        lowestQueue = stateSnapshot.lowestQueue.withAddedTask(task)
                    )
                }

                LOW -> {
                    _queueStateFlow.value = stateSnapshot.copy(
                        lowQueue = stateSnapshot.lowQueue.withAddedTask(task)
                    )
                }

                MID -> {
                    _queueStateFlow.value = stateSnapshot.copy(
                        midQueue = stateSnapshot.midQueue.withAddedTask(task)
                    )
                }

                HIGH -> {
                    _queueStateFlow.value = stateSnapshot.copy(
                        highQueue = stateSnapshot.highQueue.withAddedTask(task)
                    )
                }
            }
            runBlocking { queueStateFlow.value.printWithColors() }
        }
    }

    fun terminateTask(task: Task, priority: Priority) {
        logger.atInfo().log("Removing task, uuid:${task.uuid}, priority:$priority")
        synchronized(lock) {
            val stateSnapshot = queueStateFlow.value
            when (priority) {
                LOWEST -> {
                    _queueStateFlow.value = stateSnapshot.copy(
                        lowestQueue = stateSnapshot.lowestQueue.withFirstTaskTerminated(task)
                    )
                }

                LOW -> {
                    _queueStateFlow.value = stateSnapshot.copy(
                        lowQueue = stateSnapshot.lowQueue.withFirstTaskTerminated(task)
                    )
                }

                MID -> {
                    _queueStateFlow.value = stateSnapshot.copy(
                        midQueue = stateSnapshot.midQueue.withFirstTaskTerminated(task)
                    )
                }

                HIGH -> {
                    _queueStateFlow.value = stateSnapshot.copy(
                        highQueue = stateSnapshot.highQueue.withFirstTaskTerminated(task)
                    )
                }
            }
            queueStateFlow.value.printWithColors()
        }
    }

    fun onTaskRelease(task: Task, priority: Priority) {
        logger.atInfo().log("Releasing task, uuid:${task.uuid}, priority:$priority")
        synchronized(lock) {
            val stateSnapshot = queueStateFlow.value
            when (priority) {
                LOWEST -> {
                    _queueStateFlow.value = stateSnapshot.copy(
                        lowestQueue = stateSnapshot.lowestQueue.withTaskReleased(task)
                    )
                }

                LOW -> {
                    _queueStateFlow.value = stateSnapshot.copy(
                        lowQueue = stateSnapshot.lowQueue.withTaskReleased(task)
                    )
                }

                MID -> {
                    _queueStateFlow.value = stateSnapshot.copy(
                        midQueue = stateSnapshot.midQueue.withTaskReleased(task)
                    )
                }

                HIGH -> {
                    _queueStateFlow.value = stateSnapshot.copy(
                        highQueue = stateSnapshot.highQueue.withTaskReleased(task)
                    )
                }
            }
            runBlocking { queueStateFlow.value.printWithColors() }
        }
    }

    private fun ArrayDeque<Task>.withAddedTask(task: Task) = this.apply { addLast(task) }

    private fun ArrayDeque<Task>.withFirstTaskTerminated(task: Task) = this.apply {
        if (task != firstOrNull()) throw LogicException("Task ${task.uuid} is not first in the queue", TASK_NOT_VALID)

        removeFirst()
        _completedTasks.add(task)
    }

    private fun ArrayDeque<Task>.withTaskReleased(task: Task) = this.apply {
        if (task != firstOrNull()) throw LogicException("Task ${task.uuid} is not first in the queue", TASK_NOT_VALID)

        removeFirst()
        addLast(task)
    }

    data class QueuePool(
        val lowestQueue: ArrayDeque<Task>,
        val lowQueue: ArrayDeque<Task>,
        val midQueue: ArrayDeque<Task>,
        val highQueue: ArrayDeque<Task>
    ) {
        fun printWithColors() {
            val infoColor = Color(255, 200, 255)
            println("\n\n" + "Message Queue:".withColor(infoColor))
            lowestQueue.toStringWithColors(LOWEST)
            lowQueue.toStringWithColors(LOW)
            midQueue.toStringWithColors(MID)
            highQueue.toStringWithColors(HIGH)
            println("Logs:".withColor(infoColor))
        }

        companion object {
            private const val RESET_COLOR = "\u001b[0m"
            private val STATE_TO_COLOR_MAP = mapOf(
                READY to Color(100, 255, 100),
                RUNNING to Color(100, 100, 255),
                SUSPENDED to Color(255, 170, 100),
                WAITING to Color(255, 100, 100)
            )

            private fun ArrayDeque<Task>.toStringWithColors(priority: Priority) {
                val sb = StringBuilder().apply {
                    append("QUEUE $priority priority")
                    appendLine()
                    this@toStringWithColors.forEach { task ->
                        val state = task.state
                        append("[UUID:${task.uuid}\tState:${state}]\t".withColor(STATE_TO_COLOR_MAP[state]!!))
                    }
                }
                println(sb.toString())
            }

            private fun String.withColor(color: Color) = withColor(color.red, color.green, color.blue)

            private fun String.withColor(r: Int, g: Int, b: Int) =
                "\u001b[38;2;$r;$g;${b}m" + this + RESET_COLOR
        }
    }
}

enum class Priority {
    LOWEST,
    LOW,
    MID,
    HIGH,
}
