import TaskGenerator.createRandomTask
import java.util.concurrent.Executors

object Main {
    fun main(args: Array<String>) {
        val planner = Planner()
        val executorService = Executors.newSingleThreadExecutor()
        executorService.execute(planner)

        while (true) {
            planner.addTask(createRandomTask())
            Thread.sleep(1100L)
        }
    }
}
