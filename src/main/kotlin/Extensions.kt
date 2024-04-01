import org.slf4j.Logger


fun Throwable.withLog(logger: Logger): Throwable {
    logger.atError().log(this.message)
    return this
}

private var uuidCounter = 0L
fun generateUuid() = (uuidCounter++).toString()
