package dev.kyle.mctools.core

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*

@OptIn(ExperimentalSerializationApi::class)
object Js {
    private val lenient = Json {
        isLenient = true
        allowComments = true
        allowTrailingComma = true
        ignoreUnknownKeys = true
    }
    private val compactJson = Json
    private val pretty = Json { prettyPrint = true; prettyPrintIndent = "  " }

    fun parseObject(text: String): JsonObject? =
        try { lenient.parseToJsonElement(text.removePrefix("\uFEFF")) as? JsonObject } catch (e: Exception) { null }

    fun parseElement(text: String): JsonElement? =
        try { lenient.parseToJsonElement(text.removePrefix("\uFEFF")) } catch (e: Exception) { null }

    fun compact(element: JsonElement): String = compactJson.encodeToString(JsonElement.serializer(), element)

    /** Same JSON with all whitespace removed (and comments dropped). Null if it can't be parsed. */
    fun minify(text: String): String? = parseElement(text)?.let { compact(it) }

    fun write(element: JsonElement): String = pretty.encodeToString(JsonElement.serializer(), element) + "\n"
}

fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray
fun JsonElement?.num(): Double? = (this as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
