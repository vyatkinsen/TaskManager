import Priority.*
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals

class MQTest {
    @Test
    fun `can add to every queue`() {
        val mq = MQ()

        mq.addTask(Task(generateUuid()), LOWEST)
        mq.addTask(Task(generateUuid()), LOW)
        mq.addTask(Task(generateUuid()), MID)
        mq.addTask(Task(generateUuid()), HIGH)
        mq.addTask(Task(generateUuid()), HIGH)

        val state = mq.queueStateFlow.value

        assertEquals(1, state.lowestQueue.size)
        assertEquals(1, state.lowQueue.size)
        assertEquals(1, state.midQueue.size)
        assertEquals(2, state.highQueue.size)
    }

    @Test
    fun `can delete from specific queue`() {
        val mq = MQ()
        val task = Task(generateUuid())

        mq.addTask(task, LOWEST)
        mq.terminateTask(task, LOWEST)

        val state = mq.queueStateFlow.value
        assertEquals(0, state.lowestQueue.size)
    }

    @Test
    fun `can release tasks`() {
        val logger = LoggerFactory.getLogger(javaClass)
        logger.atInfo().log("hello")

        val mq = MQ()
        val task = Task(generateUuid())

        mq.addTask(task, LOWEST)
        mq.addTask(Task(generateUuid()), LOWEST)

        mq.onTaskRelease(task, LOWEST)
        var lowestQueue = mq.queueStateFlow.value.lowestQueue

        assertEquals(2, lowestQueue.size)
        assertEquals(task.uuid, lowestQueue.last().uuid)
    }
}