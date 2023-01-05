package kope.koperator

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.fasterxml.jackson.module.kotlin.treeToValue
import io.fabric8.kubernetes.api.model.GenericKubernetesResource
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.WatcherException
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
        check(
            client.namespace != null && krdDef.resourceDefinition.scope == Scope.NAMESPACED ||
                    krdDef.resourceDefinition.scope == Scope.CLUSTER
        ) {
            "Krd is defined as namespaced but namespace is not specified"
        }
        namespace =
            if (krdDef.resourceDefinition.scope == Scope.NAMESPACED) client.namespace else null
    }

    fun list(): List<T> {
        val values = client.genericKubernetesResources(definitionContext).list()

        val items = values.items
        if (items == null || items.isEmpty()) return emptyList()

        @Suppress("UNCHECKED_CAST")
        return items.asSequence()
            .map { KrdObject.fromJsonTree(krdDef, json.valueToTree(it) as ObjectNode).obj as T }
            .toList()
    }

    fun get(name: String): T? {
        @Suppress("UNCHECKED_CAST")
        return try {
            client.genericKubernetesResources(definitionContext).withName(name).get().let {
                val node = json.valueToTree<JsonNode>(it)
                if (node is ObjectNode) {
                    KrdObject.fromJsonTree(krdDef, node).obj as T
                } else {
                    null
                }
            }
        } catch (e: KubernetesClientException) {
            if (e.status.code == 404) null
            else throw e
        }
    }

    fun delete(obj: T): Boolean {
        return try {
            val status =
                client.genericKubernetesResources(definitionContext).withName(obj.metadata.name)
                    .delete()
            return status.size > 0
        } catch (e: KubernetesClientException) {
            if (e.code == 404) false else throw e
        }
    }

    fun create(obj: T) {
        val resource = GenericKubernetesResource().also {
            it.additionalProperties = json.treeToValue(krdDef.krdObject(obj).asJsonTree())
        }
        client.genericKubernetesResources(definitionContext)
            .withName(obj.metadata.name)
            .create(resource)
    }

    fun createOrReplace(obj: T) {
        val asJsonTree = krdDef.krdObject(obj).asJsonTree()
        val r = client.genericKubernetesResources(definitionContext)

        val existingObject = r.withName(obj.metadata.name).get()
        if (existingObject != null) {
            @Suppress("UNCHECKED_CAST")
            val resourceVersion = existingObject.metadata
                .resourceVersion
                ?: throw IllegalStateException("$existingObject doesn't contain `metadata.resourceVersion`")
            (asJsonTree.at("/metadata") as ObjectNode).put("resourceVersion", resourceVersion)
        }
        r.withName(obj.metadata.name).edit {
            it.additionalProperties = json.treeToValue(krdDef.krdObject(obj).asJsonTree())
            it
        }
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
    private var onCloseCallback: ((Exception?) -> Unit)? = null
    private var isBeingWatched = false

    fun onAction(callback: (Watcher.Action, T) -> Unit): Watch<T> {
        onActionCallback = callback
        doWatch()
        return this
    }

    fun onClose(callback: (Exception?) -> Unit): Watch<T> {
        onCloseCallback = callback
        doWatch()
        return this
    }

    private fun doWatch() {
        if (!isBeingWatched) {
            isBeingWatched = true
            client.genericKubernetesResources(definitionContext)
                .let { if (name != null) it.withName(name) else it }
                .watch(
                    object : Watcher<GenericKubernetesResource> {

                        override fun onClose(cause: WatcherException?) {
                            if (onCloseCallback != null) onCloseCallback!!.invoke(cause)
                        }

                        override fun eventReceived(
                            action: Watcher.Action,
                            resource: GenericKubernetesResource
                        ) {
                            @Suppress("UNCHECKED_CAST")
                            if (onActionCallback != null) {
                                val o = KrdObject.fromJsonTree(
                                    krdDef, json.valueToTree(resource)
                                            as ObjectNode
                                ).obj as T
                                onActionCallback!!.invoke(action, o)
                            }
                        }
                    }
                )
        }
    }
}

inline fun <reified T : Krd> KubernetesClient.krd(): KrdResource<T> = KrdResource(this, T::class)