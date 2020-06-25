dependencies {
    implementation(project(":krd"))

    val kubernetesDslVersion:String by System.getProperties()
    val jacksonVersion:String by System.getProperties()
    val kubernetesClientVersion:String by System.getProperties()

    api("com.fkorotkov:kubernetes-dsl:${kubernetesDslVersion}")
    api("io.fabric8:kubernetes-client:${kubernetesClientVersion}")

    implementation("io.github.microutils:kotlin-logging:1.7.7")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:${jacksonVersion}")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:${jacksonVersion}")

    testImplementation("ch.qos.logback:logback-classic:1.2.3")
}