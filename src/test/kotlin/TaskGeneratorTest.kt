import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class TaskGeneratorTest {
    @Test
    fun shouldCreate10Tasks() {
        val generatedList = TaskGenerator.createTasks(10)

        assertEquals( 10, generatedList.size)
    }

    @Test
    fun shouldCreateDifferentTasks() {
        val task1 = TaskGenerator.createRandomTask()
        val task2 = TaskGenerator.createRandomTask()

        assertNotEquals(task1, task2)
    }
}
