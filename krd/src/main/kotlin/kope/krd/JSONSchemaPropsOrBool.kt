package kope.krd

import com.fasterxml.jackson.annotation.*
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import io.fabric8.kubernetes.api.model.apiextensions.JSONSchemaProps
import java.io.IOException
import java.util.*
import javax.annotation.Generated

/**
 * JSONSchemaPropsOrBool represents JSONSchemaProps or a boolean value.
 * Defaults to true for the boolean property.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("org.jsonschema2pojo")
@JsonPropertyOrder("apiVersion", "kind", "metadata", "Allows", "Schema")
@JsonDeserialize(using = JSONSchemaPropsOrBool.Deserializer::class)
@JsonSerialize(using = JSONSchemaPropsOrBool.Serializer::class)
@Deprecated("Temporary fix")
class JSONSchemaPropsOrBool : io.fabric8.kubernetes.api.model.apiextensions.JSONSchemaPropsOrBool {
    /**
     *
     */
    @JsonProperty("Allows")
    private var allows: Boolean? = null

    /**
     *
     */
    @JsonProperty("Schema")
    private var schema: JSONSchemaProps? = null

    @JsonIgnore
    private val additionalProperties: MutableMap<String, Any> = HashMap()

    /**
     * No args constructor for use in serialization
     */
    constructor() {}

    @JvmOverloads
    constructor(allows: Boolean?, schema: JSONSchemaProps? = null) {
        this.allows = allows
        this.schema = schema
    }

    constructor(schema: JSONSchemaProps?) : this(null, schema) {}

    /**
     * Get Allows
     *
     * @return The allows
     */
    @JsonProperty("Allows")
    override fun getAllows(): Boolean? {
        return allows
    }

    /**
     * Set Allows
     *
     * @param allows The Allows
     */
    @JsonProperty("Allows")
    override fun setAllows(allows: Boolean) {
        this.allows = allows
    }

    /**
     * Get Schema
     *
     * @return The schema
     */
    @JsonProperty("Schema")
    override fun getSchema(): JSONSchemaProps? {
        return schema
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

    class Serializer : JsonSerializer<JSONSchemaPropsOrBool>() {
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

    class Deserializer : JsonDeserializer<JSONSchemaPropsOrBool>() {
        @Throws(IOException::class, JsonProcessingException::class)
        override fun deserialize(jsonParser: JsonParser, ctxt: DeserializationContext): JSONSchemaPropsOrBool {
            return if (jsonParser.isExpectedStartObjectToken) {
                JSONSchemaPropsOrBool(ctxt.readValue(jsonParser, JSONSchemaProps::class.java))
            } else {
                JSONSchemaPropsOrBool(ctxt.readValue(jsonParser, Boolean::class.java))
            }
        }
    }
}