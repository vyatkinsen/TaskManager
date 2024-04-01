import org.slf4j.LoggerFactory
import LogicExceptionType.ILLEGAL_TRANSITION
import Task.Action.*
import Task.State.*

open class Task(open val uuid: String, open val timeToProcess: Long = 1000) {
    protected val logger = LoggerFactory.getLogger(javaClass)

    private var _processedTime: Long = 0
    val processedTime: Long
        get() = _processedTime

    protected var _state = INITIAL_STATE
    val state: State
        get() = _state

    fun tryMakeAction(action: Action) {
        _state = getNextState(action)
    }

    protected fun getNextState(action: Action) =
        when {
            (action == ACTIVATE) && (state == SUSPENDED) -> READY
            (action == START) && (state == READY) -> RUNNING
            (action == PREEMPT) && (state == RUNNING) -> READY
            (action == TERMINATE) && (state == RUNNING) -> SUSPENDED
            else -> throw LogicException(
                message = "Transition from state $state on action $action is not allowed",
                type = ILLEGAL_TRANSITION,
            ).withLog(logger)
        }

    fun commitProcessTime(time: Long) {
        _processedTime += time
    }

    enum class Action {
        START,
        TERMINATE,
        PREEMPT,
        ACTIVATE,
    }

    enum class State {
        RUNNING,
        READY,
        SUSPENDED,
        WAITING,
    }

    companion object {
        private val INITIAL_STATE = SUSPENDED
    }
}
