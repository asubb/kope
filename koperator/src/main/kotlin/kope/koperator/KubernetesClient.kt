package kope.koperator

import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.utils.Serialization
import kope.krd.fixesModule


fun kubernetesClient(kubernetesContext: String? = null): KubernetesClient {

    val context = kubernetesContext ?: System.getenv("CONTEXT")

    val client = DefaultKubernetesClient(Config.autoConfigure(context))

    Serialization.jsonMapper().registerModule(fixesModule)

    return client
}