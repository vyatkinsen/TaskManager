import Priority.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals

class MessageQueueTest {
    @Test
    fun `can add to every queue`(): Unit = runBlocking {
        val mq = MessageQueue()

        mq.addTask(Task(generateUuid()), LOWEST)
        mq.addTask(Task(generateUuid()), LOW)
        mq.addTask(Task(generateUuid()), MID)
        mq.addTask(Task(generateUuid()), HIGH)
        mq.addTask(Task(generateUuid()), HIGH)

        val state = mq.queueStateFlow.first()

        assertEquals(1, state.lowestQueue.size)
        assertEquals(1, state.lowQueue.size)
        assertEquals(1, state.midQueue.size)
        assertEquals(2, state.highQueue.size)
    }

    @Test
    fun `can delete from specific queue`(): Unit = runBlocking {
        val mq = MessageQueue()
        val task = Task(generateUuid())

        mq.addTask(task, LOWEST)
        mq.terminateTask(task)
        println("Terminated")

        val state = mq.queueStateFlow.first()
        assertEquals(0, state.lowestQueue.size)
    }

    @Test
    fun `can release tasks`(): Unit = runBlocking {
        val logger = LoggerFactory.getLogger(javaClass)
        logger.atInfo().log("hello")

        val mq = MessageQueue()
        val task = Task(generateUuid())

        mq.addTask(task, LOWEST)
        mq.addTask(Task(generateUuid()), LOWEST)

        mq.onTaskRelease(task)
        var lowestQueue = mq.queueStateFlow.first().lowestQueue

        assertEquals(2, lowestQueue.size)
        assertEquals(task.uuid, lowestQueue.last().uuid)
    }
}