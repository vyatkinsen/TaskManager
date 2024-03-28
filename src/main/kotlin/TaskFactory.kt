import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.example.Task
import org.slf4j.LoggerFactory
import java.util.concurrent.*

class TaskFactory {
    private var isRunning = false
    private var currentTask: Task? = null
    private val logger = LoggerFactory.getLogger(javaClass.name)

    private var deferred: Deferred<Task>? = null

    /**
     * Реализует все очереди вместе, поскольку сам сортирует элементы по приоритету при добавлении
     * и является потокобезопасным
     */
    private val queue = CombinedTaskQueue()
    private val completedTasks: MutableList<Task> = ArrayList()


    suspend fun run() {
        coroutineScope {
            if (!isRunning) {
                // запуск процесса на просматривание готовых задач
                isRunning = true
                while (isRunning) {
                    val task = queue.take() ?: throw RuntimeException("no task")
                    currentTask = task

                    logger.atInfo().log("$task WANT TO EXECUTE")

                    deferred = async { task.call() }
                    try {
                        val endedTask = deferred!!.await()

                        if (endedTask.currentState == States.WAIT) {
                            launch { task.waitSomething() }.invokeOnCompletion {
                                this@TaskFactory.addTask(task)
                            }
                        } else {
                            completedTasks.add(endedTask)
                        }
                    } catch (c: CancellationException) {
                        queue.add(task) // случай прерывания текущей задачи до завершения
                    }
                }
            }
        }
    }

    fun addTask(task: Task): Boolean {
        synchronized(this) {
            val wereAdded = queue.add(task)
            if (wereAdded) {
                logger.atInfo().log("PLANNER ADD " + task + " REMAINING CAPACITY - " + queue.remainingCapacity())
                /**
                 * Проверяем что задача добавленная в очередь на исполнение является более приоритетной
                 * и то, что в очереди хватит места на задачу, которая вернется в очередь на исполнение после
                 * прерывания
                 */
                /**
                 * Проверяем что задача добавленная в очередь на исполнение является более приоритетной
                 * и то, что в очереди хватит места на задачу, которая вернется в очередь на исполнение после
                 * прерывания
                 */
                if (currentTask != null && task.comparePriority(currentTask!!) < 0 && queue.remainingCapacity() >= 1) {
                    logger.atInfo().log("WANT INTERRUPT $currentTask TO EXECUTE $task")
                    deferred!!.cancel()
                }
            } else {
                logger.atInfo().log("PLANNER CANT ADD $task NOT ENOUGH SPACE")
            }
            return wereAdded
        }
    }

    fun stop() {
        this.isRunning = false
    }

    fun getCompletedTasks(): List<Task> {
        return completedTasks
    }
}
