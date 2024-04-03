import Task.Action.ACTIVATE
import Task.State.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
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
    private var runningJob = MutableStateFlow<Job?>(null)

    val globalScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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

    private fun runNewTask(newProcessTask: Task) {
        val job = globalScope.launch {
            if (newProcessTask.state == SUSPENDED) activateTask(newProcessTask)

            val state = processor.process(
                newProcessTask,
                onTaskWait = {
                    logger.atInfo().log("Task:$it changed state, ${mq.queueStateFlow.first().toColoredString()}")
                },
                onTaskStateChange = {
                    logger.atInfo().log("Task:$it changed state, ${mq.queueStateFlow.first().toColoredString()}")
                }
            )
            logger.atInfo().log("Task:$newProcessTask finished with state: $state")
        }
        job.invokeOnCompletion {
            logger.info("Completing job, UUID:${newProcessTask.uuid} state=${newProcessTask.state}, error=$it")

            runBlocking {
                logger.atInfo().log(
                    "Finished processing task:${newProcessTask.uuid}, state:${newProcessTask.state}, timeToProcess:${newProcessTask.timeToProcess}, processedTime:${newProcessTask.processedTime} ${
                        mq.queueStateFlow.first().toColoredString()
                    }"
                )
            }
            when (newProcessTask.state) {
                SUSPENDED -> {
                    handleTaskCompletion(newProcessTask)
                    return@invokeOnCompletion
                }

                WAITING -> {
                    logger.atInfo().log("Handling WAITING state on task:$newProcessTask")
                    globalScope.launch {
                        (newProcessTask as ExtendedTask).wait(
                            beforeDelay = {
                                logger.atInfo()
                                    .log(
                                        "Task: $it changed state, ${
                                            mq.queueStateFlow.first().toColoredString()
                                        }"
                                    )
                            }
                        )
                        logger.atInfo().log("Wait for task:$newProcessTask completed")

                        mq.onTaskRelease(newProcessTask)
                    }
                    logger.atInfo().log("Current task is now null")
                    runBlocking {
                        emmitNextTask(mq.queueStateFlow.first())
                    }
                    return@invokeOnCompletion
                }

                else -> {
                    // do nothing
                }
            }
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
            .log("Terminating last processed task:[$task], currentJob = $currentTask")
//        currentTaskFlow.tryEmit(null)
        mq.terminateTask(task)
    }

    private suspend fun activateTask(newProcessTask: Task) {
        newProcessTask.tryMakeAction(ACTIVATE)
        logger.atInfo().log("Activated task:$newProcessTask, ${mq.queueStateFlow.first().toColoredString()}")
    }


    private suspend fun interruptCurrentJob() {
        if (currentTask != null) {
            logger.atInfo().log("Interrupting current processing:$currentTask")
            runBlocking { runningJob.value?.cancelAndJoin() }
        }
    }

    private fun MessageQueue.QueuePool.getHighestPriorityTaskOrNull() =
        highQueue.firstOrNull { it.canBeProcessed() }
            ?: midQueue.firstOrNull { it.canBeProcessed() }
            ?: lowQueue.firstOrNull { it.canBeProcessed() }
            ?: lowestQueue.firstOrNull { it.canBeProcessed() }

    private fun Task.canBeProcessed() = (state == SUSPENDED || state == READY) && processedTime < timeToProcess
}