package kope.krd

@Target(AnnotationTarget.PROPERTY)
annotation class PropertyDefinition(
        val name: String = "",
        val description: String = ""
)