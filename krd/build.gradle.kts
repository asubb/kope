dependencies {
    val kubernetesDslVersion:String by System.getProperties()
    implementation("com.fkorotkov:kubernetes-dsl:${kubernetesDslVersion}")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.11.+")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.11.+")
}