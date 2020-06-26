package kope.koperator

import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.fabric8.kubernetes.client.KubernetesClient
import mu.KotlinLogging
import kotlin.reflect.KClass
import kotlin.reflect.typeOf

@ExperimentalStdlibApi
fun main(args: Array<String>) {

    val log = KotlinLogging.logger {}

    log.info {
        "Starting up Koperator with parameters: $args, environment variables:\n" +
                System.getenv().entries.joinToString { "${it.key}: ${it.value}" }
    }

    lateinit var koperatorClass: KClass<Koperator<*>>
    var doInstall = false
    var kubernetesContext: String? = null

    val handlers = mapOf<String, (String?) -> Unit>(
            "class" to { arg ->
                require(arg != null) { "Parameter --class requires to have a value." }
                @Suppress("UNCHECKED_CAST")
                koperatorClass = (Thread.currentThread().contextClassLoader.loadClass(arg) as Class<Koperator<*>>).kotlin
                log.info { "Found Koperator class $koperatorClass" }
            },
            "install" to { _ -> doInstall = true },
            "context" to { arg ->
                require(arg != null) { "Parameter --profile requires to have a value." }
                kubernetesContext = arg
            }
    )

    args.forEach { arg ->
        val v = arg.split('=', limit = 2)
        val key = v[0].trimStart('-')
        val handler = handlers[key] ?: throw IllegalArgumentException("$key is not recognized")
        handler(if (v.size > 1) v[1] else null)
    }

    val client = DefaultKubernetesClient(
            Config.autoConfigure(kubernetesContext)
    )
    log.info { "Kubernetes client created $client" }

    val koperator = koperatorClass.constructors
            .firstOrNull { it.parameters.size == 1 && it.parameters[0].type == typeOf<KubernetesClient>() }
            ?.call(client)
            ?: throw IllegalStateException("Can't find constructor of $koperatorClass with ${KubernetesClient::class} as the onyl parameters")
    log.info { "[$koperator] Koperator instantiated" }

    when {
        doInstall -> {
            log.info { "[$koperator] Performing operator installation..." }

            koperator.install(client)

            log.info { "[$koperator] Operator installation performed" }
        }
        else -> {
            log.info { "[$koperator] Running controller..." }

            Runtime.getRuntime().addShutdownHook(Thread {
                log.info { "[$koperator] Koperator tearing down..." }
                koperator.tearDown()
                log.info { "[$koperator] Koperator torn down" }
            })

            log.info { "[$koperator] Koperator initializing..." }
            koperator.initialize()
            log.info { "[$koperator] Koperator initialized" }

            log.info { "[$koperator] Koperator awaiting to finish..." }
            koperator.await().get()
        }
    }
}