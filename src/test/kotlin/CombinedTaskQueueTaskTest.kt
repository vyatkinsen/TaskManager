import org.example.Task
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.stream.IntStream
import kotlin.test.assertEquals

class CombinedTaskQueueTaskTest {
    @Test
    fun `should add task and change current state when add task`() {
        val subject = CombinedTaskQueue()
        IntStream.range(0, 10).forEach { index: Int ->
            val taskToAdd = Task.of(Priority.LOW, 1000L)

            assertTrue(subject.add(taskToAdd))
            assertEquals(States.READY, taskToAdd.currentState)
        }
    }

    @Test
    fun `should not add Task when don't have space`() {
        val subject = CombinedTaskQueue()
        IntStream.range(0, 10).forEach { t: Int -> subject.add(Task.of(Priority.LOW, 1000L)) }

        val shouldNotBeAdded = Task.of(Priority.LOW, 1000L)

        assertFalse(subject.add(shouldNotBeAdded))
        assertEquals(States.SUSPENDED, shouldNotBeAdded.currentState)
    }

    @Test
    fun `should take task and change state when have task`() {
        val subject = CombinedTaskQueue()

        val shouldBeTaken = Task.of(Priority.LOW, 1000L)
        subject.add(shouldBeTaken)

        assertEquals(shouldBeTaken, subject.take())
    }

    @Test
    fun `should take in right order`() {
        val subject = CombinedTaskQueue()

        val task4 = Task.of(Priority.LOW, 1000L)
        subject.add(task4)

        val task5 = Task.of(Priority.LOWEST, 1000L)
        subject.add(task5)

        val task1 = Task.of(Priority.HIGH, 1000L)
        subject.add(task1)

        val task3 = Task.of(Priority.MIDDLE, 1000L)
        subject.add(task3)

        val task2 = Task.of(Priority.HIGH, 1000L)
        subject.add(task2)

        val testList = listOf(task1, task2, task3, task4, task5)

        testList.iterator().forEach {
            assertEquals(it, subject.take())
        }

        assertTrue(subject.isEmpty())
    }
}
