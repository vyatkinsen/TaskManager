import Task.State.READY
import Task.State.SUSPENDED
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.slf4j.LoggerFactory

class Scheduler(
    private val mq: MessageQueue,
    private val processor: TaskProcessor
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val highestPriorityTaskFlow =
        MutableSharedFlow<Task?>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST).apply {
            tryEmit(null)
        }

    private var currentTask: Task? = null
    private var runningJob: Job? = null

    private val globalScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    suspend fun run() = withContext(Dispatchers.Default) {
        launch {
            consumeQueueUpdates()
        }
        launch {
            handleOnNewHighestPriority()
        }
    }

    private suspend fun consumeQueueUpdates() {
        mq.queueStateFlow.collectLatest { queuePool ->
            emmitNextTask(queuePool)
        }
    }

    private suspend fun emmitNextTask(queuePool: MessageQueue.QueuePool) {
        val highestPriorityTask = queuePool.getHighestPriorityTaskOrNull()
        logger.atWarn()
            .log("MQ updated, highestPriority UUID:${highestPriorityTask?.uuid}, last priority UUID:${highestPriorityTaskFlow.first()?.uuid} currently processing:${currentTask}")

        highestPriorityTaskFlow.emit(highestPriorityTask)
    }

    private suspend fun handleOnNewHighestPriority() {
        highestPriorityTaskFlow.filterNotNull().collect { newProcessTask ->
            if (currentTask?.uuid != newProcessTask.uuid && (newProcessTask.state == READY || newProcessTask.state == SUSPENDED)) {
                logger.atWarn().log(
                    "Processor should run new task:$newProcessTask, currently processing:${currentTask}"
                )

                runBlocking {
                    interruptCurrentJob()
                    currentTask = newProcessTask
                }

                runNewTask(newProcessTask)
            }
        }
    }

    private suspend fun runNewTask(newProcessTask: Task) {
        runningJob = processor.getProcessingJob(
            task = newProcessTask,
            supervisorCoroutineScope = globalScope,
            onTaskStateChangeLoggerMessage = { mq.queueStateFlow.first().toColoredString() },
            onTaskCompletion = { handleTaskCompletion(it) },
            onTaskRelease = { mq.onTaskRelease(newProcessTask) },
            onWaitingStateProcessed = {
                currentTask = null
                emmitNextTask(mq.queueStateFlow.first())
            }
        )
    }

    private fun handleTaskCompletion(task: Task) {
        logger.atInfo().log("Terminating last processed task:[$task], currentJob = $currentTask")
        mq.terminateTask(task)
    }

    private suspend fun interruptCurrentJob() {
        if (currentTask != null) {
            logger.atInfo().log("Interrupting current processing:$currentTask")
            runBlocking { runningJob?.cancelAndJoin() }
        }
    }

    private fun MessageQueue.QueuePool.getHighestPriorityTaskOrNull() =
        highQueue.firstOrNull { it.canBeProcessed() }
            ?: midQueue.firstOrNull { it.canBeProcessed() }
            ?: lowQueue.firstOrNull { it.canBeProcessed() }
            ?: lowestQueue.firstOrNull { it.canBeProcessed() }

    private fun Task.canBeProcessed() = (state == SUSPENDED || state == READY) && processedTime < timeToProcess
}