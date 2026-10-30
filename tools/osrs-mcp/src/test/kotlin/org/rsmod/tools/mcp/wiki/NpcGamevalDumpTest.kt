package org.rsmod.tools.mcp.wiki

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class NpcGamevalDumpTest {
    @Test
    fun `dump npc gamevals to tsv`() {
        val index = GameValTool.load()
        val result = index.search(query = "", table = "npc", id = null, limit = Int.MAX_VALUE)
        val root = Path.of(System.getProperty("user.dir"))
        val out = root.resolve("npc-gamevals.tsv")
        val lines =
            result.matches.map { entry -> "${entry.id}\t${entry.fullKey}" }.sortedBy {
                it.substringBefore('\t').toInt()
            }
        Files.write(out, lines)
        println("wrote ${lines.size} npc gamevals -> $out")
        assertTrue(lines.isNotEmpty())
    }
}
