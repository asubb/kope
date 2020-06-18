package kope.krd

@Target(AnnotationTarget.CLASS)
annotation class ResourceDefinition(
        val name: String,
        val kind: String,
        val version: String,
        val group: String,
        val singularName: String,
        val pluralName: String,
        val scope: Scope = Scope.NAMESPACED,
        val preserveUnknownFields: Boolean = false,
        val apiVersion: String = "apiextensions.k8s.io/v1",
        val shortNames: Array<String> = emptyArray()
)

enum class Scope(val value: String) {
    NAMESPACED("Namespaced"),
    CLUSTER("Cluster")
}