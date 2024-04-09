package task

import ExtendedTask.ExtendedAction.WAIT
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import Task.State.*
import kotlin.test.assertEquals

class ExtendedTaskTransitionTest {
    @Test
    fun `can wait and then release`() {
        // what
        val waitTime = 10L
        val task = getExtendedTaskInRunningState(waitTime = waitTime)
        task.tryMakeExtendedAction(WAIT)
        assertEquals(WAITING, task.state)

        // when
        runBlocking {
            var time: Long? = null
            launch {
                task.wait { time = System.currentTimeMillis() }
                assertEquals(READY, task.state)
                assertTrue(task.isWaitCompleted)
                assertTrue(System.currentTimeMillis() - time!! in (waitTime..2 * waitTime))
            }
        }
    }
}
