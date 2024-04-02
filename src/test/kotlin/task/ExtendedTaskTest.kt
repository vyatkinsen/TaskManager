package task

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import Task.State.*
import kotlin.test.assertEquals

class ExtendedTaskTransitionTest {
    // EXTENDED
    @Test
    fun `can wait and then release`() {
        // what
        val task = getExtendedTaskInRunningState(waitTime = 10)
        assertEquals(RUNNING, task.state)

        // when
        runBlocking {
            launch { task.wait {} }
            launch {
                delay(1)
                assertEquals(WAITING, task.state)
                delay(10)
                assertEquals(READY, task.state)
                assertTrue(task.isWaitCompleted)
            }
        }
    }
}
