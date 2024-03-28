import org.example.Task
import org.slf4j.LoggerFactory
import java.util.concurrent.*

class Planner : Runnable {
    private var isRunning = false
    private var currentTask: Task? = null
    private val logger = LoggerFactory.getLogger(javaClass.name)

    private var future: Future<Task>? = null

    private val mainTaskExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    /**
     * Реализует все очереди вместе, поскольку сам сортирует элементы по приоритету при добавлении
     * и является потокобезопасным
     */
    private val queue = CombinedTaskQueue()
    private val completedTasks: MutableList<Task> = ArrayList()


    override fun run() {
        if (!isRunning) {
            // запуск процесса на просматривание готовых задач
            isRunning = true
            while (isRunning) {
                currentTask = queue.take()
                logger.atInfo().log(currentTask.toString() + " WANT TO EXECUTE")
                future = mainTaskExecutor.submit(currentTask!!)
                try {
                    val endedTask = (future as Future<Task>).get()

                    if (endedTask.currentState == States.WAIT) {
                        CompletableFuture.supplyAsync { currentTask!!.waitSomething() }
                            .thenAccept { task: Task -> this.addTask(task) }
                    } else {
                        completedTasks.add(endedTask)
                    }
                } catch (E: InterruptedException) {
                } catch (E: ExecutionException) {
                } catch (c: CancellationException) {
                    queue.add(currentTask!!) // случай прерывания текущей задачи до завершения
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
                    future!!.cancel(true)
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
