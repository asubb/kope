dependencies {
    val kubernetesDslVersion:String by System.getProperties()
    val jacksonVersion:String by System.getProperties()

    api("com.fkorotkov:kubernetes-dsl:${kubernetesDslVersion}")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:${jacksonVersion}")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:${jacksonVersion}")
}