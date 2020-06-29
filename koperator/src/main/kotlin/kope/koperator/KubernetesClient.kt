package kope.koperator

import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.utils.Serialization
import kope.krd.fixesModule


fun kubernetesClient(kubernetesContext: String?): KubernetesClient {

    val client = DefaultKubernetesClient(Config.autoConfigure(kubernetesContext))

    Serialization.jsonMapper().registerModule(fixesModule)

    return client
}