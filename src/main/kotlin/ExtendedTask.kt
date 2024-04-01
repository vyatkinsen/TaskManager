import kotlinx.coroutines.delay
import ExtendedTask.ExtendedAction.*
import LogicExceptionType.*
import Task.State.*

class ExtendedTask (
    override val uuid: String,
    override val timeToProcess: Long = 1000L,
    val waitTime: Long? = null
) : Task(uuid) {
    var _isWaitCompleted = false
    val isWaitCompleted: Boolean
        get() = _isWaitCompleted

    private fun tryMakeExtendedAction(action: ExtendedAction) {
        _state = when {
            (action == WAIT) && (state == RUNNING) -> WAITING
            (action == RELEASE) && (state == WAITING) -> READY
            else -> throw LogicException(
                message = "Transition from state $state on action $action is not allowed with Extended Actions",
                type = ILLEGAL_TRANSITION,
            ).withLog(logger)
        }
    }

    suspend fun wait() {
        if (waitTime == null) throw LogicException("Cannot wait without waitTime", WAIT_IS_NOT_ALLOWED).withLog(logger)

        tryMakeExtendedAction(WAIT)
        delay(waitTime)
        _isWaitCompleted = true
        tryMakeExtendedAction(RELEASE)
    }

    enum class ExtendedAction {
        WAIT,
        RELEASE
    }
}