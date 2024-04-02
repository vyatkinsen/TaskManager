import LogicExceptionType.TASK_CANNOT_BE_PROCESSED
import Task.Action.*
import Task.State
import Task.State.READY
import Task.State.WAITING
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import org.slf4j.LoggerFactory

class TaskProcessor {
    private val logger = LoggerFactory.getLogger(javaClass)

    suspend fun process(
        task: Task,
        isProcessingAllowedFlow: StateFlow<Boolean>,
        onTaskStateChange: suspend (task: Task) -> Unit = {}
    ): State {
        task.requireReadyState()
        task.onStartProcessing()
        onTaskStateChange(task)

        if (task is ExtendedTask && task.waitTime != null && !task.isWaitCompleted) {
            return WAITING
        }

        return runTaskAndGetState(task, isProcessingAllowedFlow)
    }

    private fun Task.onStartProcessing() {
        logger.atInfo().log("Start processing task: $this")
        this.tryMakeAction(START)
    }

    private suspend fun runTaskAndGetState(task: Task, isProcessingAllowedFlow: StateFlow<Boolean>): State {
        var processTime = 0L
        while (task.processedTime + processTime < task.timeToProcess) {
            if (!isProcessingAllowedFlow.value) {
                handleInterruption(task, processTime)
                return task.state
            }
            processTime++
            delay(1)
        }

        handleFinishProcess(task, processTime)
        return task.state
    }

    private fun Task.requireReadyState() {
        if (this.state != READY) {
            throw LogicException("Task UUID:${this.uuid} is not in READY state", TASK_CANNOT_BE_PROCESSED).withLog(
                logger
            )
        }
    }

    private fun handleInterruption(task: Task, processTime: Long) {
        logger.atInfo().log("Interrupted while processing task:$task, processTime:$processTime")
        task.commitProcessTime(processTime)
        task.tryMakeAction(PREEMPT)
        logger.atInfo().log("Task: $task is now in ${task.state} state")
    }

    private fun handleFinishProcess(task: Task, processTime: Long) {
        task.commitProcessTime(processTime)
        task.tryMakeAction(TERMINATE)
        logger.atInfo().log("Task: ${task.uuid} ${task.state} has finished processing")
    }
}