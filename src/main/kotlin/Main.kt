import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

fun main() {
    val globalScope = CoroutineScope(Dispatchers.IO)
    var job: Job? = null

    runBlocking {
        launch {
            var deferred = MutableStateFlow<Deferred<Int>?>(null)
            var state = 0
            val currentJob = globalScope.launch {
                delay(1000)
                println("HI")
            }
            currentJob.invokeOnCompletion {
                println("Completed")
            }
            job = currentJob
            println("ME first")
        }
        launch {
            delay(500)
            job!!.cancel()
        }


//        withContext(this.coroutineContext) {
//            deferred.value = async {
//                repeat(1000) {
//                    state++
//                    delay(1)
//                }
//
//                return@async 2
//            }.apply {
//                invokeOnCompletion {
//                    println("Im Cancelled, $it, state=$state")
//                }
//            }
//        }
//        launch {
//            delay(100)
//            deferred.value?.cancel()
//        }
    }
}