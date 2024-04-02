import LogicExceptionType.ILLEGAL_TRANSITION
import Task.Action.*
import Task.State.*
import org.slf4j.LoggerFactory

open class Task(
    val uuid: String,
    val timeToProcess: Long = 100
) {
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

    override fun equals(other: Any?): Boolean = (other as? Task)?.uuid == uuid

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

    override fun toString(): String = "[$commonStringAttributes]"
    override fun hashCode(): Int {
        var result = uuid.hashCode()
        result = 31 * result + _processedTime.hashCode()
        result = 31 * result + _state.hashCode()
        return result
    }

    protected val commonStringAttributes: String
        get() = "Type:${this.javaClass.name} UUID:$uuid State:$state ProcessedTime:$processedTime"


    companion object {
        private val INITIAL_STATE = SUSPENDED
    }
}
