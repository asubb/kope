package kope.krd

import com.fasterxml.jackson.core.JsonPointer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
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
        root.put("apiVersion", def.spec.group + "/" + def.spec.version)
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
            else -> {
                if (!obj::class.isData) throw UnsupportedOperationException("As classes only data classes are supported right now but found ${obj::class}")
                val properties = obj::class.declaredMemberProperties
                root.putObject(rootPropertyName).also {
                    properties.forEach { property ->
                        if (property.findAnnotation<Ignore>() == null) {
                            val name = property.findAnnotation<PropertyDefinition>()?.name ?: property.name
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

private fun ObjectNode.readTree(
        ktype: KType,
        path: JsonPointer
): Any? {
    if (this.at(path).isMissingNode) return null
    return when (ktype) {
        typeOf<String>(), typeOf<String?>() -> this.at(path).textValue()
        typeOf<Int>(), typeOf<Int?>() -> this.at(path).intValue()
        typeOf<Long>(), typeOf<Long?>() -> this.at(path).longValue()
        typeOf<Short>(), typeOf<Short?>() -> this.at(path).shortValue()
        typeOf<Double>(), typeOf<Double?>() -> this.at(path).doubleValue()
        typeOf<Float>(), typeOf<Float?>() -> this.at(path).floatValue()
        typeOf<BigDecimal>(), typeOf<BigDecimal?>() -> this.at(path).decimalValue()
        typeOf<BigInteger>(), typeOf<BigInteger?>() -> this.at(path).bigIntegerValue()
        typeOf<Boolean>(), typeOf<Boolean?>() -> this.at(path).booleanValue()
        typeOf<ByteArray>(), typeOf<ByteArray?>() -> this.at(path).binaryValue()
        else -> {
            if (!ktype.jvmErasure.isData) throw UnsupportedOperationException("As classes only data classes are supported right now but found $ktype")
            val constructor = ktype.jvmErasure.primaryConstructor!!
            val parameters = constructor.parameters.mapNotNull { parameter ->
                val property = ktype.jvmErasure.declaredMemberProperties.firstOrNull { it.name == parameter.name }
                        ?: throw IllegalStateException("Can't find property for class $ktype for constructor parameter $parameter")
                val nodeName = property.findAnnotation<PropertyDefinition>()?.name ?: property.name
                val o = this.readTree(property.returnType, path.append(JsonPointer.compile("/$nodeName")))
                if (parameter.isOptional && o == null)
                    null
                else
                    parameter to o
            }.toMap()
            constructor.callBy(parameters)
        }
    }
}
