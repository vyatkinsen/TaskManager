package task

import org.junit.jupiter.api.Test
import ExtendedTask
import Task
import Task.Action.*
import Task.State.*
import generateUuid
import kotlin.test.assertEquals

fun getTaskInReadyState() = Task(generateUuid()).apply { tryMakeAction(ACTIVATE) }
fun getTaskInRunningState() = getTaskInReadyState().apply { tryMakeAction(START) }

fun getExtendedTaskInReadyState(waitTime: Long? = null) = ExtendedTask(generateUuid(), waitTime = waitTime).apply { tryMakeAction(ACTIVATE) }
fun getExtendedTaskInRunningState(waitTime: Long? = null) = getExtendedTaskInReadyState(waitTime).apply { tryMakeAction(START) }

class HelperTest {
    @Test
    fun getTaskInRunningStateTest() {
        val task = getTaskInRunningState()

        assertEquals(RUNNING, task.state)
    }

    @Test
    fun getTaskInSuspendedStateTest() {
        val task = Task(generateUuid())

        assertEquals(SUSPENDED, task.state)
    }

    @Test
    fun getExtendedTaskInRunningStateTest() {
        val task = getExtendedTaskInRunningState()

        assertEquals(RUNNING, task.state)
    }
}