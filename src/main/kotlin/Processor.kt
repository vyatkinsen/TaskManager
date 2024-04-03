import ExtendedTask.ExtendedAction.WAIT
import LogicExceptionType.ILLEGAL_TRANSITION
import LogicExceptionType.TASK_CANNOT_BE_PROCESSED
import Task.Action.*
import Task.State
import Task.State.*
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory

class TaskProcessor {
    private val logger = LoggerFactory.getLogger(javaClass)

    suspend fun process(
        task: Task,
        onTaskStateChangeLoggerMessage: suspend () -> String,
    ): State {
        task.requireReadyState()
        task.onStartProcessing()

        task.onTaskStateChange(onTaskStateChangeLoggerMessage.invoke())

        if (task is ExtendedTask && task.waitTime != null && !task.isWaitCompleted) {
            task.tryMakeExtendedAction(WAIT)
            task.onTaskStateChange(onTaskStateChangeLoggerMessage.invoke())

            return task.state
        }

        task.doWork()
        task.handleFinishProcess()

        return task.state
    }

    suspend fun getProcessingJob(
        task: Task,
        supervisorCoroutineScope: CoroutineScope,
        onTaskStateChangeLoggerMessage: suspend () -> String = { "" },
        onTaskCompletion: (task: Task) -> Unit,
        onTaskRelease: suspend (task: Task) -> Unit,
        onWaitingStateProcessed: suspend () -> Unit
    ): Job {
        return supervisorCoroutineScope.launch {
            if (task.state == SUSPENDED) {
                task.tryMakeAction(ACTIVATE)
                task.onTaskStateChange(onTaskStateChangeLoggerMessage.invoke())
            }

            val state = process(task, onTaskStateChangeLoggerMessage)
            logger.atInfo().log("Task:$task finished with state: $state")
        }.withHandlingOnCompleteHandling(
            task,
            supervisorCoroutineScope,
            onTaskStateChangeLoggerMessage,
            onTaskCompletion,
            onTaskRelease,
            onWaitingStateProcessed
        )
    }

    private fun Task.onTaskStateChange(additionalInfo: String) {
        logger.atInfo().log("Task:$this changed state, $additionalInfo")
    }

    private fun Job.withHandlingOnCompleteHandling(
        task: Task,
        coroutineScope: CoroutineScope,
        onTaskStateChangeLoggerMessage: suspend () -> String,
        onTaskCompletion: (task: Task) -> Unit,
        onTaskRelease: suspend (task: Task) -> Unit,
        onWaitingStateProcessed: suspend () -> Unit
    ) =
        this.apply {
            invokeOnCompletion {
                logger.info("Completing job, UUID:${task.uuid} state=${task.state}, error=$it")

                runBlocking {
                    logger.atInfo().log(
                        "Completing job, UUID:${task.uuid} state=${task.state}, timeToProcess:${task.timeToProcess}, processedTime:${task.processedTime}, error=$it, ${onTaskStateChangeLoggerMessage.invoke()}"
                    )
                }
                if (task.state == SUSPENDED) {
                    onTaskCompletion(task)
                    return@invokeOnCompletion
                }
                if (task.state == WAITING) {
                    logger.atInfo().log("Handling WAITING state on task:$task")
                    coroutineScope.launch {
                        (task as ExtendedTask).wait(
                            beforeDelay = {
                                logger.atInfo()
                                    .log("Task: $it changed state, ${onTaskStateChangeLoggerMessage.invoke()}")
                            }
                        )
                        logger.atInfo().log("Wait for task:$task completed")

                        onTaskRelease(task)
                    }
                    logger.atInfo().log("Current task is now null")
                    runBlocking {
                        onWaitingStateProcessed()
                    }
                    return@invokeOnCompletion
                }

                if (it != null && it is CancellationException) {
                    logger.atInfo()
                        .log("Interrupted processing task UUID:${task.uuid}, processTime took ${task.timeToProcess} ms")
                    handleInterruption(task)
                }
            }
        }

    private suspend fun Task.doWork() {
        var processTime = 0L
        while (this.processedTime + 1 <= this.timeToProcess) {
            delay(1)
            processTime++
            this.commitProcessTime(1)
        }
    }

    private fun Task.onStartProcessing() {
        logger.atInfo().log("Start processing task: $this")
        this.tryMakeAction(START)
    }

    private fun Task.requireReadyState() {
        if (this.state != READY) {
            throw LogicException("Task UUID:${this} is not in READY state", TASK_CANNOT_BE_PROCESSED).withLog(
                logger
            )
        }
    }

    fun handleInterruption(task: Task) {
        logger.atInfo().log("Interrupted while processing task:$task, processTime:${task.processedTime}")
        if (task.processedTime == task.timeToProcess) {
            throw LogicException(
                "Trying to interrupt task:${task.uuid} in ${task.state} with finished time to process",
                ILLEGAL_TRANSITION
            ).withLog(logger)
        }
        if (task.state == RUNNING) task.tryMakeAction(PREEMPT)
        logger.atInfo().log("Task: $task is now in ${task.state} state")
    }

    private fun Task.handleFinishProcess() {
        this.tryMakeAction(TERMINATE)
        logger.atInfo().log("Task: ${this.uuid} ${this.state} has finished processing")
    }
}