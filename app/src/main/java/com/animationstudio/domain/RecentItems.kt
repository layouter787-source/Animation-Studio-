package com.animationstudio.domain

/** Lista de itens recentes (ids): o mais recente primeiro, sem repetidos, com limite. */
object RecentItems {
    const val DEFAULT_MAX = 20

    fun push(current: List<String>, id: String, max: Int = DEFAULT_MAX): List<String> =
        (listOf(id) + current.filter { it != id }).take(max)

    fun encode(list: List<String>): String = list.joinToString(",")

    fun decode(raw: String?): List<String> =
        raw.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
}
