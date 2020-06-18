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

internal fun yaml(): ObjectMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()

class KrdGenerator(val clazz: KClass<out Krd>) {

    val yaml: String by lazy { generateYaml() }

    val definition: CustomResourceDefinition by lazy { customResourceDefinition() }

    private fun generateYaml(): String = yaml().writeValueAsString(definition)

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
                            if (resourceDefinition.apiVersion == "apiextensions.k8s.io/v1") {
                                schema {
                                    openAPIV3Schema = generateJsonSchemaOf(clazz)
                                }
                            }
                        }
                )
                if (resourceDefinition.apiVersion == "apiextensions.k8s.io/v1beta1") {
                    validation = newCustomResourceValidation {
                        openAPIV3Schema = generateJsonSchemaOf(clazz)
                    }
                }
            }
        }
    }
}

private val krdMembersToIgnore by lazy {
    Krd::class.declaredMemberProperties
            .filter { it.findAnnotation<Ignore>() != null }
            .map { it.name }
            .toSet()
}

private fun generateJsonSchemaOf(
        clazz: KClass<*>,
        annotations: List<Annotation> = emptyList(),
        nullableProperty: Boolean = false
): JSONSchemaProps {
    return newJSONSchemaProps {
        val propertyDefinition = annotations.firstOrNull { it is PropertyDefinition } as PropertyDefinition?

        if (propertyDefinition != null && propertyDefinition.description.isNotEmpty())
            description = propertyDefinition.description

        if (nullableProperty)
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
                    if (it.name in krdMembersToIgnore) return@mapNotNull null

                    val name = it.findAnnotation<PropertyDefinition>()
                            ?.name
                            ?.let { name -> if (name.isNotEmpty()) name else null }
                            ?: it.name
                    name to generateJsonSchemaOf(
                            clazz = it.returnType.jvmErasure,
                            annotations = it.annotations,
                            nullableProperty = it.returnType.isMarkedNullable
                    )
                }.toMap()
            }
        }
    }
}
