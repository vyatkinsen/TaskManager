import org.example.Task
import java.util.stream.Collectors
import java.util.stream.IntStream
import kotlin.random.Random

object TaskGenerator {

    fun createRandomTask(): Task {
        if (Random.nextInt(4) <= 1) {
            return Task.of(Priority.entries.random(), Random.nextLong(500L, 2000))
        }
        return ExtendedTask.of(Priority.entries.random(), Random.nextLong(500, 2000))
    }

    fun createTasks(count: Int): List<Task> {
        return IntStream.range(0, count).mapToObj { i: Int -> createRandomTask() }
            .collect(Collectors.toList())
    }

    private fun <T : Enum<*>?> randomEnum(clazz: Class<T>): T {
        val x = Random.nextInt(clazz.enumConstants.size)
        return clazz.enumConstants[x]
    }
}
