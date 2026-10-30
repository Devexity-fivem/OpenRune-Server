package org.rsmod.tools.mcp.wiki

import kotlin.test.Test

class BindGamevalTest {
    @Test
    fun `resolve bind family symbols`() {
        val index = GameValTool.load("C:/Users/pablo/OneDrive/Desktop/OpenRune")
        val queries =
            listOf(
                "seq" to "castentangle",
                "seq" to "spellcast",
                "spotanim" to "bind",
                "spotanim" to "snare",
                "spotanim" to "entangle",
                "projanim" to "bind",
                "projanim" to "snare",
                "projanim" to "entangle",
                "synth" to "bind",
                "synth" to "snare",
                "synth" to "entangle",
                "obj" to "bind",
                "obj" to "snare",
                "obj" to "entangle",
            )
        for ((table, query) in queries) {
            val r = index.search(query = query, table = table, id = null, limit = 12)
            println("=== $table '$query' total=${r.totalMatches}")
            r.matches.forEach { println("  ${it.fullKey} = ${it.id}") }
        }
    }
}
