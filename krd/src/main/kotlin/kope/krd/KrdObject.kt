package kope.krd

import com.fasterxml.jackson.core.JsonPointer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmErasure
import kotlin.reflect.typeOf

class KrdObject(val krdDefinition: KrdDefinition, val obj: Krd) {

    companion object {
        fun fromJsonTree(krdDefinition: KrdDefinition, json: ObjectNode): KrdObject {
            val obj = json.readTree(krdDefinition.ktype, JsonPointer.empty()) as Krd
            return KrdObject(krdDefinition, obj)
        }
    }

    fun asJsonTree(): JsonNode {
        val root = createTree(JsonNodeFactory.instance.objectNode(), obj, "root")
                .takeIf { !it.isMissingNode } as ObjectNode?
                ?: throw IllegalStateException("Something went wrong -- missing node returned for a root node")
        val def = krdDefinition.definition
        root.put("apiVersion", def.spec.group + "/" + def.spec.additionalProperties["version"])
        root.put("kind", def.spec.names.kind)
        return root
    }
}

private fun createTree(root: ObjectNode, obj: Any?, rootPropertyName: String): JsonNode {
    return if (obj != null) {
        when (obj) {
            is String -> root.put(rootPropertyName, obj)
            is Long -> root.put(rootPropertyName, obj)
            is Int -> root.put(rootPropertyName, obj)
            is Short -> root.put(rootPropertyName, obj)
            is Double -> root.put(rootPropertyName, obj)
            is Float -> root.put(rootPropertyName, obj)
            is BigDecimal -> root.put(rootPropertyName, obj)
            is BigInteger -> root.put(rootPropertyName, obj)
            is Boolean -> root.put(rootPropertyName, obj)
            is ByteArray -> root.put(rootPropertyName, obj)
            is Iterable<*> -> {
                root.putArray(rootPropertyName).also { array ->
                    obj.forEach { addItem(array, it) }
                }
            }
            is Map<*, *> -> {
                root.putObject(rootPropertyName).also {
                    obj.entries.forEach { (key, value) ->
                        if (key !is String) throw UnsupportedOperationException("Maps with String as a key only are supported")
                        createTree(it, value, key)
                    }
                }
            }
            else -> {
                if (!obj::class.isData) throw UnsupportedOperationException("As classes only data classes are supported right now but found ${obj::class}")
                val properties = obj::class.declaredMemberProperties
                root.putObject(rootPropertyName).also {
                    properties.forEach { property ->
                        if (property.findAnnotation<Ignore>() == null) {
                            val name = extractPropertyName(property)
                            createTree(it, property.call(obj), name)
                        }
                    }
                }
            }
        }
    } else {
        JsonNodeFactory.instance.missingNode()
    }
}

private fun addItem(array: ArrayNode, item: Any?) {
    if (item != null) {
        when (item) {
            is String -> array.add(item)
            is Long -> array.add(item)
            is Int -> array.add(item)
            is Short -> array.add(item.toInt())
            is Double -> array.add(item)
            is Float -> array.add(item)
            is BigDecimal -> array.add(item)
            is BigInteger -> array.add(item)
            is Boolean -> array.add(item)
            is ByteArray -> array.add(item)
            is Iterable<*> -> {
                val innerArray = array.addArray()
                item.forEach { addItem(innerArray, it) }
            }
            is Map<*, *> -> {
                val node = createTree(JsonNodeFactory.instance.objectNode(), item, "!root")
                array.add(node)
            }
            else -> {
                if (!item::class.isData) throw UnsupportedOperationException("As classes only data classes are supported right now but found ${item::class}")
                val node = createTree(JsonNodeFactory.instance.objectNode(), item, "!root")
                array.add(node)
            }
        }
    }
}

private fun JsonNode.readTree(
        ktype: KType,
        path: JsonPointer
): Any? {
    val at = this.at(path)
    if (at.isMissingNode) return null
    return when {
        ktype == typeOf<String>() || ktype == typeOf<String?>() -> at.textValue()
        ktype == typeOf<Int>() || ktype == typeOf<Int?>() -> at.intValue()
        ktype == typeOf<Long>() || ktype == typeOf<Long?>() -> at.longValue()
        ktype == typeOf<Short>() || ktype == typeOf<Short?>() -> at.shortValue()
        ktype == typeOf<Double>() || ktype == typeOf<Double?>() -> at.doubleValue()
        ktype == typeOf<Float>() || ktype == typeOf<Float?>() -> at.floatValue()
        ktype == typeOf<BigDecimal>() || ktype == typeOf<BigDecimal?>() -> at.decimalValue()
        ktype == typeOf<BigInteger>() || ktype == typeOf<BigInteger?>() -> at.bigIntegerValue()
        ktype == typeOf<Boolean>() || ktype == typeOf<Boolean?>() -> at.booleanValue()
        ktype == typeOf<ByteArray>() || ktype == typeOf<ByteArray?>() -> at.binaryValue()
        ktype.isSubtypeOf(typeOf<Iterable<*>>()) -> {
            val itemType = ktype.arguments.getOrNull(0)?.type
                    ?: throw UnsupportedOperationException(
                            "Type parameters for iterable class are not as expected ${ktype.arguments}. " +
                                    "Expected to be exactly one type parameter, i.e. List<MyClass>."
                    )
            if (!at.isArray) throw IllegalStateException("At $path expected array to fill in the Iterable type")
            (0 until at.size()).map { this.readTree(itemType, path.append(JsonPointer.compile("/$it"))) }
                    .let {
                        when {
                            ktype.isSubtypeOf(typeOf<Set<*>>()) -> it.toSet()
                            ktype.isSubtypeOf(typeOf<List<*>>()) -> it.toList()
                            else -> throw UnsupportedOperationException("Type $ktype is unsupported")
                        }
                    }
        }
        ktype.isSubtypeOf(typeOf<Map<String, *>>()) -> {
            val valueKtype = ktype.arguments.getOrNull(1)?.type
                    ?: throw IllegalStateException("Can't access second type parameter of the Map $ktype")
            at.fields().asSequence().map { (key, _) ->
                key to this.readTree(valueKtype, path.append(JsonPointer.compile("/$key")))
            }.toMap()
        }
        else -> {
            if (!ktype.jvmErasure.isData) throw UnsupportedOperationException("As classes only data classes are supported right now but found $ktype")
            val constructor = ktype.jvmErasure.primaryConstructor!!
            val parameters = constructor.parameters.mapNotNull { parameter ->
                val property = ktype.jvmErasure.declaredMemberProperties.firstOrNull { it.name == parameter.name }
                        ?: throw IllegalStateException("Can't find property for class $ktype for constructor parameter $parameter")
                val nodeName = extractPropertyName(property)
                val o = this.readTree(property.returnType, path.append(JsonPointer.compile("/$nodeName")))
                if (parameter.isOptional && o == null)
                    null
                else
                    parameter to o
            }.toMap()
            try {
                constructor.callBy(parameters)
            } catch (e: Exception) {
                throw IllegalStateException("Can't call constructor $constructor of $ktype with parameters:\n$parameters", e)
            }
        }
    }
}

private fun extractPropertyName(property: KProperty1<out Any, *>): String {
    return property.findAnnotation<PropertyDefinition>()
            ?.name
            ?.takeIf { it.isNotEmpty() }
            ?: property.name
}
