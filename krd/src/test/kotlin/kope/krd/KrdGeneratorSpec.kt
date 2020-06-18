package kope.krd

import assertk.Assert
import assertk.all
import assertk.assertThat
import assertk.assertions.*
import assertk.assertions.support.expected
import com.fasterxml.jackson.databind.JsonNode
import org.spekframework.spek2.Spek
import org.spekframework.spek2.lifecycle.CachingMode
import org.spekframework.spek2.style.specification.describe

const val resourceName = "krd.model"
const val kind = "TestModel"
const val singularName = "simpleTest"
const val pluralName = "simpleTests"
const val group = "krd"
const val version = "v1"

val scope = Scope.NAMESPACED.value

object KrdGeneratorSpec : Spek({

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
                    override val name: String
            ) : Krd

            val obj by memoized(CachingMode.SCOPE) {
                yaml().readTree(
                        KrdGenerator(TestSimple::class).yaml.also { println(it) }
                )
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
                    at("/integer/type").string().isEqualTo("integer")
                    at("/integer/nullable").boolean().isFalse()
                    at("/string/type").string().isEqualTo("string")
                    at("/string/nullable").boolean().isFalse()
                    at("/long/type").string().isEqualTo("integer")
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
                    @IntegerDefinition(1, 10)
                    val integer: Int,
                    override val name: String
            ) : Krd

            val obj by memoized(CachingMode.SCOPE) {
                yaml().readTree(
                        KrdGenerator(TestSimple::class).yaml.also { println(it) }
                )
            }

            it("should define fields properly") {
                assertThat(obj.at("/spec/versions/0/schema/openAPIV3Schema/properties")).all {
                    at("/integer").all {
                        isNotMissing()
                        at("/type").string().isEqualTo("integer")
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
                    override val name: String
            ) : Krd

            val obj by memoized(CachingMode.SCOPE) {
                yaml().readTree(
                        KrdGenerator(TestSimple::class).yaml.also { println(it) }
                )
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
                    override val name: String
            ) : Krd

            val obj by memoized(CachingMode.SCOPE) {
                yaml().readTree(
                        KrdGenerator(TestSimple::class).yaml.also { println(it) }
                )
            }

            it("should define fields properly") {
                assertThat(obj.at("/spec/versions/0/schema/openAPIV3Schema/properties")).all {
                    at("/integer").all {
                        isNotMissing()
                        at("/type").string().isEqualTo("integer")
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
                    override val name: String
            ) : Krd

            val obj by memoized(CachingMode.SCOPE) {
                yaml().readTree(
                        KrdGenerator(TestSimple::class).yaml.also { println(it) }
                )
            }

            it("should define fields properly") {
                assertThat(obj.at("/spec/versions/0/schema/openAPIV3Schema/properties")).all {
                    at("/myInt").all {
                        isNotMissing()
                        at("/type").string().isEqualTo("integer")
                        at("/description").string().isEqualTo("My integer can be from 1 to 10")
                    }
                }
            }
        }

        describe("Nested objects") {

            data class NestedObject(
                    @IntegerDefinition(1, 10)
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
                    override val name: String
            ) : Krd

            val obj by memoized(CachingMode.SCOPE) {
                yaml().readTree(
                        KrdGenerator(TestSimple::class).yaml.also { println(it) }
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
                                at("/type").string().isEqualTo("integer")
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
                                at("/type").string().isEqualTo("integer")
                                at("/minimum").double().isEqualTo(1.0)
                                at("/maximum").double().isEqualTo(10.0)
                            }
                        }
                    }
                }
            }
        }
    }

    describe("Differences for apiextensions.k8s.io/v1beta1") {

        describe("validation is on top level") {

            @ResourceDefinition(
                    name = resourceName,
                    kind = kind,
                    singularName = singularName,
                    pluralName = pluralName,
                    group = group,
                    version = version,
                    preserveUnknownFields = true,
                    apiVersion = "apiextensions.k8s.io/v1beta1"
            )
            data class TestSimple(
                    val integer: Int,
                    val string: String,
                    val long: Long,
                    @Ignore
                    val ignore: String,
                    override val name: String
            ) : Krd

            val obj by memoized(CachingMode.SCOPE) {
                yaml().readTree(
                        KrdGenerator(TestSimple::class).yaml.also { println(it) }
                )
            }

            it("should define basics properly") {
                assertThat(obj).all {
                    at("/apiVersion").string().isEqualTo("apiextensions.k8s.io/v1beta1")
                    at("/kind").string().isEqualTo("CustomResourceDefinition")
                    at("/metadata/name").string().isEqualTo(resourceName)
                    at("/spec/group").string().isEqualTo(group)
                    at("/spec/preserveUnknownFields").boolean().isTrue()
                    at("/spec/names/kind").string().isEqualTo(kind)
                    at("/spec/names/plural").string().isEqualTo(pluralName)
                    at("/spec/names/singular").string().isEqualTo(singularName)
                    at("/spec/scope").string().isEqualTo(scope)
                    at("/spec/versions/0/name").isNotMissing().string().isEqualTo(version)
                    at("/spec/versions/0/storage").isNotMissing().boolean().isTrue()
                    at("/spec/versions/0/served").isNotMissing().boolean().isTrue()
                    at("/spec/versions/0/schema/openAPIV3Schema/properties").isMissing()
                    at("/spec/validation/openAPIV3Schema/properties").isNotMissing()
                }
            }

            it("should define fields properly") {
                assertThat(obj.at("/spec/validation/openAPIV3Schema/properties")).all {
                    at("/integer/type").string().isEqualTo("integer")
                    at("/integer/nullable").boolean().isFalse()
                    at("/string/type").string().isEqualTo("string")
                    at("/string/nullable").boolean().isFalse()
                    at("/long/type").string().isEqualTo("integer")
                    at("/long/nullable").boolean().isFalse()
                    at("/ignore").isMissing()
                }
            }
        }
    }
})

private fun Assert<JsonNode>.isMissing() = transform { if (!it.isMissingNode) expected("to be missing") else it }

private fun Assert<JsonNode>.isNotMissing(): Assert<JsonNode> = transform { if (it.isMissingNode) expected("to be not missing") else it }

private fun Assert<JsonNode>.at(path: String): Assert<JsonNode> {
    return prop("[$path]") { it.at(path) }
}

private fun Assert<JsonNode>.string(): Assert<String> {
    return prop("asString") { it.textValue() }
}

private fun Assert<JsonNode>.double(): Assert<Double> {
    return prop("asDouble") { it.doubleValue() }
}

private fun Assert<JsonNode>.long(): Assert<Long> {
    return prop("asLong") { it.longValue() }
}

private fun Assert<JsonNode>.boolean(): Assert<Boolean> {
    return prop("asBoolean") { it.booleanValue() }
}

