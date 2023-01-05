dependencies {
    implementation(project(":krd"))

    val jacksonVersion: String by System.getProperties()
    val kubernetesClientVersion: String by System.getProperties()

    api("io.fabric8:kubernetes-client-api:${kubernetesClientVersion}")
    implementation("io.fabric8:kubernetes-client:${kubernetesClientVersion}")

    implementation("io.github.microutils:kotlin-logging:1.7.7")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:${jacksonVersion}")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:${jacksonVersion}")
}