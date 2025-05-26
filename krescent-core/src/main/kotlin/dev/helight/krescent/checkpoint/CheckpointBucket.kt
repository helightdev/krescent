@file:OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
@file:Suppress("unused")

package dev.helight.krescent.checkpoint

import kotlinx.serialization.*
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalSerializationApi::class)
fun main() {
    val value: BucketValue = BucketValue.JsonValue(buildJsonObject {
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
    println(Cbor.encodeToHexString(value))
    println(Json.encodeToString(value))
}

@Serializable
class CheckpointBucket(
    val buffer: MutableMap<String, BucketValue> = mutableMapOf(),
) {

    fun clone(): CheckpointBucket {
        return CheckpointBucket(buffer.toMutableMap())
    }

    operator fun set(key: String, value: JsonElement) = put(key, value)
    operator fun set(key: String, value: BucketValue) = buffer.put(key, value)
    operator fun get(key: String): JsonElement? = getJsonElement(key)

    fun getValue(key: String): BucketValue? = buffer[key]

    fun put(key: String, value: String) {
        buffer[key] = BucketValue.StringValue(value)
    }

    fun put(key: String, value: Int) {
        buffer[key] = BucketValue.IntValue(value)
    }

    fun put(key: String, value: Boolean) {
        buffer[key] = BucketValue.BooleanValue(value)
    }

    fun put(key: String, value: ByteArray) {
        buffer[key] = BucketValue.ByteArrayValue(value)
    }

    fun put(key: String, value: JsonElement) {
        buffer[key] = BucketValue.JsonValue(value)
    }

    fun getString(key: String): String? = buffer[key]?.let {
        it as? BucketValue.StringValue ?: error("Element with key '$key' is not a StringValue, found: $it")
    }?.value

    fun getInt(key: String): Int? = buffer[key]?.let {
        it as? BucketValue.IntValue ?: error("Element with key '$key' is not an IntValue, found: $it")
    }?.value

    fun getBoolean(key: String): Boolean? = buffer[key]?.let {
        it as? BucketValue.BooleanValue ?: error("Element with key '$key' is not a BooleanValue, found: $it")
    }?.value

    fun getByteArray(key: String): ByteArray? = buffer[key]?.let {
        it as? BucketValue.ByteArrayValue ?: error("Element with key '$key' is not a ByteArrayValue, found: $it")
    }?.value

    fun getJsonElement(key: String): JsonElement? = buffer[key]?.let {
        it as? BucketValue.JsonValue ?: error("Element with key '$key' is not a JsonValue, found: $it")
    }?.value

    fun encodeToJsonObject(): JsonElement = Json.encodeToJsonElement(this)
    fun encodeToJsonString(): String = Json.encodeToString(this)
    fun encodeToByteArray(): ByteArray = Cbor.encodeToByteArray(this)

    override fun toString(): String = "CheckpointBucket{buffer=$buffer}"

    companion object {
        fun fromJsonObject(json: JsonObject): CheckpointBucket = Json.decodeFromJsonElement<CheckpointBucket>(json)
        fun fromJsonString(json: String): CheckpointBucket = Json.decodeFromString<CheckpointBucket>(json)
        fun fromByteArray(data: ByteArray): CheckpointBucket = Cbor.decodeFromByteArray<CheckpointBucket>(data)
    }

}

@Serializable(with = BucketValueSerializer::class)
sealed class BucketValue(
    val type: Byte,
) {
    class StringValue(
        val value: String,
    ) : BucketValue(0) {
        override fun toString(): String = "StringValue(value='$value')"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as StringValue
            return value == other.value
        }

        override fun hashCode(): Int {
            return value.hashCode()
        }
    }

    class IntValue(
        val value: Int,
    ) : BucketValue(1) {
        override fun toString(): String = "IntValue(value=$value)"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as IntValue
            return value == other.value
        }

        override fun hashCode(): Int {
            return value.hashCode()
        }
    }

    class BooleanValue(
        val value: Boolean,
    ) : BucketValue(2) {
        override fun toString(): String = "BoolValue(value=$value)"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as BooleanValue
            return value == other.value
        }

        override fun hashCode(): Int {
            return value.hashCode()
        }
    }

    class ByteArrayValue(
        val value: ByteArray,
    ) : BucketValue(3) {
        override fun toString(): String = "ByteArrayValue(value=${value.contentToString()})"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as ByteArrayValue
            return value.contentEquals(other.value)
        }

        override fun hashCode(): Int {
            return value.contentHashCode()
        }
    }

    class JsonValue(
        val value: JsonElement,
    ) : BucketValue(4) {
        override fun toString(): String = "JsonValue(value=$value)"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as JsonValue
            return Json.encodeToString(value) == Json.encodeToString(other.value)
        }

        override fun hashCode(): Int {
            return Json.encodeToString(value).hashCode()
        }
    }
}

object GenericJsonElementConverter : KSerializer<JsonElement> {
    override val descriptor = JsonElement.serializer().descriptor

    override fun serialize(encoder: Encoder, value: JsonElement) = when (encoder) {
        is JsonEncoder -> encoder.encodeSerializableValue(JsonElement.serializer(), value)
        else -> encoder.encodeString(Json.Default.encodeToString(value))
    }

    override fun deserialize(decoder: Decoder): JsonElement {
        return when (decoder) {
            is JsonDecoder -> decoder.decodeSerializableValue(JsonElement.serializer())
            else -> Json.Default.decodeFromString(JsonElement.serializer(), decoder.decodeString())
        }
    }
}


@OptIn(ExperimentalEncodingApi::class)
object GenericByteArraySerializer : KSerializer<ByteArray> {
    override val descriptor = buildClassSerialDescriptor("GenericByteArray")

    override fun serialize(encoder: Encoder, value: ByteArray) = when (encoder) {
        is JsonEncoder -> encoder.encodeString(Base64.encode(value))
        else -> encoder.encodeSerializableValue(ByteArraySerializer(), value)
    }

    override fun deserialize(decoder: Decoder): ByteArray {
        return when (decoder) {
            is JsonDecoder -> decoder.decodeString().let { Base64.decode(it) }
            else -> decoder.decodeSerializableValue(ByteArraySerializer())
        }
    }
}

object BucketValueSerializer : KSerializer<BucketValue> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("BucketValue") {
        element<Int>("t")
        element("v", buildClassSerialDescriptor("v"))
    }

    override fun serialize(encoder: Encoder, value: BucketValue) {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeByteElement(descriptor, 0, value.type)
        when (value) {
            is BucketValue.StringValue -> composite.encodeSerializableElement(
                descriptor,
                1,
                String.serializer(),
                value.value
            )

            is BucketValue.IntValue -> composite.encodeSerializableElement(descriptor, 1, Int.serializer(), value.value)
            is BucketValue.BooleanValue -> composite.encodeSerializableElement(
                descriptor,
                1,
                Boolean.serializer(),
                value.value
            )

            is BucketValue.ByteArrayValue -> composite.encodeSerializableElement(
                descriptor,
                1,
                GenericByteArraySerializer,
                value.value
            )

            is BucketValue.JsonValue -> composite.encodeSerializableElement(
                descriptor,
                1,
                GenericJsonElementConverter,
                value.value
            )
        }
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): BucketValue {
        val dec = decoder.beginStructure(descriptor)
        var type: Int? = null
        var value: Any? = null

        loop@ while (true) {
            when (val index = dec.decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> break@loop
                0 -> type = dec.decodeByteElement(descriptor, index).toInt()
                1 -> value = when (type) {
                    0 -> dec.decodeSerializableElement(descriptor, index, String.serializer())
                    1 -> dec.decodeSerializableElement(descriptor, index, Int.serializer())
                    2 -> dec.decodeSerializableElement(descriptor, index, Boolean.serializer())
                    3 -> dec.decodeSerializableElement(descriptor, index, GenericByteArraySerializer)
                    4 -> dec.decodeSerializableElement(descriptor, index, GenericJsonElementConverter)
                    else -> throw SerializationException("Unknown type id: $type")
                }

                else -> throw SerializationException("Unexpected index $index")
            }
        }

        dec.endStructure(descriptor)

        return when (type) {
            0 -> BucketValue.StringValue(value as String)
            1 -> BucketValue.IntValue(value as Int)
            2 -> BucketValue.BooleanValue(value as Boolean)
            3 -> BucketValue.ByteArrayValue(value as ByteArray)
            4 -> BucketValue.JsonValue(value as JsonElement)
            else -> throw SerializationException("Unsupported type $type")
        }
    }
}