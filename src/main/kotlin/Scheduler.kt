import Task.Action.ACTIVATE
import Task.State.SUSPENDED
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import kotlin.coroutines.CoroutineContext

class Scheduler(
    private val mq: MessageQueue,
    private val processor: TaskProcessor
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val highestPriorityTaskFlow = MutableStateFlow<Task?>(null)
    private var currentTaskFlow = MutableStateFlow<Task?>(null)

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

            val deferred = runProcessingTask(context, newProcessTask)
            deferred.await()

            logger.atInfo().log(
                "Finished processing task:$newProcessTask, ${mq.queueStateFlow.value.toColoredString()}"
            )

            if (newProcessTask.state == SUSPENDED) {
                logger.atInfo().log(
                    "Terminating last processed task:[$newProcessTask], currentJob = $currentTaskFlow"
                )
                mq.terminateTask(newProcessTask)
            }
        }

        println("COLLECT COMPLETED")

    }

    private suspend fun consumeQueueUpdates() {
        mq.queueStateFlow.collect { queuePool ->
            val highestPriorityTask = queuePool.getHighestPriorityTaskOrNull()
            logger.atInfo()
                .log("MQ updated, highestPriority:$highestPriorityTask, currently processing:${currentTaskFlow.value}")

            highestPriorityTaskFlow.value = highestPriorityTask
        }
    }

    private suspend fun handleOnNewHighestPriority(context: CoroutineContext) = withContext(context) {
        highestPriorityTaskFlow.filterNotNull().collect { newProcessTask ->
            logger.atInfo().log(
                "Processor should run new task:$newProcessTask, currently processing:${currentTaskFlow.value}, " +
                        mq.queueStateFlow.value.toColoredString()
            )

            interruptCurrentJob()
            currentTaskFlow.value = newProcessTask
        }
    }

    private fun activateTask(newProcessTask: Task) {
        newProcessTask.tryMakeAction(ACTIVATE)
        logger.atInfo().log("Activated task:$newProcessTask, ${mq.queueStateFlow.value.toColoredString()}")
    }

    private suspend fun runProcessingTask(
        context: CoroutineContext,
        task: Task
    ) = withContext(context) {
        val deferred = async {
            processor.process(
                task = task,
                isProcessingAllowedFlow = isProcessingAllowedFlow.asStateFlow(),
                onWaitComplete = {
                    logger.atInfo().log("Wait for task:$task completed")
                    mq.onTaskRelease(it)
                },
                onTaskStateChange = {
                    logger.atInfo().log("Task: $task changed state, ${mq.queueStateFlow.value.toColoredString()}")
                }
            )
        }
        return@withContext deferred
    }


    private suspend fun interruptCurrentJob() {
        currentTaskFlow.value?.let { job ->
            logger.atInfo().log("Interrupting current processing")
            isProcessingAllowedFlow.value = false
        }
    }

    private fun MessageQueue.QueuePool.getHighestPriorityTaskOrNull() =
        highQueue.firstOrNull() ?: midQueue.firstOrNull() ?: lowQueue.firstOrNull() ?: lowestQueue.firstOrNull()
}