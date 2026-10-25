package org.rsmod.content.skills.agility

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.loc.MapLocDefinition
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class GnomeAgilityCourseTest {

    @Test
    fun courseLocSymbolsResolveToLiveIds() {
        val expected = mapOf(
            "loc.gnome_log_balance1" to 23145,
            "loc.log_balance2" to 23142,
            "loc.log_balance3" to 23143,
            "loc.obstical_net2" to 23134,
            "loc.obstical_net3" to 23135,
            "loc.climbing_branch" to 23559,
            "loc.climbing_tree" to 23560,
            "loc.climbing_tree2" to 23561,
            "loc.balancing_rope" to 23557,
            "loc.balancing_rope_mid" to 23558,
            "loc.obstical_pipe3_1" to 23138,
            "loc.obstical_pipe3_2" to 23139,
        )
        for ((symbol, id) in expected) {
            assertEquals(id, symbol.asRSCM(), symbol)
        }
    }

    @Test
    fun courseSeqSymbolsResolve() {
        for (symbol in listOf(
            "seq.human_walk_logbalance",
            "seq.human_climbing",
            "seq.human_climbing_down",
            "seq.myq3_human_tightrope",
            "seq.human_pipesqueeze",
        )) {
            assertTrue(symbol.asRSCM() > 0, symbol)
        }
    }

    @Test
    fun courseLocsExistAtExpectedCoords() {
        val expected = mapOf(
            23145 to setOf(2474 to 3435),
            23143 to setOf(2474 to 3434),
            23142 to setOf(2474 to 3433),
            23134 to setOf(2471 to 3425, 2473 to 3425, 2475 to 3425),
            23135 to setOf(2483 to 3426, 2485 to 3426, 2487 to 3426),
            23559 to setOf(2473 to 3422),
            23557 to setOf(2478 to 3420),
            23558 to setOf(2479 to 3420, 2480 to 3420, 2481 to 3420, 2482 to 3420),
            23560 to setOf(2486 to 3419),
            23138 to setOf(2484 to 3431, 2484 to 3435),
            23139 to setOf(2487 to 3431, 2487 to 3435),
        )
        val actual = courseLocs.filterKeys(expected::containsKey)
        assertEquals(expected.keys, actual.keys)
        for ((id, coords) in expected) {
            val positions = actual.getValue(id).map { it.x to it.z }.toSet()
            assertTrue(positions.containsAll(coords), "loc $id missing $coords from $positions")
        }
    }

    companion object {
        private lateinit var courseLocs: Map<Int, Set<CoordGrid>>

        @JvmStatic
        @BeforeAll
        fun loadCache() {
            val cache = ServerCacheManager.init(241)
            try {
                val square = MapSquareKey.from(CoordGrid(2474, 3434))
                val map = MapLocListDecoder.decode(
                    InlineByteBuf(checkNotNull(cache.data(MAPS, square.id, 1)))
                )
                val grouped = mutableMapOf<Int, MutableSet<CoordGrid>>()
                for (i in 0 until map.spawns.size) {
                    val def = MapLocDefinition(map.spawns.getLong(i))
                    grouped.getOrPut(def.id) { mutableSetOf() } +=
                        square.toCoords(def.level).translate(def.localX, def.localZ)
                }
                courseLocs = grouped
            } finally {
                cache.close()
            }
        }
    }
}
