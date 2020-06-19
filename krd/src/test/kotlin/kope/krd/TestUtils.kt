package kope.krd

import assertk.Assert
import assertk.assertions.prop
import assertk.assertions.support.expected
import com.fasterxml.jackson.databind.JsonNode
import java.math.BigDecimal
import java.math.BigInteger

fun Assert<JsonNode>.isMissing() = transform { if (!it.isMissingNode) expected("to be missing") else it }

fun Assert<JsonNode>.isNotMissing(): Assert<JsonNode> = transform { if (it.isMissingNode) expected("to be not missing") else it }

fun Assert<JsonNode>.at(path: String): Assert<JsonNode> {
    return prop("[$path]") { it.at(path) }
}

fun Assert<JsonNode>.string(): Assert<String> {
    return prop("asString") { it.textValue() }
}

fun Assert<JsonNode>.double(): Assert<Double> {
    return prop("asDouble") { it.doubleValue() }
}

fun Assert<JsonNode>.float(): Assert<Float> {
    return prop("asFloat") { it.floatValue() }
}

fun Assert<JsonNode>.long(): Assert<Long> {
    return prop("asLong") { it.longValue() }
}

fun Assert<JsonNode>.integer(): Assert<Int> {
    return prop("asInt") { it.intValue() }
}

fun Assert<JsonNode>.boolean(): Assert<Boolean> {
    return prop("asBoolean") { it.booleanValue() }
}

fun Assert<JsonNode>.bigInteger(): Assert<BigInteger> {
    return prop("asBigInteger") { it.bigIntegerValue() }
}

fun Assert<JsonNode>.bigDecimal(): Assert<BigDecimal> {
    return prop("asBigDecimal") { it.decimalValue() }
}

fun Assert<JsonNode>.byteArray(): Assert<ByteArray> {
    return prop("asByteArray") { it.binaryValue() }
}

