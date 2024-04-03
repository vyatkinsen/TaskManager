import Task.Action.ACTIVATE
import Task.State.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors

class Scheduler(
    private val mq: MessageQueue,
    private val processor: TaskProcessor
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val highestPriorityTaskFlow =
        MutableSharedFlow<Task?>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST).apply {
            tryEmit(null)
        }
    private var currentTaskFlow =
        MutableSharedFlow<Task?>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST).apply {
            tryEmit(null)
        }
    private var runningJob = MutableStateFlow<Job?>(null)

    private val waitExecutor = Executors.newCachedThreadPool()

    val globalScope = CoroutineScope(Dispatchers.IO)

    suspend fun run() = withContext(Dispatchers.Default) {
        launch {
            consumeQueueUpdates()
        }
        launch {
            handleOnNewHighestPriority()
        }
        launch {
            consumeHighestPriorityTasks()
        }
    }

    private suspend fun consumeHighestPriorityTasks() {
        currentTaskFlow.filterNotNull().collect { newProcessTask ->

        }
    }

    private suspend fun consumeQueueUpdates() {
        mq.queueStateFlow.collect { queuePool ->
            emmitNextTask(queuePool)
        }
    }

    private suspend fun emmitNextTask(queuePool: MessageQueue.QueuePool) {
        val highestPriorityTask = queuePool.getHighestPriorityTaskOrNull()
        logger.atInfo()
            .log("MQ updated, highestPriority UUID:${highestPriorityTask?.uuid}, last priority UUID:${highestPriorityTaskFlow.first()?.uuid} currently processing:${currentTaskFlow.first()}")

        highestPriorityTaskFlow.emit(highestPriorityTask)
    }

    private suspend fun handleOnNewHighestPriority() {
        highestPriorityTaskFlow.filterNotNull().collect { newProcessTask ->
            if (currentTaskFlow.first() != newProcessTask) {
                logger.atInfo().log(
                    "Processor should run new task:$newProcessTask, currently processing:${currentTaskFlow.first()}"
                )

                interruptCurrentJob()

                currentTaskFlow.emit(newProcessTask)

                runNewTask(newProcessTask)
            }
        }
    }

    private fun runNewTask(newProcessTask: Task) {
        val job = globalScope.launch {
            if (newProcessTask.state == SUSPENDED) activateTask(newProcessTask)

            val state = processor.process(newProcessTask) {
                logger.atInfo().log("Task: $it changed state, ${mq.queueStateFlow.first().toColoredString()}")
            }

            logger.atInfo().log(
                "Finished processing task:$newProcessTask, state:$state, ${mq.queueStateFlow.first().toColoredString()}"
            )

            when (state) {
                SUSPENDED -> handleTaskCompletion(newProcessTask)

                WAITING -> {
                    logger.atInfo().log("Handling WAITING state on task:$newProcessTask")
                    waitExecutor.execute {
                        runBlocking {
                            (newProcessTask as ExtendedTask).wait(
                                beforeDelay = {
                                    logger.atInfo()
                                        .log("Task: $it changed state, ${mq.queueStateFlow.first().toColoredString()}")
                                }
                            )
                        }
                        logger.atInfo().log("Wait for task:$newProcessTask completed")

//                        highestPriorityTaskFlow.emit(null) // to update make update to same task possible
                        mq.onTaskRelease(newProcessTask)
                    }
                    logger.atInfo().log("Current task is now null")

                    currentTaskFlow.emit(null)
                    emmitNextTask(mq.queueStateFlow.first())
                }

                else -> {
                    // do nothing
                }
            }
        }
        job.invokeOnCompletion {
            logger.info("Completing job")
            if (it != null && it is CancellationException) {
                logger.atInfo()
                    .log("Interrupted processing task UUID:${newProcessTask.uuid}, processTime took ${newProcessTask.timeToProcess} ms")
                processor.handleInterruption(newProcessTask)
            }
        }
        runningJob.value = job
    }

    private fun handleTaskCompletion(task: Task) {
        logger.atInfo()
            .log("Terminating last processed task:[$task], currentJob = $currentTaskFlow")
        currentTaskFlow.tryEmit(null)
        mq.terminateTask(task)
    }

    private suspend fun activateTask(newProcessTask: Task) {
        newProcessTask.tryMakeAction(ACTIVATE)
        logger.atInfo().log("Activated task:$newProcessTask, ${mq.queueStateFlow.first().toColoredString()}")
    }


    private suspend fun interruptCurrentJob() {
        currentTaskFlow.first()?.let {
            logger.atInfo().log("Interrupting current processing:$it")
            currentTaskFlow.emit(null)
            runningJob.value?.cancelAndJoin()
        }
    }

    private fun MessageQueue.QueuePool.getHighestPriorityTaskOrNull() =
        highQueue.firstOrNull { it.canBeProcessed() }
            ?: midQueue.firstOrNull { it.canBeProcessed() }
            ?: lowQueue.firstOrNull { it.canBeProcessed() }
            ?: lowestQueue.firstOrNull { it.canBeProcessed() }

    private fun Task.canBeProcessed() = (state == SUSPENDED || state == READY) && processedTime < timeToProcess
}