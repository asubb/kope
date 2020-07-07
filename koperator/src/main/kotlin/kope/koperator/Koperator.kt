package kope.koperator

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.fabric8.kubernetes.api.model.DeleteOptions
import io.fabric8.kubernetes.api.model.DeletionPropagation
import io.fabric8.kubernetes.api.model.ListOptions
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext
import kope.krd.Krd
import kope.krd.KrdDefinition
import kope.krd.fixesModule
import mu.KotlinLogging
import java.io.Closeable
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.reflect.KClass

private val json by lazy { ObjectMapper().registerKotlinModule().registerModule(fixesModule) }

abstract class Koperator<K : Kontroller> : Closeable {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    protected val pool = Executors.newSingleThreadExecutor()

    abstract val resources: List<KClass<out Krd>>

    abstract val kontroller: K

    open fun initialize() {
        log.info { "[$this] Kontroller initializing..." }
        kontroller.initialize()
        log.info { "[$this] Kontroller initialized" }
    }

    open fun await(): Future<Unit> {
        return pool.submit(Callable {
            log.info { "[$this] Kontroller main loop started" }
            kontroller.main()
            log.info { "[$this] Kontroller main loop finished" }
        })
    }

    open fun tearDown() {
        log.info { "[$this] Kontroller tearing down..." }
        kontroller.tearDown()
        log.info { "[$this] Kontroller torn down" }
    }

    override fun close() {
        log.info { "[$this] Shutting down execution pool..." }
        pool.shutdown()
        if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {
            log.info { "[$this] Force shutting down execution pool as it hasn't finished in 30 sec..." }
            pool.shutdownNow()
        }
        log.info { "[$this] Shut down execution pool" }
    }
}

fun Koperator<*>.install(client: KubernetesClient) {
    val log = KotlinLogging.logger {}

    val resourcesToCreate = this.resources.mapNotNull { krd ->
        val g = KrdDefinition(krd)
        log.info { "Installing resource $krd with definition:\n${g.yaml}" }
        try {
            val crd = try {
                client.customResourceDefinitions().withName(g.definition.metadata.name).get()
            } catch (e: KubernetesClientException) {
                if (e.code == 404) null else throw e
            }
            if (crd == null) {
                g.definition
            } else {
                log.warn { "CRD is already installed:\n${g.yaml}" }
                null
            }
        } catch (e: KubernetesClientException) {
            throw KoperatorException("\"Installing resource $krd with definition failed:\\n${g.yaml}\"", e)
        }
    }

    val l = CountDownLatch(resourcesToCreate.size)
    val names = resourcesToCreate.map { it.metadata.name }.toSet()
    client.customResourceDefinitions().watch(object : Watcher<CustomResourceDefinition> {
        override fun onClose(cause: KubernetesClientException?) {
            if (cause != null) {
                log.warn(cause) { "Problems while creating CRDs:\n${resourcesToCreate.joinToString("\n---\n")}" }
            }
        }

        override fun eventReceived(action: Watcher.Action, resource: CustomResourceDefinition) {
            if (resource.metadata.name in names) {
                when (action) {
                    Watcher.Action.DELETED, Watcher.Action.MODIFIED, Watcher.Action.ERROR ->
                        log.warn { "Got unexpected action=$action on watch for CRD creation: $resource" }
                    Watcher.Action.ADDED -> {
                        log.info { "CRD creation confirmed: $resource" }
                        l.countDown()
                    }
                }
            }
        }
    }).use {
        resourcesToCreate.forEach {
            client.customResourceDefinitions().create(it)
        }

        l.await(60000, TimeUnit.MILLISECONDS)
    }
}

fun Koperator<*>.reset(client: KubernetesClient) {
    val log = KotlinLogging.logger {}
    val kindsToRemove = this.resources.map { krd ->
        val g = KrdDefinition(krd)
        g.resourceDefinition.kind
    }.toSet()

    log.info { "Resting resources with kinds: $kindsToRemove" }
    val definitions = client.customResourceDefinitions().list()
            .items
            .filter { it.spec.names.kind in kindsToRemove }

    definitions.forEach { definition ->
        val context = CustomResourceDefinitionContext.fromCrd(definition)
        @Suppress("UNCHECKED_CAST")
        try {
            val objects = client.customResource(context).list()
            val items = objects["items"] as Collection<Map<String, Any?>>?
            if (items != null && items.isNotEmpty()) {
                val l = CountDownLatch(items.size)
                val names = items.mapNotNull { item -> (item["metadata"] as Map<String, Any?>?)?.get("name") as String? }.toSet()

                client.customResource(context).watch(
                        client.namespace,
                        null,
                        emptyMap(),
                        ListOptions(),
                        object : Watcher<String> {
                            override fun onClose(cause: KubernetesClientException?) {
                                if (cause != null)
                                    log.warn(cause) { "Problems while removing CRD object with names=$names, items=$items" }
                            }

                            override fun eventReceived(action: Watcher.Action, resource: String) {
                                val resourceName = json.readTree(resource).at("/metadata/name").textValue()
                                if (resourceName in names) {
                                    when (action) {
                                        Watcher.Action.ADDED, Watcher.Action.MODIFIED, Watcher.Action.ERROR ->
                                            log.warn { "Got unexpected action=$action on watch for CRD Object deletion: $resource" }
                                        Watcher.Action.DELETED -> {
                                            log.info { "CRD Object deletion confirmed: $resource" }
                                            l.countDown()
                                        }
                                    }
                                }
                            }
                        }
                ).use {
                    val deleteOptions = DeleteOptions()
                    deleteOptions.gracePeriodSeconds = 0
                    deleteOptions.propagationPolicy = "Foreground"
                    val deletedObjects = client.customResource(context).delete(client.namespace, deleteOptions)

                    l.await(60000, TimeUnit.MILLISECONDS)

                    log.info { "Removed objects (${(deletedObjects["items"] as Collection<*>).size}): $deletedObjects" }
                }
            }
        } catch (e: KubernetesClientException) {
            log.warn(e) { "Removing objects failed for context $context" }
        }
    }
}

fun Koperator<*>.uninstall(client: KubernetesClient) {

    reset(client)

    val log = KotlinLogging.logger {}
    val kindsToRemove = this.resources.map { krd ->
        val g = KrdDefinition(krd)
        g.resourceDefinition.kind
    }.toSet()

    try {
        val definitions = client.customResourceDefinitions().list()
                .items
                .filter { it.spec.names.kind in kindsToRemove }

        if (definitions.isNotEmpty()) {
            val l = CountDownLatch(definitions.size)
            val names = definitions.map { definition -> definition.metadata.name }.toSet()
            client.customResourceDefinitions().watch(
                    object : Watcher<CustomResourceDefinition> {
                        override fun onClose(cause: KubernetesClientException?) {
                            if (cause != null) {
                                log.warn(cause) { "Problems while removing CRD with names=$names, definitions=$definitions" }
                            }
                        }

                        override fun eventReceived(action: Watcher.Action, resource: CustomResourceDefinition) {
                            if (resource.metadata.name in names) {
                                when (action) {
                                    Watcher.Action.ADDED, Watcher.Action.MODIFIED, Watcher.Action.ERROR ->
                                        log.warn { "Got unexpected action=$action on watch for CRD deletion: $resource" }
                                    Watcher.Action.DELETED -> {
                                        log.info { "CRD deletion confirmed: $resource" }
                                        l.countDown()
                                    }
                                }
                            }

                        }
                    }
            ).use {
                definitions.forEach {
                    client.customResourceDefinitions()
                            .withName(it.metadata.name)
                            .withPropagationPolicy(DeletionPropagation.FOREGROUND)
                            .delete()
                }
                l.await(60000, TimeUnit.MILLISECONDS)

                log.info { "Removed definitions (${definitions.size}): $definitions" }
            }
        }
    } catch (e: KubernetesClientException) {
        throw KoperatorException("Uninstalling resources with kinds failed:\n${kindsToRemove}", e)
    }
}

class KoperatorException(message: String, cause: Throwable? = null) : Exception(message, cause)
