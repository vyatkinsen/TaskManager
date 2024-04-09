import ExtendedTask.ExtendedAction.RELEASE
import ExtendedTask.ExtendedAction.WAIT
import LogicExceptionType.ILLEGAL_TRANSITION
import LogicExceptionType.WAIT_IS_NOT_ALLOWED
import Task.State.*
import kotlinx.coroutines.delay

class ExtendedTask(
    uuid: String,
    timeToProcess: Long = 100,
    val waitTime: Long? = null
) : Task(uuid, timeToProcess) {
    private var _isWaitCompleted = false
    val isWaitCompleted: Boolean
        get() = _isWaitCompleted

    fun tryMakeExtendedAction(action: ExtendedAction) {
        _state = when {
            (action == WAIT) && (state == RUNNING) -> WAITING
            (action == RELEASE) && (state == WAITING) -> READY
            else -> throw LogicException(
                message = "Transition from state $state on action $action is not allowed UUID:$uuid",
                type = ILLEGAL_TRANSITION,
            ).withLog(logger)
        }
    }

    suspend fun wait(beforeDelay: suspend (task: Task) -> Unit = {}) {
        if (waitTime == null) throw LogicException("Cannot wait without waitTime", WAIT_IS_NOT_ALLOWED).withLog(logger)
        beforeDelay(this)
        logger.atInfo().log("Task:$this is waiting $waitTime ms")
        delay(waitTime)
        _isWaitCompleted = true
        tryMakeExtendedAction(RELEASE)
    }

    override fun toString(): String =
        "[$commonStringAttributes IsWaitCompleted:$isWaitCompleted WaitTime:$waitTime]"

    enum class ExtendedAction {
        WAIT,
        RELEASE
    }
}