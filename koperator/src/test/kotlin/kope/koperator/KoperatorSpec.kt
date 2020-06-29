package kope.koperator

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.nhaarman.mockitokotlin2.mock
import io.fabric8.kubernetes.client.KubernetesClient
import kope.krd.Krd
import org.spekframework.spek2.Spek
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
})