import Priority.HIGH
import Priority.LOWEST
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals


class SchedulerTest {
    @Test
    fun `scheduler can interrupt processing on higher priority task added`(): Unit = runBlocking {
        val mq = MessageQueue()
        val taskProcessor = TaskProcessor()
        val scheduler = Scheduler(mq, taskProcessor)

        val job1 = launch {
            scheduler.run()
        }

        val task1 = Task(generateUuid(), timeToProcess = 1)
        val task2 = Task(generateUuid(), timeToProcess = 1)
        mq.addTask(task1, LOWEST)
        mq.addTask(task2, HIGH)

        while (mq.completedTasks.size != 2) {
            delay(1)
        }

        assertEquals(task2, mq.completedTasks[0])
        assertEquals(task1, mq.completedTasks[1])

        job1.cancel()
    }

    @Test
    fun `getting lower priority task does not interrupt current`(): Unit = runBlocking {
        val mq = MessageQueue()
        val taskProcessor = TaskProcessor()
        val scheduler = Scheduler(mq, taskProcessor)

        val job1 = launch {
            scheduler.run()
        }

        launch {
            val task1 = Task(generateUuid(), timeToProcess = 1000)
            val task2 = Task(generateUuid(), timeToProcess = 1000)
            mq.addTask(task2, HIGH)
            mq.addTask(task1, LOWEST)

            while (mq.completedTasks.size != 2) {
                delay(1)
            }

            assertEquals(mq.completedTasks[0], task2)
            assertEquals(mq.completedTasks[1], task1)
            job1.cancel()
        }
    }

    @Test
    fun `scheduler can process one waiting task`(): Unit = runBlocking {
        val mq = MessageQueue()
        val taskProcessor = TaskProcessor()
        val scheduler = Scheduler(mq, taskProcessor)

        val job1 = launch {
            scheduler.run()
        }
        val waitTime = 100L
        val task = ExtendedTask(generateUuid(), timeToProcess = 1, waitTime = waitTime)
        mq.addTask(task, LOWEST)

        while (mq.completedTasks.size != 1) {
            delay(10)
        }

        assertEquals(task, mq.completedTasks[0])

        job1.cancel()
    }

    @Test
    fun `completes random tasks with random priorities`(): Unit = runBlocking {
        val mq = MessageQueue()
        val taskProcessor = TaskProcessor()
        val cycles = 1000
        val producer = TaskProducer(
            mq,
            activeCycles = cycles,
            minTaskDuration = 1,
            maxTaskDuration = 10,
            cycleDelay = 10
        )
        val scheduler = Scheduler(mq, taskProcessor)

        val job1 = launch(Dispatchers.Default) {
            scheduler.run()
        }
        val job2 = launch {
            producer.generateTasks()
        }

        while (mq.completedTasks.size != cycles * 3) {
            delay(1)
        }
        job1.cancel()
        job2.cancel()
    }
}