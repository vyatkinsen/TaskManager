import Task.State.*
import io.mockk.spyk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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

        launch {
            val task1 = Task(generateUuid(), timeToProcess = 1)
            val task2 = Task(generateUuid(), timeToProcess = 1)
            mq.addTask(task1, Priority.LOWEST)
            mq.addTask(task2, Priority.HIGH)

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

        val stateList = mutableListOf(SUSPENDED, READY).iterator()
        val job2 = launch {
            val task = ExtendedTask(generateUuid(), timeToProcess = 1, waitTime = 10)
            mq.addTask(task, Priority.LOWEST)
            mq.queueStateFlow.collect { queuePool ->
                queuePool.lowestQueue.firstOrNull()?.state?.let {
                    assertEquals(stateList.next(), it)
                }
            }
        }
        launch {
            while (!(mq.completedTasks.size == 1 && mq.queueStateFlow.first().lowestQueue.size == 0)) {
                delay(1)
            }
            assertEquals(null, mq.queueStateFlow.first().lowestQueue.firstOrNull())
            job1.cancel()
            job2.cancel()
        }
    }

    @Test
    fun `completes random tasks with random priorities`(): Unit = runBlocking {
        val mq = MessageQueue()
        val taskProcessor = TaskProcessor()
        val producer = TaskProducer(
            mq,
            activeCycles = 20,
            minTaskDuration = 1,
            maxTaskDuration = 10,
            cycleDelay = 10
        )
        val scheduler = Scheduler(mq, taskProcessor)

        val job1 = launch {
            scheduler.run()
        }
        val job2 = launch {
            producer.generateTasks()
        }

        while (mq.completedTasks.size != 60) {
            delay(1)
        }
        job1.cancel()
        job2.cancel()
    }
}