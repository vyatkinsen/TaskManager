import kotlinx.coroutines.*
import kotlin.random.Random

class TaskProducer(
    private val mq: MessageQueue,
    private val activeCycles: Int = 1,
    private val minTaskDuration: Long = 1000L,
    private val maxTaskDuration: Long = 1500L,
    private val cycleDelay: Long = 0,
) {
    private var cycleCounter = 0

    suspend fun generateTasks() = withContext(Dispatchers.Default) {
        launch {
            while (cycleCounter < activeCycles) {
                mq.addTask(generateSimpleTask(), getRandomPriority())
                mq.addTask(generateSimpleExtendedTask(), getRandomPriority())
                mq.addTask(generateWaitingExtendedTask(), getRandomPriority())
                cycleCounter++
                delay(cycleDelay)
            }
        }
    }

    private fun generateRandomDuration() =
        Random.nextLong(minTaskDuration, maxTaskDuration)

    private fun generateSimpleTask() =
        Task(generateUuid(), timeToProcess = generateRandomDuration())

    private fun generateSimpleExtendedTask() =
        ExtendedTask(generateUuid(), timeToProcess = generateRandomDuration())

    private fun generateWaitingExtendedTask() =
        ExtendedTask(generateUuid(), timeToProcess = generateRandomDuration(), waitTime = generateRandomDuration())

    private fun getRandomPriority() =
        Priority.entries.random()
}