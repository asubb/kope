package kope.krd

import com.fasterxml.jackson.databind.module.SimpleModule
import io.fabric8.kubernetes.api.model.apiextensions.v1.JSONSchemaPropsOrArray
import io.fabric8.kubernetes.api.model.apiextensions.v1.JSONSchemaPropsOrBool

val fixesModule by lazy {
    val module = SimpleModule()
    // waiting https://github.com/fabric8io/kubernetes-client/pull/2281 to get a proper fix
    module.addSerializer(JSONSchemaPropsOrBool::class.java, JSONSchemaPropsOrBoolSerializer)
    module.addDeserializer(JSONSchemaPropsOrBool::class.java, JSONSchemaPropsOrBoolDeserializer)
    module.addSerializer(JSONSchemaPropsOrArray::class.java, JSONSchemaPropsOrArraySerializer)
    module.addDeserializer(JSONSchemaPropsOrArray::class.java, JSONSchemaPropsOrArrayDeserializer)
    module
}

