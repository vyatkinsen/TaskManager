package task

import ExtendedTask
import Task
import Task.Action.ACTIVATE
import Task.Action.START
import Task.State.RUNNING
import Task.State.SUSPENDED
import generateUuid
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

fun getTaskInReadyState(timeToProcess: Long = 1000L) =
    Task(generateUuid(), timeToProcess).apply { tryMakeAction(ACTIVATE) }

fun getTaskInRunningState(timeToProcess: Long = 1000L) =
    getTaskInReadyState(timeToProcess).apply { tryMakeAction(START) }

fun getExtendedTaskInReadyState(waitTime: Long? = null, timeToProcess: Long = 1000L) =
    ExtendedTask(generateUuid(), timeToProcess, waitTime).apply { tryMakeAction(ACTIVATE) }

fun getExtendedTaskInRunningState(waitTime: Long? = null, timeToProcess: Long = 1000L) =
    getExtendedTaskInReadyState(waitTime, timeToProcess).apply { tryMakeAction(START) }

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