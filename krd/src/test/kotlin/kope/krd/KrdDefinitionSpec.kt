package kope.krd

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.spekframework.spek2.Spek
import org.spekframework.spek2.lifecycle.CachingMode
import org.spekframework.spek2.style.specification.describe

private const val resourceName = "krd.model"
private const val kind = "TestModel"
private const val singularName = "simpleTest"
private const val pluralName = "simpleTests"
private const val group = "krd"
private const val version = "v1"

private val scope = Scope.NAMESPACED.value

object KrdDefinitionSpec : Spek({

    describe("Default apiVersion=apiextensions.k8s.io/v1") {

        describe("Simple Model without property definitions") {

            @ResourceDefinition(
                    name = resourceName,
                    kind = kind,
                    singularName = singularName,
                    pluralName = pluralName,
                    group = group,
                    version = version,
                    preserveUnknownFields = true
            )
            data class TestSimple(
                    val integer: Int,
                    val string: String,
                    val long: Long,
                    @Ignore
                    val ignore: String,
                    override val metadata: Metadata
            ) : Krd

            val obj by memoized(CachingMode.SCOPE) {
                yaml().readTree(KrdDefinition(TestSimple::class).yaml.also { println(it) })
            }

            it("should define basics properly") {
                assertThat(obj).all {
                    at("/apiVersion").string().isEqualTo("apiextensions.k8s.io/v1")
                    at("/kind").string().isEqualTo("CustomResourceDefinition")
                    at("/metadata/name").string().isEqualTo(resourceName)
                    at("/spec/group").string().isEqualTo(group)
                    at("/spec/preserveUnknownFields").boolean().isTrue()
                    at("/spec/names/kind").string().isEqualTo(kind)
                    at("/spec/names/plural").string().isEqualTo(pluralName)
                    at("/spec/names/singular").string().isEqualTo(singularName)
                    at("/spec/scope").string().isEqualTo(scope)
                    at("/spec/versions/0/name").string().isEqualTo(version)
                    at("/spec/versions/0/storage").isNotMissing().boolean().isTrue()
                    at("/spec/versions/0/served").isNotMissing().boolean().isTrue()
                    at("/spec/versions/0/schema/openAPIV3Schema/properties").isNotMissing()
                    at("/spec/versions/0/schema/openAPIV3Schema/nullable").isMissing()
                }
            }

            it("should define fields properly") {
                assertThat(obj.at("/spec/versions/0/schema/openAPIV3Schema/properties")).all {
                    at("/name").isMissing()
                    at("/integer/type").string().isEqualTo("number")
                    at("/integer/nullable").boolean().isFalse()
                    at("/string/type").string().isEqualTo("string")
                    at("/string/nullable").boolean().isFalse()
                    at("/long/type").string().isEqualTo("number")
                    at("/long/nullable").boolean().isFalse()
                    at("/ignore").isMissing()
                }
            }
        }

        describe("Simple Model with property definitions for integers") {

            @ResourceDefinition(
                    name = resourceName,
                    kind = kind,
                    singularName = singularName,
                    pluralName = pluralName,
                    group = group,
                    version = version
            )
            data class TestSimple(
                    @NumberDefinition(1.0, 10.0)
                    val integer: Int,
                    override val metadata: Metadata
            ) : Krd

            val obj by memoized(CachingMode.SCOPE) {
                yaml().readTree(KrdDefinition(TestSimple::class).yaml.also { println(it) })
            }

            it("should define fields properly") {
                assertThat(obj.at("/spec/versions/0/schema/openAPIV3Schema/properties")).all {
                    at("/integer").all {
                        isNotMissing()
                        at("/type").string().isEqualTo("number")
                        at("/minimum").double().isEqualTo(1.0)
                        at("/maximum").double().isEqualTo(10.0)
                    }
                }
            }
        }

        describe("Simple Model with property definitions for string") {

            @ResourceDefinition(
                    name = resourceName,
                    kind = kind,
                    singularName = singularName,
                    pluralName = pluralName,
                    group = group,
                    version = version
            )
            data class TestSimple(
                    @StringDefinition(
                            format = "some-format",
                            minLength = 1,
                            maxLength = 10,
                            pattern = "regex-pattern"
                    )
                    val s: String,
                    override val metadata: Metadata
            ) : Krd

            val obj by memoized(CachingMode.SCOPE) {
                yaml().readTree(KrdDefinition(TestSimple::class).yaml.also { println(it) })
            }

            it("should define fields properly") {
                assertThat(obj.at("/spec/versions/0/schema/openAPIV3Schema/properties")).all {
                    at("/s").all {
                        isNotMissing()
                        at("/format").string().isEqualTo("some-format")
                        at("/pattern").string().isEqualTo("regex-pattern")
                        at("/minLength").long().isEqualTo(1L)
                        at("/maxLength").long().isEqualTo(10L)
                    }
                }
            }
        }

        describe("Simple Model with property definitions for nullable primitives") {

            @ResourceDefinition(
                    name = resourceName,
                    kind = kind,
                    singularName = singularName,
                    pluralName = pluralName,
                    group = group,
                    version = version
            )
            data class TestSimple(
                    val integer: Int?,
                    val string: String?,
                    override val metadata: Metadata
            ) : Krd

            val obj by memoized(CachingMode.SCOPE) {
                yaml().readTree(KrdDefinition(TestSimple::class).yaml.also { println(it) })
            }

            it("should define fields properly") {
                assertThat(obj.at("/spec/versions/0/schema/openAPIV3Schema/properties")).all {
                    at("/integer").all {
                        isNotMissing()
                        at("/type").string().isEqualTo("number")
                        at("/nullable").boolean().isTrue()
                    }
                    at("/string").all {
                        isNotMissing()
                        at("/type").string().isEqualTo("string")
                        at("/nullable").boolean().isTrue()
                    }
                }
            }
        }

        describe("Simple Model with property definitions") {

            @ResourceDefinition(
                    name = resourceName,
                    kind = kind,
                    singularName = singularName,
                    pluralName = pluralName,
                    group = group,
                    version = version
            )
            data class TestSimple(
                    @PropertyDefinition(
                            name = "myInt",
                            description = "My integer can be from 1 to 10"
                    )
                    val integer: Int,
                    override val metadata: Metadata
            ) : Krd

            val obj by memoized(CachingMode.SCOPE) {
                yaml().readTree(KrdDefinition(TestSimple::class).yaml.also { println(it) })
            }

            it("should define fields properly") {
                assertThat(obj.at("/spec/versions/0/schema/openAPIV3Schema/properties")).all {
                    at("/myInt").all {
                        isNotMissing()
                        at("/type").string().isEqualTo("number")
                        at("/description").string().isEqualTo("My integer can be from 1 to 10")
                    }
                }
            }
        }

        describe("Nested objects") {

            data class NestedObject(
                    @NumberDefinition(1.0, 10.0)
                    val integer: Int
            )

            @ResourceDefinition(
                    name = resourceName,
                    kind = kind,
                    singularName = singularName,
                    pluralName = pluralName,
                    group = group,
                    version = version
            )
            data class TestSimple(
                    @PropertyDefinition(name = "nested")
                    val nestedObject: NestedObject,
                    val nullableNestedObject: NestedObject?,
                    override val metadata: Metadata
            ) : Krd

            val obj by memoized(CachingMode.SCOPE) {
                yaml().readTree(
                        KrdDefinition(TestSimple::class).yaml.also { println(it) }
                )
            }

            it("should define fields properly") {
                assertThat(obj.at("/spec/versions/0/schema/openAPIV3Schema/properties")).all {
                    at("/nested").all {
                        isNotMissing()
                        at("/type").string().isEqualTo("object")
                        at("/description").isMissing()
                        at("/properties").all {
                            isNotMissing()
                            at("/integer").all {
                                isNotMissing()
                                at("/type").string().isEqualTo("number")
                                at("/minimum").double().isEqualTo(1.0)
                                at("/maximum").double().isEqualTo(10.0)
                            }
                        }
                    }
                    at("/nullableNestedObject").all {
                        isNotMissing()
                        at("/type").string().isEqualTo("object")
                        at("/nullable").boolean().isTrue()
                        at("/description").isMissing()
                        at("/properties").all {
                            isNotMissing()
                            at("/integer").all {
                                isNotMissing()
                                at("/type").string().isEqualTo("number")
                                at("/minimum").double().isEqualTo(1.0)
                                at("/maximum").double().isEqualTo(10.0)
                            }
                        }
                    }
                }
            }
        }

        describe("Iterables") {

            data class NeedToGoDeeper(
                    @PropertyDefinition(name = "bigA")
                    val a: Int,
                    @PropertyDefinition(name = "bigB")
                    val b: String,
                    @PropertyDefinition(name = "bigC")
                    val c: Float
            )

            data class NestedObject(
                    @PropertyDefinition("myList", description = "My precious list")
                    val list: List<Int>,
                    val set: Set<NeedToGoDeeper>
            )

            @ResourceDefinition(
                    name = resourceName,
                    kind = kind,
                    singularName = singularName,
                    pluralName = pluralName,
                    group = group,
                    version = version
            )
            data class TestSimple(
                    override val metadata: Metadata,
                    @PropertyDefinition(description = "list of strings")
                    val listOfString: List<String>,
                    val nestedObjects: List<NestedObject>
            ) : Krd

            val obj by memoized(CachingMode.SCOPE) {
                yaml().readTree(KrdDefinition(TestSimple::class).yaml.also { println(it) })
            }

            it("should define fields properly") {
                assertThat(obj.at("/spec/versions/0/schema/openAPIV3Schema/properties")).all {
                    at("/listOfString").all {
                        at("/type").string().isEqualTo("array")
                        at("/description").string().isEqualTo("list of strings")
                        at("/items").all {
                            at("/type").string().isEqualTo("string")
                        }
                    }
                    at("/nestedObjects").all {
                        at("/type").string().isEqualTo("array")
                        at("/items/type").string().isEqualTo("object")
                        at("/items/properties/myList/type").string().isEqualTo("array")
                        at("/items/properties/myList/description").string().isEqualTo("My precious list")
                        at("/items/properties/myList/items/type").string().isEqualTo("number")
                        at("/items/properties/set").all {
                            at("/type").string().isEqualTo("array")
                            at("/items/type").string().isEqualTo("object")
                            at("/items/properties/bigA/type").string().isEqualTo("number")
                            at("/items/properties/bigB/type").string().isEqualTo("string")
                            at("/items/properties/bigC/type").string().isEqualTo("number")
                        }
                    }
                }
            }
        }

        describe("Maps") {

            data class NestedObject(
                    @PropertyDefinition(name = "bigA")
                    val a: Int,
                    @PropertyDefinition(name = "bigB")
                    val b: String,
                    @PropertyDefinition(name = "bigC")
                    val c: Float
            )

            @ResourceDefinition(
                    name = resourceName,
                    kind = kind,
                    singularName = singularName,
                    pluralName = pluralName,
                    group = group,
                    version = version
            )
            data class TestSimple(
                    override val metadata: Metadata,
                    @PropertyDefinition(description = "mapStringInt")
                    val simpleMap: Map<String, Int>,
                    val nestedObjectsAsValues: Map<String, NestedObject>,
                    @PropertyDefinition(name = "map")
                    val nestedMap: Map<String, Map<String, Float>>
            ) : Krd

            val obj by memoized(CachingMode.SCOPE) {
                yaml().readTree(KrdDefinition(TestSimple::class).yaml.also { println(it) })
            }

            it("should define fields properly") {
                assertThat(obj.at("/spec/versions/0/schema/openAPIV3Schema/properties")).all {
                    at("/simpleMap").all {
                        at("/type").string().isEqualTo("object")
                        at("/additionalProperties").boolean().isEqualTo(true)
                    }
                    at("/nestedObjectsAsValues").all {
                        at("/type").string().isEqualTo("object")
                        at("/additionalProperties").boolean().isEqualTo(true)
                    }
                    at("/map").all {
                        at("/type").string().isEqualTo("object")
                        at("/additionalProperties").boolean().isEqualTo(true)
                    }
                }
            }
        }
    }
})

