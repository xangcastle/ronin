package com.ronin.model

data class RoninMonorepo(
    val targets: Map<String, RoninTarget> = emptyMap()
)

data class RoninTarget(
    val kind: String = "library",
    val base: String? = null,
    val lang: String = "unknown",
    val port: Int? = null,
    val internal_deps: List<String> = emptyList(),
    val deps: List<String> = emptyList()
)
