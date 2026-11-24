package com.example.data.cloud

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

object Sha256Util {
    fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun jsonObjectToMap(obj: JSONObject): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = obj.get(key)
            result[key] = convertJsonValue(value)
        }
        return result
    }

    fun jsonArrayToList(arr: JSONArray): List<Any?> {
        val list = ArrayList<Any?>(arr.length())
        for (i in 0 until arr.length()) {
            list.add(convertJsonValue(arr.get(i)))
        }
        return list
    }

    fun mapToJsonObject(map: Map<String, Any?>): JSONObject {
        val obj = JSONObject()
        map.forEach { (k, v) ->
            obj.put(k, convertAnyToJson(v))
        }
        return obj
    }

    private fun convertJsonValue(value: Any?): Any? = when (value) {
        null, JSONObject.NULL -> null
        is JSONObject -> jsonObjectToMap(value)
        is JSONArray -> jsonArrayToList(value)
        is Number, is Boolean, is String -> value
        else -> value.toString()
    }

    private fun convertAnyToJson(value: Any?): Any? = when (value) {
        null -> JSONObject.NULL
        is Map<*, *> -> {
            val obj = JSONObject()
            value.forEach { (k, v) -> if (k != null) obj.put(k.toString(), convertAnyToJson(v)) }
            obj
        }
        is Collection<*> -> {
            val arr = JSONArray()
            value.forEach { arr.put(convertAnyToJson(it)) }
            arr
        }
        else -> value
    }
}
