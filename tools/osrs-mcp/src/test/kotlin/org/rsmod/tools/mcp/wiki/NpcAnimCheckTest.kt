package org.rsmod.tools.mcp.wiki

import kotlin.test.Test

class NpcAnimCheckTest {
    @Test
    fun `check spell obj symbols`() {
        val index = GameValTool.load("C:/Users/pablo/OneDrive/Desktop/OpenRune")
        for (id in listOf(3280, 6884, 1963, 6883, 526, 6904, 6905, 6906, 6907)) {
            val r = index.search(query = "", table = "obj", id = id, limit = 5)
            println("=== obj id=$id")
            r.matches.forEach { println("  ${it.fullKey} = ${it.id}") }
        }
        val r = index.search(query = "bones", table = "obj", id = null, limit = 60)
        println("=== obj 'bones' total=${r.totalMatches}")
        r.matches.forEach { println("  ${it.fullKey} = ${it.id}") }
    }
}
