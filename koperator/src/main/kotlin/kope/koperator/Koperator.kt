package kope.koperator

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext
import kope.krd.Krd
import kope.krd.KrdDefinition
import mu.KotlinLogging
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.reflect.KClass

internal fun json(): ObjectMapper = ObjectMapper().registerKotlinModule()

abstract class Koperator<K : Kontroller> {

    protected val pool = Executors.newSingleThreadExecutor()

    abstract val resources: List<KClass<out Krd>>

    abstract val kontroller: K

    open fun initialize() {
        kontroller.initialize()
    }

    open fun await(): Future<Unit> {
        return pool.submit(Callable {
            kontroller.main()
        })
    }

    open fun tearDown() {
        kontroller.tearDown()
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

interface Kontroller {

    val client: KubernetesClient

    fun initialize() {}

    fun main()

    fun tearDown() {}
}

fun KubernetesClient.create(obj: Krd) {
    try {
        val krd = KrdDefinition(obj::class)
        val krdObject = krd.krdObject(obj)

        val resource = customResource(CustomResourceDefinitionContext.fromCrd(krd.definition))
        resource.create(json().writeValueAsString(krdObject.asJsonTree()))
    } catch (e: KubernetesClientException) {
        throw KoperatorException("Can't create $obj", e)
    }
}

class KoperatorException(message: String, cause: Throwable? = null) : Exception(message, cause)
