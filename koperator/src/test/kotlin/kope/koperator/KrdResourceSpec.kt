package kope.koperator

import assertk.Assert
import assertk.all
import assertk.assertThat
import assertk.assertions.*
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext
import kope.krd.Krd
import kope.krd.Metadata
import kope.krd.ResourceDefinition
import kope.krd.Scope
import org.spekframework.spek2.Spek
import org.spekframework.spek2.lifecycle.CachingMode
import org.spekframework.spek2.style.specification.describe
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

// run tests as a group only, order matters
object KrdResourceSpec : Spek({

    @ResourceDefinition(
            name = "myobjects.test.kope.internal",
            kind = "MyObject",
            version = "v1",
            group = "test.kope.internal",
            singularName = "myobject",
            pluralName = "myobjects",
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

    val client by memoized(CachingMode.SCOPE) {
        require(System.getenv("CONTEXT") != null) {
            "This test relies on being launched with specific k8s context. Set CONTEXT env var"
        }
        kubernetesClient()
    }

    beforeEachGroup {
        Launcher(MyKoperator(client), Action.INSTALL, client).use { it.run() }
    }

    afterEachGroup {
        Launcher(MyKoperator(client), Action.UNINSTALL, client).use { it.run() }
    }

    val krd by memoized(CachingMode.SCOPE) { client.krd<MyObject>() }

    describe("Create") {
        it("should create object with no errors") {
            val id = "myobject1"
            krd.create(MyObject(Metadata(id), 1))

            val storedObj = client.customResource(CustomResourceDefinitionContext.fromCrd(krd.krdDef.definition)).get(id)

            assertThat(storedObj).isNotNull().all {
                m("metadata").o("name").isEqualTo(id)
                o("kind").isEqualTo("MyObject")
                o("myField").isEqualTo(1)
            }
        }
    }

    describe("Create or replace") {
        it("should create object with no errors") {
            val id = "myobject1"
            krd.create(MyObject(Metadata(id), 1))
            krd.createOrReplace(MyObject(Metadata(id), 2))

            val storedObj = client.customResource(CustomResourceDefinitionContext.fromCrd(krd.krdDef.definition)).get(id)

            assertThat(storedObj).isNotNull().all {
                m("metadata").o("name").isEqualTo(id)
                o("kind").isEqualTo("MyObject")
                o("myField").isEqualTo(2)
            }
        }
    }

    describe("Get") {
        val obj1 = MyObject(Metadata("myobject1"), 1)
        it("should return nothing") {
            assertThat(krd.get("non-existing")).isNull()
        }
        it("should return element") {
            krd.create(obj1)
            assertThat(krd.get("myobject1")).isEqualTo(obj1)
        }
    }

    describe("Delete") {
        val obj1 = MyObject(Metadata("myobject1"), 1)
        it("should remove element") {
            krd.create(obj1)
            assertThat(krd.delete(obj1)).isTrue()
        }
        it("should not remove element once again") {
            assertThat(krd.delete(obj1)).isFalse()
        }
    }

    describe("List") {
        val obj1 = MyObject(Metadata("myobject1"), 1)
        val obj2 = MyObject(Metadata("myobject2"), 2)
        it("should return empty list") { assertThat(krd.list()).isEmpty() }
        it("should return one element list") {
            krd.create(obj1)
            assertThat(krd.list()).isEqualTo(listOf(obj1))
        }
        it("should return two element list") {
            krd.create(obj2)
            assertThat(krd.list()).isEqualTo(listOf(obj1, obj2))
        }
    }

    describe("Watch") {

        describe("Globally") {
            var lastActionFired: Watcher.Action? = null
            var lastObject: MyObject? = null
            var lastError: Exception? = null
            var l: CountDownLatch? = null
            beforeGroup {
                krd.watch()
                        .onAction { action, obj ->
                            if (l != null) l!!.countDown()
                            lastActionFired = action
                            lastObject = obj
                        }
                        .onClose { lastError = it }
            }

            val obj1 = MyObject(Metadata("myobject1"), 1)
            val newObj1 = obj1.copy(myField = 2)
            it("should not fire anything") {
                assertThat(lastActionFired).isNull()
                assertThat(lastObject).isNull()
                assertThat(lastError).isNull()
            }
            it("should fire ADDED") {
                l = CountDownLatch(1)
                krd.create(obj1)
                l!!.await(1000, TimeUnit.MILLISECONDS)
                l = null
                assertThat(lastActionFired).isNotNull().isEqualTo(Watcher.Action.ADDED)
                assertThat(lastObject).isNotNull().isEqualTo(obj1)
                assertThat(lastError).isNull()
            }
            it("should fire MODIFIED") {
                l = CountDownLatch(1)
                krd.createOrReplace(newObj1)
                l!!.await(1000, TimeUnit.MILLISECONDS)
                l = null
                assertThat(lastActionFired).isNotNull().isEqualTo(Watcher.Action.MODIFIED)
                assertThat(lastObject).isNotNull().isEqualTo(newObj1)
                assertThat(lastError).isNull()
            }
            it("should fire DELETED") {
                l = CountDownLatch(1)
                krd.delete(obj1)
                l!!.await(1000, TimeUnit.MILLISECONDS)
                l = null
                assertThat(lastActionFired).isNotNull().isEqualTo(Watcher.Action.DELETED)
                assertThat(lastObject).isNotNull().isEqualTo(newObj1)
                assertThat(lastError).isNull()
            }
        }

        describe("By name") {
            var lastActionFired: Watcher.Action? = null
            var lastObject: MyObject? = null
            var lastError: Exception? = null
            var l: CountDownLatch? = null
            beforeGroup {
                krd.watch("myobject2")
                        .onAction { action, obj ->
                            if (l != null) l!!.countDown()
                            lastActionFired = action
                            lastObject = obj
                        }
                        .onClose { lastError = it }
            }

            val obj1 = MyObject(Metadata("myobject1"), 1)
            val obj2 = MyObject(Metadata("myobject2"), 2)
            val newObj1 = obj1.copy(myField = 2)
            val newObj2 = obj2.copy(myField = 3)
            it("should not fire anything") {
                assertThat(lastActionFired).isNull()
                assertThat(lastObject).isNull()
                assertThat(lastError).isNull()
            }
            it("should not fire ADDED for obj1") {
                krd.create(obj1)
                assertThat(lastActionFired).isNull()
                assertThat(lastObject).isNull()
                assertThat(lastError).isNull()
            }
            it("should fire ADDED for obj2") {
                l = CountDownLatch(1)
                krd.create(obj2)
                l!!.await(1000, TimeUnit.MILLISECONDS)
                l = null
                assertThat(lastActionFired).isNotNull().isEqualTo(Watcher.Action.ADDED)
                assertThat(lastObject).isNotNull().isEqualTo(obj2)
                assertThat(lastError).isNull()

                lastActionFired = null
                lastObject = null
            }
            it("should not fire MODIFIED for obj1") {
                krd.createOrReplace(newObj1)
                assertThat(lastActionFired).isNull()
                assertThat(lastObject).isNull()
                assertThat(lastError).isNull()
            }
            it("should fire MODIFIED for obj2") {
                l = CountDownLatch(1)
                krd.createOrReplace(newObj2)
                l!!.await(1000, TimeUnit.MILLISECONDS)
                l = null
                assertThat(lastActionFired).isNotNull().isEqualTo(Watcher.Action.MODIFIED)
                assertThat(lastObject).isNotNull().isEqualTo(newObj2)
                assertThat(lastError).isNull()

                lastActionFired = null
                lastObject = null
            }
            it("should not fire DELETED for obj1") {
                krd.delete(obj1)
                assertThat(lastActionFired).isNull()
                assertThat(lastObject).isNull()
                assertThat(lastError).isNull()
            }
            it("should fire DELETED for obj2") {
                krd.delete(obj2)
                assertThat(lastActionFired).isNotNull().isEqualTo(Watcher.Action.DELETED)
                assertThat(lastObject).isNotNull().isEqualTo(newObj2)
                assertThat(lastError).isNull()
            }
        }
    }
})

@Suppress("UNCHECKED_CAST")
private fun Assert<Map<String, Any?>>.m(key: String): Assert<Map<String, Any?>> =
        this.prop("map@$key") { it[key] as Map<String, Any?> }

private fun Assert<Map<String, Any?>>.o(key: String): Assert<Any?> =
        this.prop("object@$key") { it[key] }