package kope.krd

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import org.spekframework.spek2.Spek
import org.spekframework.spek2.lifecycle.CachingMode.SCOPE
import org.spekframework.spek2.style.specification.describe
import java.math.BigDecimal
import java.math.BigInteger

private const val resourceName = "myObject"
private const val kind = "MyObject"
private const val group = "example.com"
private const val singularName = "myObject"
private const val pluralName = "myObjects"
private const val version = "v0.1.2.3"
private const val myObjectName = "myPreciousObject"

object KrdObjectSpec : Spek({
    describe("Complex object") {

        data class InnerObject(
                @PropertyDefinition("myString")
                val string: String,
                @PropertyDefinition("myLong")
                val long: Long,
                @PropertyDefinition("myByteArray")
                val byteArray: ByteArray,
                @PropertyDefinition(description = "description")
                val anotherString: String
        ) {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as InnerObject

                if (string != other.string) return false
                if (long != other.long) return false
                if (!byteArray.contentEquals(other.byteArray)) return false

                return true
            }

            override fun hashCode(): Int {
                var result = string.hashCode()
                result = 31 * result + long.hashCode()
                result = 31 * result + byteArray.contentHashCode()
                return result
            }
        }

        data class NestedObject(
                val string: String,
                val long: Long,
                val int: Int,
                val short: Short,
                val double: Double,
                val float: Float,
                val bigDecimal: BigDecimal,
                val bigInteger: BigInteger,
                val innerObject: InnerObject
        )

        @ResourceDefinition(
                name = resourceName,
                kind = kind,
                singularName = singularName,
                pluralName = pluralName,
                group = group,
                version = version
        )
        data class MyObject(
                override val metadata: Metadata,
                val string: String,
                val long: Long,
                val int: Int,
                val short: Short,
                val double: Double,
                val float: Float,
                val bigDecimal: BigDecimal,
                val bigInteger: BigInteger,
                val nestedObject: NestedObject
        ) : Krd

        val myObject by memoized(SCOPE) {
            MyObject(
                    metadata = Metadata(myObjectName),
                    string = "string0",
                    long = 1L,
                    int = 2,
                    short = 3,
                    double = 4.0,
                    float = 5.0f,
                    bigDecimal = BigDecimal.valueOf(6.0),
                    bigInteger = BigInteger.valueOf(7L),
                    nestedObject = NestedObject(
                            string = "string8",
                            long = 9L,
                            int = 10,
                            short = 11,
                            double = 12.0,
                            float = 13.0f,
                            bigDecimal = BigDecimal.valueOf(14.0),
                            bigInteger = BigInteger.valueOf(15L),
                            innerObject = InnerObject(
                                    string = "string16",
                                    long = 17L,
                                    byteArray = byteArrayOf(18, 19, 20, 21, 22, 23, 24),
                                    anotherString = "25"
                            )
                    )
            )
        }

        val krdDefinition by memoized(SCOPE) { KrdDefinition(MyObject::class) }

        it("should serialize to a tree") {
            val o = krdDefinition.krdObject(myObject)
            assertThat(o.asJsonTree().also { println(it.toPrettyString()) }).all {
                at("/apiVersion").string().isEqualTo("$group/$version")
                at("/kind").string().isEqualTo(kind)
                at("/metadata/name").string().isEqualTo(myObjectName)
                at("/name").isMissing()
                at("/string").string().isEqualTo("string0")
                at("/long").long().isEqualTo(1L)
                at("/int").integer().isEqualTo(2)
                at("/short").integer().isEqualTo(3)
                at("/double").double().isEqualTo(4.0)
                at("/float").float().isEqualTo(5.0f)
                at("/bigDecimal").bigDecimal().isEqualTo(BigDecimal.valueOf(6))
                at("/bigInteger").bigInteger().isEqualTo(BigInteger.valueOf(7))
                at("/nestedObject").all {
                    at("/string").string().isEqualTo("string8")
                    at("/long").long().isEqualTo(9L)
                    at("/int").integer().isEqualTo(10)
                    at("/short").integer().isEqualTo(11)
                    at("/double").double().isEqualTo(12.0)
                    at("/float").float().isEqualTo(13.0f)
                    at("/bigDecimal").bigDecimal().isEqualTo(BigDecimal.valueOf(14))
                    at("/bigInteger").bigInteger().isEqualTo(BigInteger.valueOf(15))
                    at("/innerObject").all {
                        at("/myString").string().isEqualTo("string16")
                        at("/myLong").long().isEqualTo(17L)
                        at("/myByteArray").byteArray().isEqualTo(byteArrayOf(18, 19, 20, 21, 22, 23, 24))
                        at("/anotherString").string().isEqualTo("25")
                    }
                }
            }
        }

        it("should deserialize from string to an object") {
            val json = """
                {
                  "apiVersion" : "example.com/v0.1.2.3",
                  "kind" : "MyObject",
                  "metadata" : {
                    "name" : "myPreciousObject"
                  },
                  "bigDecimal" : 6.0,
                  "bigInteger" : 7,
                  "double" : 4.0,
                  "float" : 5.0,
                  "int" : 2,
                  "long" : 1,
                  "nestedObject" : {
                    "bigDecimal" : 14.0,
                    "bigInteger" : 15,
                    "double" : 12.0,
                    "float" : 13.0,
                    "innerObject" : {
                      "anotherString" : "25",
                      "myByteArray" : "EhMUFRYXGA==",
                      "myLong" : 17,
                      "myString" : "string16"
                    },
                    "int" : 10,
                    "long" : 9,
                    "short" : 11,
                    "string" : "string8"
                  },
                  "short" : 3,
                  "string" : "string0"
                }
            """.trimIndent()

            val o = krdDefinition.krdObjectFromJson(json)

            assertThat(o).prop("obj") { o.obj }.isEqualTo(myObject)
        }
    }

    describe("Nullable fields") {
        @ResourceDefinition(
                name = resourceName,
                kind = kind,
                singularName = singularName,
                pluralName = pluralName,
                group = group,
                version = version
        )
        data class MyObject(
                override val metadata: Metadata,
                val string1: String?,
                val string2: String?
        ) : Krd

        val myObject by memoized(SCOPE) {
            MyObject(
                    Metadata(myObjectName),
                    null,
                    "string2"
            )
        }

        val krdDefinition by memoized(SCOPE) { KrdDefinition(MyObject::class) }

        it("should serialize to a tree") {
            val o = krdDefinition.krdObject(myObject)
            assertThat(o.asJsonTree().also { println(it.toPrettyString()) }).all {
                at("/apiVersion").string().isEqualTo("$group/$version")
                at("/kind").string().isEqualTo(kind)
                at("/metadata/name").string().isEqualTo(myObjectName)
                at("/string1").isMissing()
                at("/string2").string().isEqualTo("string2")
            }
        }

        it("should deserialize from string to an object") {
            val json = """
                {
                  "apiVersion" : "example.com/v0.1.2.3",
                  "kind" : "MyObject",
                  "metadata" : {
                    "name" : "myPreciousObject"
                  },
                  "string2" : "string2"
                }
            """.trimIndent()

            val o = krdDefinition.krdObjectFromJson(json)

            assertThat(o).prop("obj") { o.obj }.isEqualTo(myObject)
        }
    }

    describe("Ignored fields") {
        @ResourceDefinition(
                name = resourceName,
                kind = kind,
                singularName = singularName,
                pluralName = pluralName,
                group = group,
                version = version
        )
        data class MyObject(
                override val metadata: Metadata,
                @Ignore
                val string1: String = "default",
                val string2: String
        ) : Krd

        val myObject by memoized(SCOPE) {
            MyObject(
                    Metadata(myObjectName),
                    string2 = "string2"
            )
        }

        val krdDefinition by memoized(SCOPE) { KrdDefinition(MyObject::class) }

        it("should serialize to a tree") {
            val o = krdDefinition.krdObject(myObject)
            assertThat(o.asJsonTree().also { println(it.toPrettyString()) }).all {
                at("/apiVersion").string().isEqualTo("$group/$version")
                at("/kind").string().isEqualTo(kind)
                at("/metadata/name").string().isEqualTo(myObjectName)
                at("/name").isMissing()
                at("/string1").isMissing()
                at("/string2").string().isEqualTo("string2")
            }
        }

        it("should deserialize from string to an object") {
            val json = """
                {
                  "apiVersion" : "example.com/v0.1.2.3",
                  "kind" : "MyObject",
                  "metadata" : {
                    "name" : "myPreciousObject"
                  },
                  "string2" : "string2"
                }
            """.trimIndent()

            val o = krdDefinition.krdObjectFromJson(json)

            assertThat(o).prop("obj") { o.obj }.isEqualTo(myObject)
        }
    }

    describe("Iterables") {

        data class NestedObject(
                @PropertyDefinition("int")
                val i: Int,
                @PropertyDefinition("str")
                val s: String
        )

        @ResourceDefinition(
                name = resourceName,
                kind = kind,
                singularName = singularName,
                pluralName = pluralName,
                group = group,
                version = version
        )
        data class MyObject(
                override val metadata: Metadata,
                @PropertyDefinition("myList")
                val list: List<Set<NestedObject>>,
                val set: Set<List<String>>
        ) : Krd

        val myObject by memoized(SCOPE) {
            MyObject(
                    Metadata(myObjectName),
                    listOf(
                            setOf(NestedObject(1, "2")),
                            setOf(NestedObject(3, "4"), NestedObject(5, "6"))
                    ),
                    setOf(
                            listOf("7", "8", "9")
                    )
            )
        }

        val krdDefinition by memoized(SCOPE) { KrdDefinition(MyObject::class) }

        it("should serialize to a tree") {
            val o = krdDefinition.krdObject(myObject)
            assertThat(o.asJsonTree().also { println(it.toPrettyString()) }).all {
                at("/apiVersion").string().isEqualTo("$group/$version")
                at("/kind").string().isEqualTo(kind)
                at("/metadata/name").string().isEqualTo(myObjectName)
                at("/myList/0/0/int").integer().isEqualTo(1)
                at("/myList/0/0/str").string().isEqualTo("2")
                at("/myList/1/0/int").integer().isEqualTo(3)
                at("/myList/1/0/str").string().isEqualTo("4")
                at("/myList/1/1/int").integer().isEqualTo(5)
                at("/myList/1/1/str").string().isEqualTo("6")
                at("/set/0/0").string().isEqualTo("7")
                at("/set/0/1").string().isEqualTo("8")
                at("/set/0/2").string().isEqualTo("9")
            }
        }

        it("should deserialize from string to an object") {
            val json = """
                {
                  "myList" : [ [ {
                    "int" : 1,
                    "str" : "2"
                  } ], [ {
                    "int" : 3,
                    "str" : "4"
                  }, {
                    "int" : 5,
                    "str" : "6"
                  } ] ],
                  "metadata" : {
                    "name" : "myPreciousObject"
                  },
                  "set" : [ [ "7", "8", "9" ] ],
                  "apiVersion" : "example.com/v0.1.2.3",
                  "kind" : "MyObject"
                }
            """.trimIndent()

            val o = krdDefinition.krdObjectFromJson(json)

            assertThat(o).prop("obj") { o.obj }.isEqualTo(myObject)
        }
    }
})