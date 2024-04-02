import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.slf4j.LoggerFactory

fun main() {
    val logger = LoggerFactory.getLogger("HHHHHH")
    logger.atInfo().log("hello")

    runBlocking {
        val _stateFlow = MutableStateFlow(
            MessageQueue.QueuePool(
                ArrayDeque(),
                ArrayDeque(),
                ArrayDeque(),
                ArrayDeque(),
            ))
        val stateFlow = _stateFlow.asStateFlow()
        launch {
            repeat(10) {
                val task = Task("1")
                val oldState = stateFlow.value
                val newState = stateFlow.value.copy(
                    lowestQueue = ArrayDeque<Task>().apply { add(task) }
                )
                println(oldState == newState)

                _stateFlow.value = newState
                delay(500)
            }
        }
        launch {
            stateFlow.collect {
                println(it)
            }
        }
    }
}

