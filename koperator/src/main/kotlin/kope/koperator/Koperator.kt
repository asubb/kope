package kope.koperator

import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext
import kope.krd.Krd
import kope.krd.KrdDefinition
import mu.KotlinLogging
import java.io.Closeable
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

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
    this.resources.forEach { krd ->
        val g = KrdDefinition(krd)
        log.info { "Installing resource $krd with definition:\n${g.yaml}" }
        try {
            client.customResourceDefinitions().createOrReplace(g.definition)
        } catch (e: KubernetesClientException) {
            throw KoperatorException("\"Installing resource $krd with definition failed:\\n${g.yaml}\"", e)
        }
    }
}

fun Koperator<*>.uninstall(client: KubernetesClient) {
    val log = KotlinLogging.logger {}
    val kindsToRemove = this.resources.map { krd ->
        val g = KrdDefinition(krd)
        g.resourceDefinition.kind
    }.toSet()

    try {
        log.info { "Uninstalling resources with kinds:\n${kindsToRemove}" }
        val definitions = client.customResourceDefinitions().list()
                .items
                .filter { it.spec.names.kind in kindsToRemove }

        definitions.forEach { definition ->
            val context = CustomResourceDefinitionContext.fromCrd(definition)
            try {
                val deletedObjects = client.customResource(context).delete(null)
                log.info { "Removed objects: $deletedObjects" }
            } catch (e: KubernetesClientException) {
                log.warn(e) { "Removing objects failed for context $context" }
            }
        }

        client.customResourceDefinitions().delete(definitions)
        log.info { "Removed objects: $definitions" }
    } catch (e: KubernetesClientException) {
        throw KoperatorException("Uninstalling resources with kinds failed:\n${kindsToRemove}", e)
    }

}

class KoperatorException(message: String, cause: Throwable? = null) : Exception(message, cause)
