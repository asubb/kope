dependencies {
    val kubernetesDslVersion:String by System.getProperties()
    val kubernetesClientVersion: String by System.getProperties()
    val jacksonVersion:String by System.getProperties()

    api("com.github.fkorotkov:k8s-kotlin-dsl:${kubernetesDslVersion}") {
        exclude(group = "io.fabric8", module = "kubernetes-model")
    }
    api("io.fabric8:kubernetes-model:${kubernetesClientVersion}")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:${jacksonVersion}")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:${jacksonVersion}")
}