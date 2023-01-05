package kope.krd

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import io.fabric8.kubernetes.api.model.apiextensions.v1.JSONSchemaProps
import io.fabric8.kubernetes.api.model.apiextensions.v1.JSONSchemaPropsOrArray
import java.io.IOException

@Deprecated("Temporary fix")
object JSONSchemaPropsOrArraySerializer : JsonSerializer<JSONSchemaPropsOrArray>() {
    @Throws(IOException::class)
    override fun serialize(value: JSONSchemaPropsOrArray, jgen: JsonGenerator, provider: SerializerProvider) {
        require(!(value.schema != null && value.jsonSchemas != null)) { "schema and jSONSchema both can't be present " }
        if (value.jsonSchemas != null) {
            writeArray(value, jgen)
        } else if (value.getSchema() != null) {
            jgen.writeObject(value.getSchema())
        } else {
            jgen.writeNull()
        }
    }

    @Throws(IOException::class)
    private fun writeArray(value: JSONSchemaPropsOrArray, jgen: JsonGenerator) {
        val jsonSchemas = value.jsonSchemas
        if (jsonSchemas != null) {
            jgen.writeStartArray(jsonSchemas.size)
            for (jsonSchemaProps in jsonSchemas) {
                jgen.writeObject(jsonSchemaProps)
            }
            jgen.writeEndArray()
        }
    }
}

@Deprecated("Temporary fix")
object JSONSchemaPropsOrArrayDeserializer : JsonDeserializer<JSONSchemaPropsOrArray>() {
    @Suppress("UNCHECKED_CAST")
    @Throws(IOException::class)
    override fun deserialize(jsonParser: JsonParser, ctxt: DeserializationContext): JSONSchemaPropsOrArray {
        return if (jsonParser.isExpectedStartArrayToken) {
            JSONSchemaPropsOrArray(
                    ctxt.readValue<Any>(jsonParser, ctxt.typeFactory.constructCollectionType(MutableList::class.java, JSONSchemaProps::class.java)) as List<JSONSchemaProps>,
                    null
            )
        } else {
            JSONSchemaPropsOrArray(
                    null,
                    ctxt.readValue(jsonParser, JSONSchemaProps::class.java)
            )
        }
    }
}
