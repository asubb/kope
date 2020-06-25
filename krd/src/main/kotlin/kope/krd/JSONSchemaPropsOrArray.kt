package kope.krd;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apiextensions.JSONSchemaProps;
import io.sundr.builder.annotations.Buildable;
import io.sundr.builder.annotations.BuildableReference;
import io.sundr.builder.annotations.Inline;

import javax.annotation.Generated;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * JSONSchemaPropsOrArray represents a value that can either be a JSONSchemaProps
 * or an array of JSONSchemaProps. Mainly here for serialization purposes.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("org.jsonschema2pojo")
@JsonPropertyOrder({
        "apiVersion",
        "kind",
        "metadata",
        "JSONSchemas",
        "Schema"
})
@JsonDeserialize(using = JSONSchemaPropsOrArray.Deserializer.class)
@JsonSerialize(using = JSONSchemaPropsOrArray.Serializer.class)
@Buildable(editableEnabled = false, validationEnabled = false, generateBuilderPackage = false, lazyCollectionInitEnabled = false, builderPackage = "io.fabric8.kubernetes.api.builder", inline = {
        @Inline(type = Doneable.class, prefix = "Doneable", value = "done")
}, refs = {
        @BuildableReference(ObjectMeta.class),
        @BuildableReference(LabelSelector.class),
        @BuildableReference(Container.class),
        @BuildableReference(PodTemplateSpec.class),
        @BuildableReference(ResourceRequirements.class),
        @BuildableReference(IntOrString.class),
        @BuildableReference(ObjectReference.class),
        @BuildableReference(LocalObjectReference.class),
        @BuildableReference(PersistentVolumeClaim.class)
})
@Deprecated
public class JSONSchemaPropsOrArray extends io.fabric8.kubernetes.api.model.apiextensions.JSONSchemaPropsOrArray {

    @JsonProperty("JSONSchemas")
    private List<JSONSchemaProps> jSONSchemas = new ArrayList<JSONSchemaProps>();

    @JsonProperty("Schema")
    private JSONSchemaProps schema;

    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    /**
     * No args constructor for use in serialization
     */
    public JSONSchemaPropsOrArray() {
    }

    public JSONSchemaPropsOrArray(List<JSONSchemaProps> jSONSchemas, JSONSchemaProps schema) {
        this.jSONSchemas = jSONSchemas;
        this.schema = schema;
    }

    public JSONSchemaPropsOrArray(List<JSONSchemaProps> jsonSchemaPropsArray) {
        this(jsonSchemaPropsArray, null);
    }

    public JSONSchemaPropsOrArray(JSONSchemaProps schema) {
        this(null, schema);
    }

    /**
     * Get JSON schemas
     * @return The jSONSchemas
     */
    @JsonProperty("JSONSchemas")
    public List<JSONSchemaProps> getJSONSchemas() {
        return jSONSchemas;
    }

    /**
     * Set JSON schemas
     *
     * @param jSONSchemas The JSONSchemas
     */
    @JsonProperty("JSONSchemas")
    public void setJSONSchemas(List<JSONSchemaProps> jSONSchemas) {
        this.jSONSchemas = jSONSchemas;
    }

    /**
     * Get schema
     *
     * @return The schema
     */
    @JsonProperty("Schema")
    public JSONSchemaProps getSchema() {
        return schema;
    }

    /**
     * Set schema
     *
     * @param schema The Schema
     */
    @JsonProperty("Schema")
    public void setSchema(JSONSchemaProps schema) {
        this.schema = schema;
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }


    public static class Serializer extends JsonSerializer<JSONSchemaPropsOrArray> {

        @Override
        public void serialize(JSONSchemaPropsOrArray value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
            if (value.getSchema() != null && value.getJSONSchemas() != null) {
                throw new IllegalArgumentException("schema and jSONSchema both can't be present ");
            } else if (value.getJSONSchemas() != null) {
                writeArray(value, jgen);
            } else if (value.getSchema() != null) {
                jgen.writeObject(value.getSchema());
            } else {
                jgen.writeNull();
            }
        }

        private void writeArray(JSONSchemaPropsOrArray value, JsonGenerator jgen) throws IOException {
            jgen.writeStartArray(value.getJSONSchemas().size());
            for (JSONSchemaProps jsonSchemaProps : value.getJSONSchemas()) {
                jgen.writeObject(jsonSchemaProps);
            }
            jgen.writeEndArray();
        }

    }

    public static class Deserializer extends JsonDeserializer<JSONSchemaPropsOrArray> {

        @Override
        public JSONSchemaPropsOrArray deserialize(JsonParser jsonParser, DeserializationContext ctxt) throws IOException {
            if (jsonParser.isExpectedStartArrayToken()) {
                return new JSONSchemaPropsOrArray((List<JSONSchemaProps>)ctxt.readValue(jsonParser, ctxt.getTypeFactory().constructCollectionType(List.class, JSONSchemaProps.class)));
            } else {
                return new JSONSchemaPropsOrArray(ctxt.readValue(jsonParser, JSONSchemaProps.class));
            }
        }
    }

}