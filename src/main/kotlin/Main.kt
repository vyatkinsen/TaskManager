import TaskGenerator.createRandomTask
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors

fun main(): Unit = runBlocking {
    val taskFactory = TaskFactory()
    launch {
        while (true) {
            taskFactory.addTask(createRandomTask())
            delay(1100L)
        }
    }
    launch { taskFactory.run() }
}

