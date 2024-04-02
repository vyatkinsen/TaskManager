import Task.State.*
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import task.getExtendedTaskInReadyState
import task.getTaskInReadyState
import task.getTaskInRunningState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TaskProcessorTest {
    private val noInterruptionsFlow = MutableStateFlow(true)

    @Test
    fun `processes one task`() {
        val processor = TaskProcessor()
        val task = getTaskInReadyState(timeToProcess = 3)
        assertEquals(0, task.processedTime)
        val taskState = runBlocking { processor.process(task, noInterruptionsFlow, {}) }

        assertEquals(task.timeToProcess, task.processedTime)
        assertEquals(SUSPENDED, taskState)
    }

    @Test
    fun `try process task and get interrupted`(): Unit = runBlocking {
        val processor = TaskProcessor()
        val task = getTaskInReadyState(timeToProcess = 100)
        assertEquals(0, task.processedTime)
        val withInterruptionFlow = MutableStateFlow(true)

        val deferred = async { processor.process(task, withInterruptionFlow, {}) }
        delay(1)
        withInterruptionFlow.value = false
        deferred.await()

        assertTrue { task.timeToProcess > task.processedTime }
        assertEquals(READY, task.state)
    }

    @Test
    fun `processes extended task with should wait trait`(): Unit = runBlocking {
        val processor = TaskProcessor()
        val task = getExtendedTaskInReadyState(timeToProcess = 10, waitTime = 100)
        assertEquals(0, task.processedTime)
        var hasWaited = false

        launch {
            processor.process(
                task,
                noInterruptionsFlow,
                onWaitComplete = {
                    hasWaited = true
                }
            )
        }
        delay(50)

        assertEquals(WAITING, task.state)
        assertEquals(0, task.processedTime)
        assertFalse(hasWaited)

        delay(100)

        assertEquals(READY, task.state)
        assertEquals(0, task.processedTime)
        assertTrue(hasWaited)
    }

    @Test
    fun `processes only tasks in READY states`() {
        val processor = TaskProcessor()
        val eventFlow = MutableStateFlow(true)

        var task = getTaskInReadyState()
        assertEquals(READY, task.state)
        assertDoesNotThrow {
            runBlocking { processor.process(task, eventFlow, {}) }
        }

        task = Task(generateUuid(), timeToProcess = 3)
        assertEquals(SUSPENDED, task.state)
        assertThrows<LogicException> {
            runBlocking { processor.process(task, eventFlow, {}) }
        }

        task = getTaskInRunningState()
        assertThrows<LogicException> {
            runBlocking { processor.process(task, eventFlow, {}) }
        }
    }
}