package kope.koperator

import io.fabric8.kubernetes.client.KubernetesClient
import kope.koperator.Action.*
import mu.KotlinLogging
import kotlin.reflect.KClass
import kotlin.reflect.typeOf

enum class Action(val flag: String) {
    INSTALL("install"),
    UNINSTALL("uninstall"),
    RESET("reset"),
    RUN("") // default action, is being used if nothing else is specified
}

fun main(args: Array<String>) {
    val log = KotlinLogging.logger {}

    log.info {
        "Starting up Koperator with parameters: ${args.toList()}, environment variables:\n" +
                System.getenv().entries.joinToString("\n") { "${it.key}: ${it.value}" }
    }

    lateinit var koperatorClass: KClass<Koperator<*>>
    var action = RUN
    var kubernetesContext: String? = null
    var namespace: String? = null

    val handlers = mapOf<String, (String?) -> Unit>(
            "class" to { arg ->
                require(arg != null) { "Parameter --class requires to have a value." }
                @Suppress("UNCHECKED_CAST")
                koperatorClass = (Thread.currentThread().contextClassLoader.loadClass(arg) as Class<Koperator<*>>).kotlin
                log.info { "Found Koperator class $koperatorClass" }
            },
            "context" to { arg ->
                require(arg != null) { "Parameter --profile requires to have a value." }
                kubernetesContext = arg
            },
            "namespace" to { arg ->
                require(arg != null) { "Parameter --namespace requires to have a value." }
                namespace = arg
            }
    ) + Action.values().map { actionItem ->
        actionItem.flag to { _: String? -> action = actionItem }
    }

    args.forEach { arg ->
        val v = arg.split('=', limit = 2)
        val key = v[0].trimStart('-')
        val handler = handlers[key] ?: throw IllegalArgumentException("$key is not recognized")
        handler(if (v.size > 1) v[1] else null)
    }

    kubernetesClient(kubernetesContext, namespace).use { client ->
        log.info { "Kubernetes client created $client" }

        val koperator = koperatorClass.constructors
                .firstOrNull { it.parameters.size == 1 && it.parameters[0].type == typeOf<KubernetesClient>() }
                ?.call(client)
                ?: throw IllegalStateException("Can't find constructor of $koperatorClass with ${KubernetesClient::class} as the only parameter")
        log.info { "[$koperator] Koperator instantiated" }

        Launcher(koperator, action, client).run()
    }
}

class Launcher(
        private val koperator: Koperator<*>,
        private val action: Action,
        private val client: KubernetesClient
) : Runnable {

    private val log = KotlinLogging.logger { }

    override fun run() {
        log.info { "[$koperator] Performing koperator action $action" }
        when (action) {
            INSTALL -> {
                koperator.install(client)
                log.info { "[$koperator] Operator installation performed" }
            }
            UNINSTALL -> {
                koperator.uninstall(client)
                log.info { "[$koperator] Operator uninstall performed" }
            }
            RESET -> {
                koperator.reset(client)
                log.info { "[$koperator] Operator reset performed" }
            }
            RUN -> {
                log.info { "[$koperator] Koperator initializing..." }
                koperator.initialize()
                log.info { "[$koperator] Koperator initialized" }

                log.info { "[$koperator] Koperator awaiting to finish..." }
                koperator.await().get()
                log.info { "[$koperator] Koperator future resolved. Ending" }

                log.info { "[$koperator] Koperator tearing down..." }
                koperator.tearDown()
                log.info { "[$koperator] Koperator torn down" }
                koperator.close()
            }
        }
    }
}

