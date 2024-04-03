import LogicExceptionType.TASK_NOT_FOUND
import LogicExceptionType.TASK_NOT_VALID
import Priority.*
import Task.State.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.awt.Color
import java.lang.Exception


class MessageQueue {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val taskToPriorityMap = mutableMapOf<String, Priority>()

    private val _completedTasks = mutableListOf<Task>()
    val completedTasks: List<Task>
        get() = _completedTasks

    private val lock = Any()

    private val state = QueuePool(
        ArrayDeque(),
        ArrayDeque(),
        ArrayDeque(),
        ArrayDeque()
    )

    private val _queueStateFlow =
        MutableSharedFlow<QueuePool>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val queueStateFlow = _queueStateFlow.asSharedFlow()


    init {
        logger.atInfo().log(state.toColoredString())
    }

    suspend fun addTask(task: Task, priority: Priority) {
        synchronized(lock) {
            taskToPriorityMap[task.uuid] = priority
            getQueueByPriority(priority).addLast(task)
            runBlocking { _queueStateFlow.emit(state) }

            logger.atInfo().log(
                "Added new task, uuid:${task.uuid}, priority:$priority, ${state.toColoredString()}"
            )
        }
    }

    fun terminateTask(task: Task) {
        synchronized(lock) {
            val priority = taskToPriorityMap[task.uuid]
                ?: throw LogicException("Task UUID:${task.uuid} not found", TASK_NOT_FOUND).withLog(logger)
            getQueueByPriority(priority).terminateTask(task)
            _completedTasks.add(task)
            runBlocking { _queueStateFlow.emit(state) }
            logger.atInfo().log(
                "Removed task, UUID:${task.uuid}, PRIORITY:$priority, ${state.toColoredString()}"
            )
        }
    }

    fun onTaskRelease(task: Task) {
        synchronized(lock) {
            val priority = taskToPriorityMap[task.uuid]
                ?: throw LogicException("Task not found", TASK_NOT_FOUND).withLog(logger)
            getQueueByPriority(priority).terminateTask(task).addLast(task)
            runBlocking { _queueStateFlow.emit(state) }
            logger.atInfo().log(
                "Released task UUID:${task.uuid}, PRIORITY:$priority, ${state.toColoredString()}"
            )
        }
    }

    private fun getQueueByPriority(priority: Priority) = when (priority) {
        LOWEST -> state.lowestQueue
        LOW -> state.lowQueue
        MID -> state.midQueue
        HIGH -> state.highQueue
    }

    private fun ArrayDeque<Task>.withAddedTask(task: Task) =
        ArrayDeque<Task>().apply {
            addAll(this@withAddedTask)
            addLast(task)
        }


    private fun ArrayDeque<Task>.terminateTask(task: Task) = this.apply {
        val result = remove(task)
        if (!result) throw LogicException(
            "Task ${task.uuid} could not be removed",
            TASK_NOT_VALID
        )
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
        fun toSnapshot() = QueuePool(
            lowestQueue = ArrayDeque<Task>().apply { addAll(lowestQueue) },
            lowQueue = ArrayDeque<Task>().apply { addAll(lowQueue) },
            midQueue = ArrayDeque<Task>().apply { addAll(midQueue) },
            highQueue = ArrayDeque<Task>().apply { addAll(highQueue) },
        )

        fun toColoredString(): String {
            val infoColor = Color(255, 200, 255)
            val charOffset = "\t\t"
            val sb = StringBuilder()
                .appendLine()
                .appendLine(charOffset + "Message Queue:".withColor(infoColor))
                .appendLine(charOffset + lowestQueue.toStringWithColors(LOWEST))
                .appendLine(charOffset + lowQueue.toStringWithColors(LOW))
                .appendLine(charOffset + midQueue.toStringWithColors(MID))
                .appendLine(charOffset + highQueue.toStringWithColors(HIGH))
            return sb.toString()
        }

        private fun ArrayDeque<Task>.toStringWithColors(priority: Priority): String {
            try {
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
            } catch (e:Exception) {
                return "ERROR WHILE COMPOSING"
            }
        }

        companion object {
            private const val RESET_COLOR = "\u001b[0m"
            private val QUEUE_COLOR = Color(200, 255, 255)
            private val STATE_TO_COLOR_MAP = mapOf(
                READY to Color(100, 255, 100),
                RUNNING to Color(150, 150, 255),
                SUSPENDED to Color(255, 170, 100),
                WAITING to Color(255, 255, 100)
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
