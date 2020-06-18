package kope.krd

@Target(AnnotationTarget.PROPERTY)
annotation class StringDefinition (
    val format: String = "",
    val minLength: Int = -1,
    val maxLength: Int = -1,
    val pattern: String = ""
)