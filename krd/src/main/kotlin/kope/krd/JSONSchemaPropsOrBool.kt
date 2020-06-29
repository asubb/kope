package kope.krd

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import io.fabric8.kubernetes.api.model.apiextensions.JSONSchemaProps
import io.fabric8.kubernetes.api.model.apiextensions.JSONSchemaPropsOrBool
import java.io.IOException

@Deprecated("Temporary fix")
object JSONSchemaPropsOrBoolSerializer : JsonSerializer<JSONSchemaPropsOrBool>() {
    @Throws(IOException::class)
    override fun serialize(value: JSONSchemaPropsOrBool, jgen: JsonGenerator, provider: SerializerProvider) {
        val allows = value.getAllows()
        val schema = value.getSchema()
        require(!(schema != null && allows != null)) { "schema and allows both can't be set" }
        if (allows != null) {
            jgen.writeBoolean(allows)
        } else if (schema != null) {
            jgen.writeObject(schema)
        } else {
            jgen.writeNull()
        }
    }
}

@Deprecated("Temporary fix")
object JSONSchemaPropsOrBoolDeserializer : JsonDeserializer<JSONSchemaPropsOrBool>() {
    @Throws(IOException::class, JsonProcessingException::class)
    override fun deserialize(jsonParser: JsonParser, ctxt: DeserializationContext): JSONSchemaPropsOrBool {
        return if (jsonParser.isExpectedStartObjectToken) {
            JSONSchemaPropsOrBool(null, ctxt.readValue(jsonParser, JSONSchemaProps::class.java))
        } else {
            JSONSchemaPropsOrBool(ctxt.readValue(jsonParser, Boolean::class.java), null)
        }
    }
}
