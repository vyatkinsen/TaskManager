import org.example.Task
import java.util.concurrent.PriorityBlockingQueue
import States.WAIT

class CombinedTaskQueue : PriorityBlockingQueue<Task>(MAX_READY_TASK) {
    /**
     * Обратим внимание, что вместимость очереди игнорируется, когда задача добавлять
     * после собственного перехода из состояния ожидания. Данное условие было уточнено у лектора, поскольку
     * в процессорах с таким планировщиком такой ситуации произойти не может, что места на задачу после ожидания не хватает.
     * ------------------------------------------------------------------
     * В целом ничего не мешает резервировать место в очереди на задачу которая сейчас в ожидании,
     * но мне кажется для моделирования процесса это правильне.
     */
    override fun add(task: Task): Boolean {
        synchronized(this) {
            task.startWaitingTime = System.currentTimeMillis()
            val added = (task.currentState == WAIT || remainingCapacity() > 0) && super.add(task)
            if (added) {
                task.currentState = States.READY
            }
            return added
        }
    }

    override fun take(): Task? {
        val t: Task
        try {
            t = super.take()
        } catch (e: InterruptedException) {
            return null
        }
        return t
    }

    override fun remainingCapacity(): Int {
        return MAX_READY_TASK - this.size
    }

    companion object {
        private const val MAX_READY_TASK = 10
    }
}
