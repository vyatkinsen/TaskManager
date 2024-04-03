import Task.State.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import task.getTaskInReadyState
import task.getTaskInRunningState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TaskProcessorTest {
    @Test
    fun `processes one task`(): Unit = runBlocking {
        val processor = TaskProcessor()
        val task = getTaskInReadyState(timeToProcess = 3)
        assertEquals(0, task.processedTime)
        val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())


        val job = processor.getProcessingJob(
            task,
            coroutineScope,
            onTaskStateChangeLoggerMessage = { "" },
            onTaskCompletion = { },
            onTaskRelease = { },
            onWaitingStateProcessed = { },
        )

        job.join()

        assertEquals(task.timeToProcess, task.processedTime)
        assertEquals(SUSPENDED, task.state)
    }

    @Test
    fun `try process task and get interrupted`(): Unit = runBlocking {
        val processor = TaskProcessor()
        val task = getTaskInReadyState(timeToProcess = 100)
        assertEquals(0, task.processedTime)
        val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        var isCompleted = false

        val job = processor.getProcessingJob(
            task,
            coroutineScope,
            onTaskStateChangeLoggerMessage = { "" },
            onTaskCompletion = { isCompleted = true },
            onTaskRelease = { },
            onWaitingStateProcessed = { },
        )

        job.cancelAndJoin()

        assertTrue(task.timeToProcess > task.processedTime)
        assertEquals(READY, task.state)
        assertFalse(isCompleted)
    }

    @Test
    fun `processes extended task with should wait trait`(): Unit = runBlocking {
        val processor = TaskProcessor()
        val task = ExtendedTask(generateUuid(), waitTime = 100)
        assertEquals(0, task.processedTime)
        val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        var isCompleted = false
        var isWaitingFinished = false

        val job = processor.getProcessingJob(
            task,
            coroutineScope,
            onTaskStateChangeLoggerMessage = { "" },
            onTaskCompletion = { isCompleted = true },
            onTaskRelease = { isWaitingFinished = true },
            onWaitingStateProcessed = { },
        )

        job.join()
        assertTrue(task.timeToProcess > task.processedTime)
        assertEquals(WAITING, task.state)
        assertFalse(isCompleted)
        while (!isWaitingFinished) {
            delay(1)
        }
        assertEquals(READY, task.state)
    }
}