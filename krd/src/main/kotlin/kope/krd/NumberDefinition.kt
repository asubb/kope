package kope.krd

const   val NAN: Double = 1.0e-323

@Target(AnnotationTarget.PROPERTY)
annotation class NumberDefinition(
        val minimum: Double = NAN,
        val maximum: Double = NAN
)