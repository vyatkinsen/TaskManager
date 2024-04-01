import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import LogicExceptionType.TASK_CANNOT_BE_PROCESSED
import Task.Action.*
import Task.State.*
import Task.State

class TaskProcessor {
    private val logger = LoggerFactory.getLogger(javaClass)

    suspend fun process(
        task: Task,
        isProcessingAllowedFlow: StateFlow<Boolean>,
        onWaitComplete: (task: Task) -> Unit = {}
    ): State {
        task.requireReadyOrSuspended()
        task.onStartProcessing()

        if (task is ExtendedTask && !task.isWaitCompleted) {
            coroutineScope {
                launch { task.wait() }
            }.invokeOnCompletion {
                onWaitComplete(task)
            }
            return WAITING
        }

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

    private fun Task.onStartProcessing() {
        logger.atInfo().log("Start processing task: ${this.uuid}, ${this.state}")
        if (this.state == SUSPENDED) this.tryMakeAction(ACTIVATE)
        this.tryMakeAction(START)
    }

    private fun Task.requireReadyOrSuspended() {
        if (this.state != SUSPENDED && this.state != READY) {
            throw LogicException("Task is not in SUSPENDED or READY state", TASK_CANNOT_BE_PROCESSED).withLog(logger)
        }
    }

    private fun handleInterruption(task: Task, processTime: Long) {
        logger.atInfo().log("Interrupted while processing task: ${task.uuid}, ${task.state}")
        task.commitProcessTime(processTime)
        task.tryMakeAction(PREEMPT)
        logger.atInfo().log("Task: ${task.uuid} is now in ${task.state} state")
    }

    private fun handleFinishProcess(task: Task, processTime: Long) {
        task.commitProcessTime(processTime)
        task.tryMakeAction(TERMINATE)
        logger.atInfo().log("Task: ${task.uuid} ${task.state} has finished processing")
    }
}