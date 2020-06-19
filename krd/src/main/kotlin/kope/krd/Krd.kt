package kope.krd

interface Krd {
    val metadata: Metadata
}

data class Metadata (
    val name: String
)