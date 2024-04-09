enum class LogicExceptionType {
    ILLEGAL_TRANSITION,
    TASK_CANNOT_BE_PROCESSED,
    TASK_NOT_FOUND,
    TASK_NOT_VALID,
    WAIT_IS_NOT_ALLOWED
}

class LogicException(override val message: String, type: LogicExceptionType) : Exception()