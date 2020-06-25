package kope.krd

import com.fasterxml.jackson.annotation.*
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import io.fabric8.kubernetes.api.model.apiextensions.JSONSchemaProps
import java.io.IOException
import java.util.*

/**
 * JSONSchemaPropsOrArray represents a value that can either be a JSONSchemaProps
 * or an array of JSONSchemaProps. Mainly here for serialization purposes.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder("apiVersion", "kind", "metadata", "JSONSchemas", "Schema")
@JsonDeserialize(using = JSONSchemaPropsOrArray.Deserializer::class)
@JsonSerialize(using = JSONSchemaPropsOrArray.Serializer::class)
@Deprecated("Temporary fix")
class JSONSchemaPropsOrArray : io.fabric8.kubernetes.api.model.apiextensions.JSONSchemaPropsOrArray {
    @JsonProperty("JSONSchemas")
    private var jSONSchemas: List<JSONSchemaProps>? = ArrayList()

    @JsonProperty("Schema")
    private var schema: JSONSchemaProps? = null

    @JsonIgnore
    private val additionalProperties: MutableMap<String, Any> = HashMap()

    /**
     * No args constructor for use in serialization
     */
    constructor() {}
    constructor(jSONSchemas: List<JSONSchemaProps>?, schema: JSONSchemaProps?) {
        this.jSONSchemas = jSONSchemas
        this.schema = schema
    }

    constructor(jsonSchemaPropsArray: List<JSONSchemaProps>) : this(jsonSchemaPropsArray, null) {}
    constructor(schema: JSONSchemaProps?) : this(null, schema) {}

    /**
     * Get JSON schemas
     * @return The jSONSchemas
     */
    @JsonProperty("JSONSchemas")
    override fun getJSONSchemas(): List<JSONSchemaProps>? {
        return jSONSchemas
    }

    /**
     * Set JSON schemas
     *
     * @param jSONSchemas The JSONSchemas
     */
    @JsonProperty("JSONSchemas")
    override fun setJSONSchemas(jSONSchemas: List<JSONSchemaProps>) {
        this.jSONSchemas = jSONSchemas
    }

    /**
     * Get schema
     *
     * @return The schema
     */
    @JsonProperty("Schema")
    override fun getSchema(): JSONSchemaProps {
        return schema!!
    }

    /**
     * Set schema
     *
     * @param schema The Schema
     */
    @JsonProperty("Schema")
    override fun setSchema(schema: JSONSchemaProps) {
        this.schema = schema
    }

    @JsonAnyGetter
    override fun getAdditionalProperties(): Map<String, Any> {
        return this.additionalProperties
    }

    @JsonAnySetter
    override fun setAdditionalProperty(name: String, value: Any) {
        this.additionalProperties[name] = value
    }

    class Serializer : JsonSerializer<JSONSchemaPropsOrArray>() {
        @Throws(IOException::class)
        override fun serialize(value: JSONSchemaPropsOrArray, jgen: JsonGenerator, provider: SerializerProvider) {
            require(!(value.getSchema() != null && value.jsonSchemas != null)) { "schema and jSONSchema both can't be present " }
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

    class Deserializer : JsonDeserializer<JSONSchemaPropsOrArray>() {
        @Throws(IOException::class)
        override fun deserialize(jsonParser: JsonParser, ctxt: DeserializationContext): JSONSchemaPropsOrArray {
            return if (jsonParser.isExpectedStartArrayToken) {
                JSONSchemaPropsOrArray(ctxt.readValue<Any>(jsonParser, ctxt.typeFactory.constructCollectionType(MutableList::class.java, JSONSchemaProps::class.java)) as List<JSONSchemaProps>)
            } else {
                JSONSchemaPropsOrArray(ctxt.readValue(jsonParser, JSONSchemaProps::class.java))
            }
        }
    }
}