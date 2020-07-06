package kope.koperator

import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.utils.Serialization
import kope.krd.fixesModule
import mu.KotlinLogging


fun kubernetesClient(
        kubernetesContext: String? = null,
        namespace: String? = null
): KubernetesClient {
    val log = KotlinLogging.logger { }

    val context = kubernetesContext ?: System.getenv("CONTEXT")

    log.info { "Creating Kubernetes client: context=$context, namespace=$namespace" }

    val client = DefaultKubernetesClient(Config.autoConfigure(context))
            .inNamespace(namespace)

    Serialization.jsonMapper().registerModule(fixesModule)

    return client
}