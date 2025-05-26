@file:OptIn(ExperimentalSerializationApi::class)

package dev.helight.krescent

import dev.helight.krescent.checkpoint.BucketValue
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals

class BucketValueTest {

    @Test
    fun testString() {
        val value = BucketValue.StringValue("Hello, World!")
        val jsonEncoded = Json.encodeToString<BucketValue>(value)
        val cborEncoded = Cbor.encodeToByteArray<BucketValue>(value)
        assertEquals(value, Json.decodeFromString<BucketValue>(jsonEncoded))
        assertEquals(value, Cbor.decodeFromByteArray<BucketValue>(cborEncoded))
    }

    @Test
    fun testInt() {
        val value = BucketValue.IntValue(42)
        val jsonEncoded = Json.encodeToString<BucketValue>(value)
        val cborEncoded = Cbor.encodeToByteArray<BucketValue>(value)
        assertEquals(value, Json.decodeFromString<BucketValue>(jsonEncoded))
        assertEquals(value, Cbor.decodeFromByteArray<BucketValue>(cborEncoded))
    }

    @Test
    fun testBoolean() {
        val value = BucketValue.BooleanValue(true)
        val jsonEncoded = Json.encodeToString<BucketValue>(value)
        val cborEncoded = Cbor.encodeToByteArray<BucketValue>(value)
        assertEquals(value, Json.decodeFromString<BucketValue>(jsonEncoded))
        assertEquals(value, Cbor.decodeFromByteArray<BucketValue>(cborEncoded))
    }

    @Test
    fun testJsonObject() {
        val value = BucketValue.JsonValue(buildJsonObject {
            put("key1", "value1")
            put("key2", 42)
            put("key3", true)
            put("key4", buildJsonObject {
                put("nestedKey", "nestedValue")
            })
            putJsonArray("key5") {
                add("item1")
                add(123)
                add(true)
                add(buildJsonObject {
                    put("nestedArrayKey", "nestedArrayValue")
                })
            }
        })
        val jsonEncoded = Json.encodeToString<BucketValue>(value)
        val cborEncoded = Cbor.encodeToByteArray<BucketValue>(value)
        assertEquals(value, Json.decodeFromString<BucketValue>(jsonEncoded))
        assertEquals(value, Cbor.decodeFromByteArray<BucketValue>(cborEncoded))
    }

    @Test
    fun testByteArray() {
        val value = BucketValue.ByteArrayValue(byteArrayOf(1, 2, 3, 4, 5))
        val jsonEncoded = Json.encodeToString<BucketValue>(value)
        val cborEncoded = Cbor.encodeToByteArray<BucketValue>(value)
        assertEquals(value, Json.decodeFromString<BucketValue>(jsonEncoded))
        assertEquals(value, Cbor.decodeFromByteArray<BucketValue>(cborEncoded))
    }
}