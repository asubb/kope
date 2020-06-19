package kope.koperator

import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.fabric8.kubernetes.client.KubernetesClient
import kope.krd.Krd
import kope.krd.PropertyDefinition
import kope.krd.ResourceDefinition
import kope.krd.Scope
import kotlin.reflect.KClass

@ResourceDefinition(
        name = "tests.example.com",
        kind = "Test",
        version = "v1alpha",
        group = "example.com",
        singularName = "test",
        pluralName = "tests",
        apiVersion = "apiextensions.k8s.io/v1beta1",
        scope = Scope.CLUSTER
)
data class MyTest(
        override val metadata: kope.krd.Metadata,
        @PropertyDefinition(name = "testField")
        val field: String
) : Krd

class MyKoperator(
        override val resources: List<KClass<out Krd>>,
        override val kontroller: MyKontroller
) : Koperator<MyKontroller>

class MyKontroller(override val client: KubernetesClient) : Kontroller {

    fun createInitialResources(res: MyTest) {
        client.create(res)
    }
}

fun main() {
    val client = DefaultKubernetesClient()
    val koperator = MyKoperator(
        resources = listOf(MyTest::class),
        kontroller = MyKontroller(client)
    )
    koperator.install(client)
    koperator.kontroller.createInitialResources(MyTest(kope.krd.Metadata("my-new-test"), "234"))
}