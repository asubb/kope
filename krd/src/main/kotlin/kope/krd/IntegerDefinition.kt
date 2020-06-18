package kope.krd

@Target(AnnotationTarget.PROPERTY)
annotation class IntegerDefinition(
        val minimum: Int = Integer.MIN_VALUE,
        val maximum: Int = Integer.MAX_VALUE
)