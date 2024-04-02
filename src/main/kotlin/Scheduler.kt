import Task.Action.ACTIVATE
import Task.State.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

class Scheduler(
    private val mq: MessageQueue,
    private val processor: TaskProcessor
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val highestPriorityTaskFlow = MutableStateFlow<Task?>(null)
    private var currentTaskFlow =
        MutableSharedFlow<Task?>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST).apply {
            tryEmit(null)
        }

    private val waitScope = Executors.newSingleThreadExecutor()

    private val isProcessingAllowedFlow = MutableStateFlow(true)

    suspend fun run() = withContext(Dispatchers.Default) {
        launch {
            consumeQueueUpdates()
        }
        launch {
            handleOnNewHighestPriority(this.coroutineContext)
        }
        launch {
            consumeHighestPriorityTasks(this.coroutineContext)
        }
    }

    private suspend fun consumeHighestPriorityTasks(context: CoroutineContext) {
        currentTaskFlow.filterNotNull().collect { newProcessTask ->
            if (newProcessTask.state == SUSPENDED) activateTask(newProcessTask)
            isProcessingAllowedFlow.value = true

            val state = runProcessingTask(newProcessTask)

            logger.atInfo().log(
                "Finished processing task:$newProcessTask, state:$state, ${mq.queueStateFlow.first().toColoredString()}"
            )

            if (newProcessTask.state == SUSPENDED) {
                logger.atInfo().log("Terminating last processed task:[$newProcessTask], currentJob = $currentTaskFlow")
                mq.terminateTask(newProcessTask)
            } else if (state == WAITING) {
                logger.atInfo().log("Handling WAITING state on task:$newProcessTask")
                waitScope.execute {
                    runBlocking {
                        (newProcessTask as ExtendedTask).wait {
                            logger.atInfo()
                                .log("Task: $it changed state, ${mq.queueStateFlow.first().toColoredString()}")
                        }
                    }
                    logger.atInfo().log("Wait for task:$newProcessTask completed")
                    highestPriorityTaskFlow.value = null

                    mq.onTaskRelease(newProcessTask)
                }
                logger.atInfo().log("Current task is now null")
                currentTaskFlow.emit(null)
                val nextTask = mq.queueStateFlow.first().getHighestPriorityTaskOrNull()
                logger.atInfo().log(
                    "CurrentHighest priority: UUID:${highestPriorityTaskFlow.value?.uuid}, new UUID:${nextTask?.uuid}"
                )
                highestPriorityTaskFlow.value = nextTask
            }
        }

        println("COLLECT COMPLETED")

    }

    private suspend fun consumeQueueUpdates() {
        mq.queueStateFlow.collect { queuePool ->
            val highestPriorityTask = queuePool.getHighestPriorityTaskOrNull()
            logger.atInfo()
                .log("MQ updated, highestPriority:$highestPriorityTask, currently processing:${currentTaskFlow.first()}")

            highestPriorityTaskFlow.value = highestPriorityTask
        }
    }

    private suspend fun handleOnNewHighestPriority(context: CoroutineContext) = withContext(context) {
        highestPriorityTaskFlow.filterNotNull().collect { newProcessTask ->
            logger.atInfo().log(
                "Processor should run new task:$newProcessTask, currently processing:${currentTaskFlow.first()}"
            )

            interruptCurrentJob()

            currentTaskFlow.emit(newProcessTask)
        }
    }

    private suspend fun activateTask(newProcessTask: Task) {
        newProcessTask.tryMakeAction(ACTIVATE)
        logger.atInfo().log("Activated task:$newProcessTask, ${mq.queueStateFlow.first().toColoredString()}")
    }

    private suspend fun runProcessingTask(
        task: Task
    ) = processor.process(
        task = task,
        isProcessingAllowedFlow = isProcessingAllowedFlow.asStateFlow(),
        onWaitComplete = {

        },
        onTaskStateChange = {

        }
    )


    private suspend fun interruptCurrentJob() {
        currentTaskFlow.first()?.let { task ->
            logger.atInfo().log("Interrupting current processing:$task")
            isProcessingAllowedFlow.value = false
            currentTaskFlow.emit(null)
        }
    }

    private fun MessageQueue.QueuePool.getHighestPriorityTaskOrNull() =
        highQueue.firstOrNull { it.state == SUSPENDED || it.state == READY }
            ?: midQueue.firstOrNull { it.state == SUSPENDED || it.state == READY }
            ?: lowQueue.firstOrNull { it.state == SUSPENDED || it.state == READY }
            ?: lowestQueue.firstOrNull { it.state == SUSPENDED || it.state == READY }
}