package task

import org.junit.jupiter.api.assertThrows
import LogicException
import Task
import Task.Action
import Task.Action.*
import Task.State.*
import generateUuid
import kotlin.test.Test
import kotlin.test.assertEquals

class TaskTransitionTest {
    @Test
    fun `initial state is SUSPENDED`() {
        // what
        val task = Task(generateUuid())

        // then
        assertEquals(SUSPENDED, task.state)
    }

    @Test
    fun `on ACTIVATE from SUSPENDED results in READY state`() {
        // what
        val task = Task(generateUuid())

        // when
        task.tryMakeAction(ACTIVATE)

        // then
        assertEquals(READY, task.state)
    }

    @Test
    fun `on !ACTIVATE from SUSPENDED results in Exception`() {
        // what
        val task = Task(generateUuid())

        Action.entries.filter { it != ACTIVATE } // when
            .forEach {
                // then
                assertThrows<LogicException> { task.tryMakeAction(it) }
            }
    }

    @Test
    fun `on START from READY results in RUNNING state`() {
        // what
        val task = getTaskInReadyState()

        // when
        task.tryMakeAction(START)

        // then
        assertEquals(RUNNING, task.state)
    }

    @Test
    fun `on !START from READY results in Exception`() {
        // what
        val task = getTaskInReadyState()

        Action.entries.filter { it != START } // when
            .forEach {
                // then
                assertThrows<LogicException> { task.tryMakeAction(it) }
            }
    }

    @Test
    fun `on PREEMPT from RUNNING results in READY state`() {
        // what
        val task = getTaskInRunningState()
        assertEquals(RUNNING, task.state)

        // when
        task.tryMakeAction(PREEMPT)

        // then
        assertEquals(READY, task.state)
    }

    @Test
    fun `on TERMINATE from RUNNING results in SUSPENDED state`() {
        // what
        val task = getTaskInRunningState()
        assertEquals(RUNNING, task.state)

        // when
        task.tryMakeAction(TERMINATE)

        // then
        assertEquals(SUSPENDED, task.state)
    }

    @Test
    fun `on !PREEMPT and !TERMINATE from RUNNING results in Exception`() {
        // what
        val task = getTaskInRunningState()

        Action.entries.filter { (it != PREEMPT) && (it != TERMINATE) } // when
            .forEach {
                // then
                assertThrows<LogicException> { task.tryMakeAction(it) }
            }
    }
}