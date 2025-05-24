package dev.helight.krescent

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Test class for the CoroutineUtilsKt class focusing on the 'joinSequentialFlows' function.
 *
 * The 'joinSequentialFlows' function sequentially joins two flows, collecting all values
 * from the first flow before switching to live collection from the second flow.
 * Buffers are used to temporarily store data, and a callback could also be optionally triggered.
 */
class JoinSequentialFlowTest {

    @Test
    fun `test joinSequentialFlows with simple distinct flows`() = runBlocking {
        val firstFlow = flow {
            emit("A")
            emit("B")
            emit("C")
        }

        val secondFlow = flow {
            emit("1")
            emit("2")
            emit("3")
        }

        val result = mutableListOf<String>()

        joinSequentialFlows(firstFlow, secondFlow).collect { value ->
            result.add(value)
        }

        assertEquals(listOf("A", "B", "C", "1", "2", "3"), result)
    }

    @Test
    fun `test joinSequentialFlows with delay between emissions`() = runBlocking {
        val firstFlow = flow {
            emit("X")
            delay(100)
            emit("Y")
            delay(100)
            emit("Z")
        }

        val secondFlow = flow {
            delay(50)
            emit("7")
            delay(50)
            emit("8")
            delay(50)
            emit("9")
        }

        val result = mutableListOf<String>()

        joinSequentialFlows(firstFlow, secondFlow).collect { value ->
            result.add(value)
        }

        assertEquals(listOf("X", "Y", "Z", "7", "8", "9"), result)
    }

    @Test
    fun `test joinSequentialFlows with empty first flow`() = runBlocking {
        val firstFlow = flow<String> { /* No emissions */ }

        val secondFlow = flow {
            emit("First")
            emit("Second")
            emit("Third")
        }

        val result = mutableListOf<String>()

        joinSequentialFlows(firstFlow, secondFlow).collect { value ->
            result.add(value)
        }

        assertEquals(listOf("First", "Second", "Third"), result)
    }

    @Test
    fun `test joinSequentialFlows with empty second flow`() = runBlocking {
        val firstFlow = flow {
            emit("Only")
            emit("First")
            emit("Flow")
        }

        val secondFlow = flow<String> { /* No emissions */ }

        val result = mutableListOf<String>()

        joinSequentialFlows(firstFlow, secondFlow).collect { value ->
            result.add(value)
        }

        assertEquals(listOf("Only", "First", "Flow"), result)
    }

    @Test
    fun `test joinSequentialFlows with activation callback`() = runBlocking {
        val firstFlow = flow {
            emit("1A")
            emit("1B")
        }

        val secondFlow = flow {
            emit("2A")
            emit("2B")
        }

        var callbackInvoked = false

        val result = mutableListOf<String>()

        joinSequentialFlows(firstFlow, secondFlow, activateCallback = {
            callbackInvoked = true
        }).collect { value ->
            result.add(value)
        }

        assertEquals(listOf("1A", "1B", "2A", "2B"), result)
        assertEquals(true, callbackInvoked)
    }

    @Test
    fun `test joinSequentialFlows with second flow emitting after delay`() = runBlocking {
        val firstFlow = flow {
            emit("Alpha")
            delay(200)
            emit("Beta")
        }

        val secondFlow = flow {
            delay(300)
            emit("1")
            delay(100)
            emit("2")
        }

        val result = mutableListOf<String>()

        joinSequentialFlows(firstFlow, secondFlow).collect { value ->
            result.add(value)
        }

        assertEquals(listOf("Alpha", "Beta", "1", "2"), result)
    }

    @Test
    fun `test joinSequentialFlows with overlapping emissions`() = runBlocking {
        val firstFlow = flow {
            emit("Start-1A")
            delay(80)
            emit("Start-1B")
        }

        val secondFlow = flow {
            delay(50)
            emit("Live-2A")
            delay(100)
            emit("Live-2B")
        }

        val result = mutableListOf<String>()

        joinSequentialFlows(firstFlow, secondFlow).collect { value ->
            result.add(value)
        }

        assertEquals(listOf("Start-1A", "Start-1B", "Live-2A", "Live-2B"), result)
    }
}