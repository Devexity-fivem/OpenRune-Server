package org.rsmod.tools.mcp.wiki

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * Verifies that duplicate `[[npc]]` overlay blocks merge instead of silently dropping fields.
 * Requires `.data/cache/SERVER` to exist (run `gradlew :or-cache:buildCache` first).
 */
class ServerCacheOverlayVerificationTest {

    private fun searchServerNpc(id: Int): CacheTool.SearchHit {
        assumeTrue(Files.isDirectory(Path.of(".data/cache/SERVER")))
        val result = CacheTool().search(CacheKind.SERVER, CacheSearchType.Npc, null, id, 1)
        assertEquals(1, result.matches.size, "npc $id missing from server cache")
        return result.matches.first()
    }

    private fun field(hit: CacheTool.SearchHit, name: String): String? =
        hit.data.split("; ").firstOrNull { it.startsWith("$name=") }?.substringAfter('=')

    @Test
    fun `callisto keeps hunt anims and killcount fields from both overlay blocks`() {
        val hit = searchServerNpc(6609)
        assertEquals("1", field(hit, "huntMode"))
        assertEquals("30", field(hit, "huntRange"))
        assertEquals("30", field(hit, "maxRange"))
        assertEquals("11", field(hit, "respawnRate"))
        assertTrue(field(hit, "params")!!.contains("callisto"))
    }

    @Test
    fun `bandos avatar keeps hunt config anims and killcount varp`() {
        val hit = searchServerNpc(2215)
        assertEquals("6", field(hit, "huntMode"))
        assertEquals("30", field(hit, "huntRange"))
        assertTrue(field(hit, "params")!!.contains("godwars_bandos_attack"))
        assertTrue(field(hit, "params")!!.contains("total_bandos_kills"))
    }

    @Test
    fun `saltfish fishing spot keeps respawn rate`() {
        val hit = searchServerNpc(1530)
        assertEquals("50", field(hit, "respawnRate"))
        assertEquals("content.net_bait_fishing_spot", field(hit, "contentGroup"))
    }

    @Test
    fun `lesser demon keeps server params under pack wanderRange overlay`() {
        val hit = searchServerNpc(2005)
        assertEquals("2", field(hit, "wanderRange"))
        val params = field(hit, "params")!!
        assertTrue(params.contains("demon_attack"), "lesser demon lost attack anim: $params")
        assertTrue(params.contains("attacktype_slash"), "lesser demon lost attack type: $params")
    }
}
