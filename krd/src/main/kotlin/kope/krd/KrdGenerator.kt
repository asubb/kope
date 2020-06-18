package kope.krd

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.fkorotkov.kubernetes.apiextensions.*
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition
import io.fabric8.kubernetes.api.model.apiextensions.JSONSchemaProps
import kotlin.reflect.KClass
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
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
        if (propertyDefinition != null) {
            if (propertyDefinition.description.isNotEmpty()) description = propertyDefinition.description
        }
        when (clazz) {
            String::class -> {
                type = "string"
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
            Long::class -> {
                type = "integer"
            }
            else -> {
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
