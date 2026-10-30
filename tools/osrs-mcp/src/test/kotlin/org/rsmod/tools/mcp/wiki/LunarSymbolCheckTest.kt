package org.rsmod.tools.mcp.wiki

import java.io.File
import kotlin.test.Test

class LunarSymbolCheckTest {
    @Test
    fun `validate all symbols in lunar-spells module`() {
        val index = GameValTool.load("C:/Users/pablo/OneDrive/Desktop/OpenRune")
        val dir =
            File(
                "C:/Users/pablo/OneDrive/Desktop/OpenRune/content/skills/magic/lunar-spells/src/main/kotlin"
            )
        val pattern = Regex("\"((obj|npc|loc|spotanim|seq|bas|timer|queue|varbit|varp|mesanim|synth|enum|component|param|quest|inv|stat|interface)\\.[a-zA-Z0-9_]+)\"")
        val found = sortedSetOf<String>()
        dir.walkTopDown().filter { it.extension == "kt" }.forEach { f ->
            pattern.findAll(f.readText()).forEach { found += it.groupValues[1] }
        }
        var missing = 0
        for (sym in found) {
            val table = sym.substringBefore('.')
            val key = sym.substringAfter('.')
            val r = index.search(query = key, table = table, id = null, limit = 50)
            val exact = r.matches.any { it.fullKey == sym }
            if (!exact) {
                missing++
                println("MISS $sym")
                r.matches.take(4).forEach { println("      near: ${it.fullKey}=${it.id}") }
            }
        }
        println("checked=${found.size} missing=$missing")
        for (q in listOf("89", "enchant", "recharg")) {
            val r = index.search(query = q, table = "obj", id = null, limit = 100)
            println("=== obj contains '$q' total=${r.totalMatches}")
            r.matches.filter { it.key.startsWith("89") || it.key.contains("recharg") || it.key.contains("dragonstone") }
                .forEach { println("  ${it.fullKey} = ${it.id}") }
        }
    }
}
