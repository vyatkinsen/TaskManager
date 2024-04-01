import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class Scheduler(private val mq: MQ) {
    private val _currentTask: Task? = null
    val currentTask: Task?
        get()= _currentTask

    var nextTask: Task? = null
    val lock = Any()

    private val _processingAllowedFlow = MutableStateFlow(true)
    val processingAllowedFlow = _processingAllowedFlow.asStateFlow()



    suspend fun run(): Unit {
        launchQueueCollectors()
    }

    private suspend fun launchQueueCollectors() {
        coroutineScope {
//            launch {
//                .collectLatest {
//
//                    }
//            }
//
//
//            launch {
//                mq.lowestQueue.collect {
//
//                }
//            }
//
//            launch {
//                mq.lowQueue.collect {
//
//                }
//            }
//
//            launch {
//                mq.midQueue.collect {
//
//                }
//            }
//
//            launch {
//                mq.highQueue.collect {
//
//                }
//            }
        }
    }

    enum class Event {
        TASK_COMPLETED_SUCCESSFULLY
    }
}