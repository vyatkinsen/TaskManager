import ExtendedTask.ExtendedAction.WAIT
import LogicExceptionType.ILLEGAL_TRANSITION
import LogicExceptionType.TASK_CANNOT_BE_PROCESSED
import Task.Action.*
import Task.State
import Task.State.*
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory

class TaskProcessor {
    private val logger = LoggerFactory.getLogger(javaClass)

    suspend fun process(
        task: Task,
        onTaskStateChange: suspend (task: Task) -> Unit = {},
        onTaskWait: suspend (task: Task) -> Unit = {}
    ): State {
        task.requireReadyState()
        task.onStartProcessing()
        onTaskStateChange(task)

        if (task is ExtendedTask && task.waitTime != null && !task.isWaitCompleted) {
            onTaskWait(task)
            task.tryMakeExtendedAction(WAIT)

            return task.state
        }

        var processTime = 0L
        while (task.processedTime + 1 <= task.timeToProcess) {
            delay(1)
            processTime++
            task.commitProcessTime(1)
        }

        handleFinishProcess(task)
        return task.state
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

    private fun handleFinishProcess(task: Task) {
        task.tryMakeAction(TERMINATE)
        logger.atInfo().log("Task: ${task.uuid} ${task.state} has finished processing")
    }
}