package kope.koperator

import io.fabric8.kubernetes.client.KubernetesClient

interface Kontroller {

    val client: KubernetesClient

    fun initialize() {}

    fun main()

    fun tearDown() {}
}