package kope.koperator

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import assertk.assertions.prop
import com.nhaarman.mockitokotlin2.mock
import io.fabric8.kubernetes.client.KubernetesClient
import kope.krd.Krd
import kope.krd.Metadata
import kope.krd.ResourceDefinition
import kope.krd.Scope
import org.spekframework.spek2.Spek
import org.spekframework.spek2.lifecycle.CachingMode.SCOPE
import org.spekframework.spek2.style.specification.describe
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass

object KoperatorSpec : Spek({

    describe("Lifecycle of the koperator and kontroller while running") {

        val koperatorInitialized = AtomicInteger(0)
        val koperatorTornDown = AtomicInteger(0)
        val kontrollerInitialized = AtomicInteger(0)
        val kontrollerRunMainLoop = AtomicInteger(0)
        val kontrollerTornDown = AtomicInteger(0)

        class MyKontroller(override val client: KubernetesClient) : Kontroller {

            override fun main() {
                kontrollerRunMainLoop.incrementAndGet()
            }

            override fun initialize() {
                kontrollerInitialized.incrementAndGet()
                super.initialize()
            }

            override fun tearDown() {
                kontrollerTornDown.incrementAndGet()
                super.tearDown()
            }
        }

        val client = mock<KubernetesClient>()

        val kontroller = MyKontroller(client) // can't inline the instantiation due to Kotlin bug https://youtrack.jetbrains.com/issue/KT-8120

        @Suppress("UNUSED_PARAMETER") // required to have that constructor
        class MyKoperator(client: KubernetesClient) : Koperator<MyKontroller>() {

            override val resources: List<KClass<out Krd>> = emptyList()

            override val kontroller: MyKontroller = kontroller


            override fun initialize() {
                koperatorInitialized.incrementAndGet()
                super.initialize()
            }

            override fun tearDown() {
                koperatorTornDown.incrementAndGet()
                super.tearDown()
            }
        }

        val launcher = Launcher(MyKoperator(client), Action.RUN, client)

        beforeGroup {
            launcher.use {
                it.run()
            }
        }

        it("should initialize the koperator") { assertThat(koperatorInitialized.get()).isEqualTo(1) }
        it("should tear down the koperator") { assertThat(koperatorTornDown.get()).isEqualTo(1) }
        it("should initialize the kontroller") { assertThat(kontrollerInitialized.get()).isEqualTo(1) }
        it("should run the main loop of the kontroller") { assertThat(kontrollerRunMainLoop.get()).isEqualTo(1) }
        it("should tear down the kontroller") { assertThat(kontrollerTornDown.get()).isEqualTo(1) }
    }

    describe("CRD Install") {

        @ResourceDefinition(
                name = "my-objects.test.kope.internal",
                kind = "MyObject",
                version = "v1",
                group = "test.kope.internal",
                singularName = "my-object",
                pluralName = "my-objects",
                apiVersion = "apiextensions.k8s.io/v1beta1",
                scope = Scope.CLUSTER
        )
        data class MyObject(
                override val metadata: Metadata,
                val myField: Int
        ) : Krd

        class MyKontroller(override val client: KubernetesClient) : Kontroller {
            override fun main(): Unit = throw UnsupportedOperationException("Why are you calling this?")
        }

        class MyKoperator(client: KubernetesClient) : Koperator<MyKontroller>() {
            override val resources: List<KClass<out Krd>> = listOf(MyObject::class)
            override val kontroller: MyKontroller = MyKontroller(client)
        }

        val client by memoized(SCOPE) {
            require(System.getenv("CONTEXT") != null) {
                "This test relies on being launched with specific k8s context. Set CONTEXT env var"
            }
            kubernetesClient()
        }

        beforeGroup { Launcher(MyKoperator(client), Action.INSTALL, client).use { it.run() } }

        afterGroup { Launcher(MyKoperator(client), Action.UNINSTALL, client).use { it.run() } }

        it("should be presented on the cluster") {
            val crd = client.customResourceDefinitions().withName("my-objects.test.kope.internal").get()
            assertThat(crd).isNotNull().all {
                prop("apiVersion") { it.apiVersion }.isEqualTo("apiextensions.k8s.io/v1beta1")
                prop("spec.names.kind") { it.spec.names.kind }.isEqualTo("MyObject")
                prop("spec.names.singular") { it.spec.names.singular }.isEqualTo("my-object")
                prop("spec.names.plural") { it.spec.names.plural }.isEqualTo("my-objects")
                prop("spec.version") { it.spec.version }.isEqualTo("v1")
                prop("spec.group") { it.spec.group }.isEqualTo("test.kope.internal")
                prop("spec.versions[0].served") { it.spec.versions[0].served }.isTrue()
                prop("spec.versions[0].storage") { it.spec.versions[0].storage }.isTrue()
            }
        }
    }
})