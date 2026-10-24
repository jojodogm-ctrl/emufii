package com.mpds.online

/** Small protocol JSON encoder; no reflection or arbitrary object serialization. */
internal object RoomJson {
    fun encode(value: Any?): String = when (value) {
        is Map<*, *> -> {
            require(value.keys.all { it is String })
            value.entries.joinToString(",", "{", "}") { RoomInvitation.json(it.key) + ":" + encode(it.value) }
        }
        is List<*> -> value.joinToString(",", "[", "]") { encode(it) }
        null, is String, is Boolean, is Int, is Long -> RoomInvitation.json(value)
        is Double -> {
            require(value.isFinite() && kotlin.math.abs(value) <= 9007199254740991.0)
            java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
        }
        else -> error("unsupported-json-value")
    }
}
