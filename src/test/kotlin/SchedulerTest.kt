import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class SchedulerTest {
    @Test
    fun `run`(): Unit = runBlocking {
        val mq = MessageQueue()
        val taskProducer = TaskProducer(mq, activeCycles = 2)
        val taskProcessor = TaskProcessor()
        val scheduler = Scheduler(mq, taskProcessor)

        launch {
            scheduler.run()
        }

        launch {
//            mq.addTask(Task(generateUuid(), timeToProcess = 1000), Priority.LOWEST)
//            delay(100)
//            mq.addTask(Task(generateUuid(), timeToProcess = 1000), Priority.LOW)
//            delay(100)
            mq.addTask(ExtendedTask(generateUuid(), waitTime = 1000, timeToProcess = 1), Priority.HIGH)
            delay(100)
            mq.addTask(ExtendedTask(generateUuid(), waitTime = 1000, timeToProcess = 1), Priority.HIGH)
            mq.addTask(ExtendedTask(generateUuid(), waitTime = 1000, timeToProcess = 1), Priority.HIGH)
            delay(100)
            mq.addTask(ExtendedTask(generateUuid(), waitTime = 1000, timeToProcess = 1), Priority.HIGH)
            mq.addTask(ExtendedTask(generateUuid(), waitTime = 1000, timeToProcess = 1), Priority.HIGH)
            delay(100)
            mq.addTask(ExtendedTask(generateUuid(), waitTime = 1000, timeToProcess = 1), Priority.HIGH)
            mq.addTask(ExtendedTask(generateUuid(), waitTime = 1000, timeToProcess = 1), Priority.HIGH)
            delay(100)
            mq.addTask(ExtendedTask(generateUuid(), waitTime = 1000, timeToProcess = 1), Priority.HIGH)
            mq.addTask(ExtendedTask(generateUuid(), waitTime = 1000, timeToProcess = 1), Priority.HIGH)
            delay(100)
            mq.addTask(ExtendedTask(generateUuid(), waitTime = 1000, timeToProcess = 1), Priority.HIGH)
            mq.addTask(ExtendedTask(generateUuid(), waitTime = 1000, timeToProcess = 1), Priority.HIGH)
            delay(100)
            mq.addTask(ExtendedTask(generateUuid(), waitTime = 1000, timeToProcess = 1), Priority.HIGH)

//            taskProducer.generateTasks()
        }
    }
}