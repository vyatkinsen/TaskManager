import LogicExceptionType.TASK_NOT_FOUND
import LogicExceptionType.TASK_NOT_VALID
import Priority.*
import Task.State.*
import kotlinx.coroutines.flow.*
import org.slf4j.LoggerFactory
import java.awt.Color


class MessageQueue {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val taskToPriorityMap = mutableMapOf<String, Priority>()

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

    private val _queueSharedFlow = MutableSharedFlow<QueuePool>()
    val queueSharedFlow = _queueStateFlow.asSharedFlow()

    init {
        logger.atInfo().log(queueStateFlow.value.toColoredString())
    }

    fun addTask(task: Task, priority: Priority) {
        synchronized(lock) {
            val stateSnapshot = queueStateFlow.value
            taskToPriorityMap[task.uuid] = priority

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
            logger.atInfo().log(
                "Added new task, uuid:${task.uuid}, priority:$priority, ${queueStateFlow.value.toColoredString()}"
            )
        }
    }

    fun terminateTask(task: Task) {
        val priority = taskToPriorityMap[task.uuid]
            ?: throw LogicException("Task not found", TASK_NOT_FOUND).withLog(logger)

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
            logger.atInfo().log(
                "Removed task, uuid:${task.uuid}, priority:$priority, ${queueStateFlow.value.toColoredString()}"
            )
        }
    }

    fun onTaskRelease(task: Task) {
        val priority = taskToPriorityMap[task.uuid]
            ?: throw LogicException("Task not found", TASK_NOT_FOUND).withLog(logger)

        synchronized(lock) {
            val stateSnapshot = queueStateFlow.value
            when (priority) {
                LOWEST -> {
                    _queueStateFlow.value = stateSnapshot.copy(
                        lowestQueue = stateSnapshot.lowestQueue.withFirstTaskTerminated(task)
                    )
                    _queueStateFlow.value = stateSnapshot.copy(
                        lowestQueue = stateSnapshot.lowestQueue.withAddedTask(task)
                    )
                }

                LOW -> {
                    _queueStateFlow.value = stateSnapshot.copy(
                        lowQueue = stateSnapshot.lowQueue.withFirstTaskTerminated(task)
                    )
                    _queueStateFlow.value = stateSnapshot.copy(
                        lowQueue = stateSnapshot.lowQueue.withAddedTask(task)
                    )
                }

                MID -> {
                    _queueStateFlow.value = stateSnapshot.copy(
                        midQueue = stateSnapshot.midQueue.withFirstTaskTerminated(task)
                    )
                    _queueStateFlow.value = stateSnapshot.copy(
                        midQueue = stateSnapshot.midQueue.withAddedTask(task)
                    )
                }

                HIGH -> {
                    _queueStateFlow.value = stateSnapshot.copy(
                        highQueue = stateSnapshot.highQueue.withFirstTaskTerminated(task)
                    )
                    _queueStateFlow.value = stateSnapshot.copy(
                        highQueue = stateSnapshot.highQueue.withAddedTask(task)
                    )
                }
            }
            logger.atInfo().log(
                "Released task with uuid:${task.uuid}, priority:$priority, ${queueStateFlow.value.toColoredString()}"
            )
        }
    }

    private fun a() {

    }

    private fun ArrayDeque<Task>.withAddedTask(task: Task) =
        ArrayDeque<Task>().apply {
            addAll(this@withAddedTask)
            addLast(task)
        }


    private fun ArrayDeque<Task>.withFirstTaskTerminated(task: Task) =
        ArrayDeque<Task>().apply {
            addAll(this@withFirstTaskTerminated)
            if (task != firstOrNull()) throw LogicException(
                "Task ${task.uuid} is not first in the queue",
                TASK_NOT_VALID
            )

            removeFirst()
            _completedTasks.add(task)
        }

    private fun ArrayDeque<Task>.withTaskReleased(task: Task) = ArrayDeque<Task>().apply {
        addAll(this@withTaskReleased)
        if (task != firstOrNull()) {
            throw LogicException("Task ${task.uuid} is not first in the queue", TASK_NOT_VALID).withLog(logger)
        }

        removeFirst()
        addLast(task)
    }

    data class QueuePool(
        val lowestQueue: ArrayDeque<Task>,
        val lowQueue: ArrayDeque<Task>,
        val midQueue: ArrayDeque<Task>,
        val highQueue: ArrayDeque<Task>
    ) {
        fun toColoredString(): String {
            val infoColor = Color(255, 200, 255)
            val sb = StringBuilder()
                .appendLine()
                .append("\t\t")
                .append("Message Queue:".withColor(infoColor))
                .append(lowestQueue.toStringWithColors(LOWEST))
                .append(lowQueue.toStringWithColors(LOW))
                .append(midQueue.toStringWithColors(MID))
                .append(highQueue.toStringWithColors(HIGH))
            return sb.toString()
        }

        private fun ArrayDeque<Task>.toStringWithColors(priority: Priority): String {
            val sb = StringBuilder().apply {
                append("QUEUE $priority:{".withColor(QUEUE_COLOR))
                val tasksList = mutableListOf<String>()
                this@toStringWithColors.forEach { task ->
                    tasksList.add("$task".withColor(STATE_TO_COLOR_MAP[task.state]!!))
                }
                append(tasksList.joinToString(", "))
                append("}\t".withColor(QUEUE_COLOR))
            }
            return sb.toString()
        }

        companion object {
            private const val RESET_COLOR = "\u001b[0m"
            private val QUEUE_COLOR = Color(200, 255, 255)
            private val STATE_TO_COLOR_MAP = mapOf(
                READY to Color(100, 255, 100),
                RUNNING to Color(100, 100, 255),
                SUSPENDED to Color(255, 170, 100),
                WAITING to Color(255, 100, 100)
            )

            private fun String.withColor(r: Int, g: Int, b: Int) =
                "\u001b[38;2;$r;$g;${b}m" + this + RESET_COLOR

            fun String.withColor(color: Color) = withColor(color.red, color.green, color.blue)
        }
    }
}

enum class Priority {
    LOWEST,
    LOW,
    MID,
    HIGH,
}
