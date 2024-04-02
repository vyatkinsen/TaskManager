import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class SchedulerTest {
    @Test
    fun `run`(): Unit = runBlocking {
        val mq = MessageQueue()
        val taskProducer = TaskProducer(mq)
        val taskProcessor = TaskProcessor()
        val scheduler = Scheduler(mq, taskProcessor)

        launch {
            scheduler.run()
        }

        launch {
            mq.addTask(Task(generateUuid()), Priority.LOWEST)
            mq.addTask(Task(generateUuid()), Priority.LOW)
            mq.addTask(ExtendedTask(generateUuid(), waitTime = 100), Priority.HIGH)
        }
    }
}