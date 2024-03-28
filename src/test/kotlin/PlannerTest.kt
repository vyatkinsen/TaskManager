import org.example.Task
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Интеграционный тест
 */
class PlannerTest {

    @Test
    fun shouldCompleteAllTaskInRightOrder() {
        val t1 = Task.of(Priority.LOW, 150)
        val t2 = ExtendedTask.of(Priority.LOWEST, 250, 100, 200)
        val t3 = Task.of(Priority.HIGH, 300)
        val t4 = Task.of(Priority.MIDDLE, 400)
        val t5 = ExtendedTask.of(Priority.HIGH, 400, 150, 300)
        val t6 = Task.of(Priority.LOWEST, 200)

        val tasksToComplete = listOf(t1, t2, t3, t4, t5, t6)

        val planner = Planner()
        val executorService = java.util.concurrent.Executors.newSingleThreadExecutor()
        executorService.execute(planner)

        for (t in tasksToComplete) {
            planner.addTask(t)
            Thread.sleep(100L)
        }
        Thread.sleep(2000L)

        assertEquals(planner.getCompletedTasks(), listOf(t1, t3, t5, t4, t6, t2))
    }
}
