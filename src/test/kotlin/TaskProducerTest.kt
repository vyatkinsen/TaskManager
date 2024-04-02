import MessageQueue.QueuePool
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.lang.System.currentTimeMillis
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TaskProducerTest {
    @Test
    fun `run tasks producer for 3 cycles`(): Unit = runBlocking {
        //what
        val cycleCount = 15
        val mq = MessageQueue()
        val taskProducer = TaskProducer(mq, activeCycles = cycleCount)

        //when
        val job = taskProducer.generateTasks()
        job.join()

        //then
        val state = mq.queueStateFlow.value
        val (simpleTasksCount, extendedTaskWithoutWaitCount, extendedTaskWithWaitCount) = getQueuePoolStats(state)
        assertEquals(cycleCount, simpleTasksCount)
        assertEquals(cycleCount, extendedTaskWithoutWaitCount)
        assertEquals(cycleCount, extendedTaskWithWaitCount)
    }

    @Test
    fun `runs with delay after each cycle`(): Unit = runBlocking {
        //what
        val mq = MessageQueue()
        val cycleCount = 3
        val cycleDelay = 100L
        val taskProducer = TaskProducer(mq, activeCycles = cycleCount, cycleDelay = cycleDelay)
        val initialTime = currentTimeMillis()

        //when
        val job = taskProducer.generateTasks()
        job.join()

        //then
        val endTime = currentTimeMillis()
        assertTrue(endTime - initialTime > cycleCount * cycleDelay)
    }

    private fun getQueuePoolStats(queuePool: QueuePool): Triple<Int, Int, Int> {
        var simpleTasksCount = 0
        var extendedTaskWithoutWaitCount = 0
        var extendedTaskWithWaitCount = 0

        (queuePool.lowestQueue + queuePool.lowQueue + queuePool.midQueue + queuePool.highQueue).forEach {
            if (it is ExtendedTask) {
                if (it.waitTime != null) {
                    extendedTaskWithWaitCount++
                } else {
                    extendedTaskWithoutWaitCount++
                }
            } else {
                simpleTasksCount++
            }
        }
        return Triple(simpleTasksCount, extendedTaskWithoutWaitCount, extendedTaskWithWaitCount)
    }


}