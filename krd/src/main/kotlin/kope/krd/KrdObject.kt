package kope.krd

import com.fasterxml.jackson.core.JsonPointer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.reflect.KClass
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmErasure

class KrdObject(val krdDefinition: KrdDefinition, val obj: Krd) {

    companion object {
        fun fromJsonTree(krdDefinition: KrdDefinition, json: ObjectNode): KrdObject {
            val obj = json.readTree(krdDefinition.clazz, JsonPointer.empty()) as Krd
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
        clazz: KClass<*>,
        path: JsonPointer
): Any? {
    if (this.at(path).isMissingNode) return null
    return when (clazz) {
        String::class -> this.at(path).textValue()
        Int::class -> this.at(path).intValue()
        Long::class -> this.at(path).longValue()
        Short::class -> this.at(path).shortValue()
        Double::class -> this.at(path).doubleValue()
        Float::class -> this.at(path).floatValue()
        BigDecimal::class -> this.at(path).decimalValue()
        BigInteger::class -> this.at(path).bigIntegerValue()
        Boolean::class -> this.at(path).booleanValue()
        ByteArray::class -> this.at(path).binaryValue()
        else -> {
            if (!clazz.isData) throw UnsupportedOperationException("As classes only data classes are supported right now but found ${clazz}")
            val constructor = clazz.primaryConstructor!!
            val parameters = constructor.parameters.mapNotNull { parameter ->
                val property = clazz.declaredMemberProperties.firstOrNull { it.name == parameter.name }
                        ?: throw IllegalStateException("Can't find property for class $clazz for constructor parameter $parameter")
                val nodeName = property.findAnnotation<PropertyDefinition>()?.name ?: property.name
                val o = this.readTree(property.returnType.jvmErasure, path.append(JsonPointer.compile("/$nodeName")))
                if (parameter.isOptional && o == null)
                    null
                else
                    parameter to o
            }.toMap()
            constructor.callBy(parameters)
        }
    }
}
