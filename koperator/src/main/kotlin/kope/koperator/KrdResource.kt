package kope.koperator

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.fabric8.kubernetes.api.model.ListOptions
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext
import kope.krd.*
import kotlin.reflect.KClass

private val json by lazy { ObjectMapper().registerKotlinModule().registerModule(fixesModule) }

class KrdResource<T : Krd>(
        val client: KubernetesClient,
        val clazz: KClass<T>
) {

    val krdDef = KrdDefinition(clazz)
    val namespace: String?

    private val definitionContext = CustomResourceDefinitionContext.fromCrd(krdDef.definition)

    init {
        check(client.namespace != null && krdDef.resourceDefinition.scope == Scope.NAMESPACED ||
                krdDef.resourceDefinition.scope == Scope.CLUSTER) {
            "Krd is defined as namespaced but namespace is not specified"
        }
        namespace = if (krdDef.resourceDefinition.scope == Scope.NAMESPACED) client.namespace else null
    }

    fun list(): List<T> {
        val values = client.customResource(definitionContext).list(namespace)

        val items = values["items"] as Collection<Any?>?
        if (items == null || items.isEmpty()) return emptyList()

        @Suppress("UNCHECKED_CAST")
        return items.asSequence()
                .map { it as Map<String, Any?> }
                .map { KrdObject.fromJsonTree(krdDef, json.valueToTree(it) as ObjectNode).obj as T }
                .toList()
    }

    fun get(name: String): T? {
        @Suppress("UNCHECKED_CAST")
        return try {
            client.customResource(definitionContext).get(namespace, name).let {
                KrdObject.fromJsonTree(krdDef, json.valueToTree(it) as ObjectNode).obj as T
            }
        } catch (e: KubernetesClientException) {
            if (e.status.code == 404) null
            else throw e
        }
    }

    fun delete(obj: T): Boolean {
        return try {
            client.customResource(definitionContext).delete(namespace, obj.metadata.name)
            true
        } catch (e: KubernetesClientException) {
            if (e.code == 404) false else throw e
        }
    }

    fun create(obj: T) {
        client.customResource(definitionContext)
                .create(namespace, json.writeValueAsString(krdDef.krdObject(obj).asJsonTree()))
    }

    fun createOrReplace(obj: T) {
        val asJsonTree = krdDef.krdObject(obj).asJsonTree()
        val r = client.customResource(definitionContext)

        val existingObject = r.get(namespace, obj.metadata.name)
        if (existingObject != null) {
            @Suppress("UNCHECKED_CAST")
            val resourceVersion = (existingObject["metadata"] as Map<String, Any?>?)
                    ?.get("resourceVersion") as String?
                    ?: throw IllegalStateException("$existingObject doesn't contain `metadata.resourceVersion`")
            (asJsonTree.at("/metadata") as ObjectNode).put("resourceVersion", resourceVersion)
        }
        r.edit(namespace, obj.metadata.name, json.writeValueAsString(asJsonTree))
    }

    fun watch(name: String? = null): Watch<T> {
        return Watch(name, client, clazz)
    }
}

class Watch<T : Krd>(
        val name: String?,
        val client: KubernetesClient,
        clazz: KClass<T>
) {
    private val krdDef = KrdDefinition(clazz)
    private val definitionContext = CustomResourceDefinitionContext.fromCrd(krdDef.definition)

    private var onActionCallback: ((Watcher.Action, T) -> Unit)? = null
    private var onCloseCallback: ((KubernetesClientException?) -> Unit)? = null
    private var isBeingWatched = false

    fun onAction(callback: (Watcher.Action, T) -> Unit): Watch<T> {
        onActionCallback = callback
        doWatch()
        return this
    }

    fun onClose(callback: (KubernetesClientException?) -> Unit): Watch<T> {
        onCloseCallback = callback
        doWatch()
        return this
    }

    private fun doWatch() {
        if (!isBeingWatched) {
            isBeingWatched = true
            client.customResource(definitionContext).watch(
                    client.namespace,
                    name,
                    emptyMap(),
                    ListOptions(),
                    object : Watcher<String> {

                        override fun onClose(cause: KubernetesClientException?) {
                            if (onCloseCallback != null) onCloseCallback!!.invoke(cause)
                        }

                        override fun eventReceived(action: Watcher.Action, resource: String) {
                            @Suppress("UNCHECKED_CAST")
                            if (onActionCallback != null) {
                                val o = krdDef.krdObjectFromJson(resource).obj as T
                                onActionCallback!!.invoke(action, o)
                            }
                        }
                    }
            )
        }
    }
}

inline fun <reified T : Krd> KubernetesClient.krd(): KrdResource<T> = KrdResource(this, T::class)