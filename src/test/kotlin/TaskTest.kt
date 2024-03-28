import org.example.Task
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class TaskTest {

    @Test
    fun shouldHaveSuspendedStateWhenEndRunTaskSuccessfully() {
        val underTest = Task.of(Priority.LOW, 1000L)
        underTest.currentState = States.READY

        underTest.call()
        assertEquals(States.SUSPENDED, underTest.currentState)
    }

    @Test
    fun shouldHaveWaitStateWhenEndRunExtendedTaskSuccessfully() {
        val underTest: Task = ExtendedTask.of(Priority.LOW, 1000L)
        underTest.currentState = States.READY

        underTest.call()

        assertEquals(States.WAIT, underTest.currentState)

    }

    @Test
    fun shouldHaveSuspendedStateWhenEndExtendedRunTaskSuccessfully() {
        val underTest: Task = ExtendedTask.of(Priority.LOW, 1000L)

        underTest.currentState = States.READY
        underTest.call()

        underTest.waitSomething()

        underTest.currentState = States.READY
        underTest.call()

        assertEquals(States.SUSPENDED, underTest.currentState)
    }

    @Test
    fun shouldThrowErrorWhenIllegalTransition() {
        val underTest: Task = ExtendedTask.of(Priority.LOW, 1000L)

        assertThrows<IllegalArgumentException> { underTest.currentState = States.RUNNING }
    }
}
