package kope.krd

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.fkorotkov.kubernetes.apiextensions.*
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition
import io.fabric8.kubernetes.api.model.apiextensions.JSONSchemaProps
import kotlin.reflect.KClass
import kotlin.reflect.full.*
import kotlin.reflect.jvm.jvmErasure

fun yaml(): ObjectMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()

class KrdGenerator(val clazz: KClass<*>) {

    fun generateYaml(): String {
        val crdDefinition = customResourceDefinition()

        return yaml().writeValueAsString(crdDefinition)
    }

    private fun customResourceDefinition(): CustomResourceDefinition {
        val resourceDefinition = clazz.findAnnotation<ResourceDefinition>()
                ?: throw IllegalStateException("Specify ${ResourceDefinition::class} annotation on specified class $clazz")
        return newCustomResourceDefinition {
            apiVersion = resourceDefinition.apiVersion
            metadata {
                name = resourceDefinition.name
            }
            spec {
                group = resourceDefinition.group
                scope = resourceDefinition.scope.value
                preserveUnknownFields = resourceDefinition.preserveUnknownFields
                names {
                    plural = resourceDefinition.pluralName
                    singular = resourceDefinition.singularName
                    kind = resourceDefinition.kind
                    shortNames = resourceDefinition.shortNames.toList()
                }
                versions = listOf(
                        newCustomResourceDefinitionVersion {
                            name = resourceDefinition.version
                            served = true
                            storage = true
                            schema {
                                openAPIV3Schema = generateOpenV3SchemaOf(clazz)
                            }
                        }
                )
            }
        }
    }
}

private fun generateOpenV3SchemaOf(clazz: KClass<*>, annotations: List<Annotation> = emptyList()): JSONSchemaProps {
    return newJSONSchemaProps {
        val propertyDefinition = annotations.firstOrNull { it is PropertyDefinition } as PropertyDefinition?

        if (propertyDefinition != null && propertyDefinition.description.isNotEmpty())
            description = propertyDefinition.description

        if (clazz.createType().isSubtypeOf(Any::class.createType(nullable = true)))
            nullable = true

        when (clazz) {
            String::class -> {
                type = "string"
                val stringDefinition = annotations.firstOrNull { it is StringDefinition } as StringDefinition?
                if (stringDefinition != null) {
                    if (stringDefinition.format.isNotEmpty()) format = stringDefinition.format
                    if (stringDefinition.pattern.isNotEmpty()) pattern = stringDefinition.pattern
                    if (stringDefinition.minLength >= 0) minLength = stringDefinition.minLength.toLong()
                    if (stringDefinition.maxLength >= 0) maxLength = stringDefinition.maxLength.toLong()
                }
            }
            Int::class, Long::class -> {
                type = "integer"
                val integerDefinition = annotations.firstOrNull { it is IntegerDefinition } as IntegerDefinition?
                if (integerDefinition != null) {
                    if (integerDefinition.minimum > Int.MIN_VALUE)
                        minimum = integerDefinition.minimum.toDouble()
                    if (integerDefinition.maximum < Int.MAX_VALUE)
                        maximum = integerDefinition.maximum.toDouble()
                }
            }
            else -> {
                if (clazz.javaPrimitiveType != null) throw UnsupportedOperationException("$clazz support is not implemented")
                type = "object"
                properties = clazz.declaredMemberProperties.mapNotNull {
                    if (it.findAnnotation<Ignore>() != null) return@mapNotNull null
                    val name = it.findAnnotation<PropertyDefinition>()
                            ?.name
                            ?.let { name -> if (name.isNotEmpty()) name else null }
                            ?: it.name
                    name to generateOpenV3SchemaOf(it.returnType.jvmErasure, it.annotations)
                }.toMap()
            }
        }
    }
}
