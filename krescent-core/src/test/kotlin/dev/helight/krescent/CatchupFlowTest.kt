package dev.helight.krescent

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.time.Duration.Companion.milliseconds

class CatchupFlowTest {
    @Test
    fun `Create catchup flow deduplication`() = runBlocking {
        val firstFlow = flow {
            emit(1)
            delay(80.milliseconds)
            emit(2)
            delay(80.milliseconds)
            emit(3)
            delay(80.milliseconds)
            emit(4)
        }

        val secondFlow = flow {
            delay(10.milliseconds)
            emit(3)
            delay(10.milliseconds)
            emit(4)
            delay(10.milliseconds)
            emit(5)
            delay(50.milliseconds)
            emit(6)
        }

        val result = mutableListOf<Int>()
        createCatchupFlow(firstFlow, secondFlow, comparator = Comparator.comparingInt {
            it
        }).collect { value ->
            result.add(value)
        }

        assertContentEquals(listOf(1, 2, 3, 4, 5, 6), result)
    }

}