import LogicExceptionType.TASK_CANNOT_BE_PROCESSED
import Task.Action.*
import Task.State
import Task.State.*
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

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
            return WAITING
        }

        var processTime = 0L
        while (task.processedTime + processTime < task.timeToProcess) {
            delay(1)
            processTime++
            task.commitProcessTime(1)
        }

        handleFinishProcess(task, processTime)
        return task.state
    }

    private fun Task.onStartProcessing() {
        logger.atInfo().log("Start processing task: $this")
        this.tryMakeAction(START)
    }

//    private suspend fun runTaskAndGetState(task: Task, isProcessingAllowedFlow: StateFlow<Boolean>): State {
//        var processTime = 0L
//        while (task.processedTime + processTime < task.timeToProcess) {
//            if (!isProcessingAllowedFlow.value) {
//                logger.atInfo().log("Interrupted processing task UUID:${task.uuid}, processTime took $processTime ms")
////                handleInterruption(task, processTime)
//                return task.state
//            }
//            processTime++
//            delay(1)
//        }
//
//        handleFinishProcess(task, processTime)
//        processTime = 0
//        return task.state
//    }

    private fun Task.requireReadyState() {
        if (this.state != READY) {
            throw LogicException("Task UUID:${this.uuid} is not in READY state", TASK_CANNOT_BE_PROCESSED).withLog(
                logger
            )
        }
    }

    fun handleInterruption(task: Task) {
        logger.atInfo().log("Interrupted while processing task:$task, processTime:${task.processedTime}")
        if (task.state == RUNNING) task.tryMakeAction(PREEMPT)
        logger.atInfo().log("Task: $task is now in ${task.state} state")
    }

    private fun handleFinishProcess(task: Task, processTime: Long) {
        task.commitProcessTime(processTime)
        task.tryMakeAction(TERMINATE)
        logger.atInfo().log("Task: ${task.uuid} ${task.state} has finished processing")
    }
}