package org.rsmod.content.skills.agility

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.loc.MapLocDefinition
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.tile.MapTileDecoder
import dev.openrune.map.tile.MapTileSimpleDefinition
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
class BarbarianOutpostCourseTest {

    @Test
    fun courseLocSymbolsResolveToLiveIds() {
        val expected = mapOf(
            "loc.obstical_ropeswing1" to 23131,
            "loc.barbarian_log_balance1" to 23144,
            "loc.agility_obstical_net_barbarian" to 20211,
            "loc.agility_obstical_pipe_barbarian" to 20210,
            "loc.balancing_ledge1" to 23547,
            "loc.barbarian_laddertop_norim" to 42487,
            "loc.castlecrumbly1" to 1948,
        )
        for ((symbol, id) in expected) {
            assertEquals(id, symbol.asRSCM(), symbol)
        }
    }

    @Test
    fun courseSeqSymbolsResolve() {
        for (symbol in listOf(
            "seq.human_ropeswing_long",
            "seq.human_ropeswing_long_miss",
            "seq.ropeswing_long",
            "seq.human_walk_logbalance",
            "seq.human_falling",
            "seq.human_climbing_fall",
            "seq.human_swim",
            "seq.human_pipesqueeze",
            "seq.human_climbing",
            "seq.human_climbing_down",
            "seq.human_walk_sidestepl",
            "seq.agility_shortcut_wall_jump",
        )) {
            assertTrue(symbol.asRSCM() > 0, symbol)
        }
    }

    @Test
    fun courseLocsExistAtExpectedCoords() {
        val expected = mapOf(
            23131 to setOf(Triple(2551, 3550, 0)),
            23144 to setOf(Triple(2550, 3546, 0)),
            20211 to setOf(Triple(2538, 3545, 0)),
            20210 to setOf(Triple(2552, 3559, 0)),
            23547 to setOf(Triple(2535, 3547, 1)),
            42487 to setOf(Triple(2532, 3545, 1)),
            1948 to setOf(Triple(2536, 3553, 0), Triple(2539, 3553, 0), Triple(2542, 3553, 0)),
            16680 to setOf(Triple(2547, 3551, 0)),
        )
        val actual = courseLocs.filterKeys(expected::containsKey)
        assertEquals(expected.keys, actual.keys)
        for ((id, coords) in expected) {
            val positions = actual.getValue(id).map { Triple(it.x, it.z, it.level) }.toSet()
            assertTrue(positions.containsAll(coords), "loc $id missing $coords from $positions")
        }
    }

    @Test
    fun pitLadderExistsBelowCourse() {
        val expected = mapOf(
            23550 to setOf(Triple(2550, 9951, 0)),
            17385 to setOf(Triple(2547, 9951, 0)),
        )
        val actual = pitLocs.filterKeys(expected::containsKey)
        assertEquals(expected.keys, actual.keys)
        for ((id, coords) in expected) {
            val positions = actual.getValue(id).map { Triple(it.x, it.z, it.level) }.toSet()
            assertTrue(positions.containsAll(coords), "loc $id missing $coords from $positions")
        }
    }

    companion object {
        private const val BRIDGE_FLAG_LEVEL = 1

        private lateinit var courseLocs: Map<Int, Set<CoordGrid>>
        private lateinit var pitLocs: Map<Int, Set<CoordGrid>>

        @JvmStatic
        @BeforeAll
        fun loadCache() {
            val cache = ServerCacheManager.init(241)
            try {
                courseLocs =
                    decodeLocs(
                        cache.data(MAPS, SQUARE_COURSE.id, 0)!!,
                        cache.data(MAPS, SQUARE_COURSE.id, 1)!!,
                        SQUARE_COURSE,
                    )
                pitLocs =
                    decodeLocs(
                        cache.data(MAPS, SQUARE_PIT.id, 0)!!,
                        cache.data(MAPS, SQUARE_PIT.id, 1)!!,
                        SQUARE_PIT,
                    )
            } finally {
                cache.close()
            }
        }

        private val SQUARE_COURSE = MapSquareKey.from(CoordGrid(2551, 3552))
        private val SQUARE_PIT = MapSquareKey.from(CoordGrid(2549, 9951))

        // Mirrors GameMapDecoder.putLocs: spawns on LINK_BELOW tiles resolve down a level.
        private fun decodeLocs(
            tileData: ByteArray,
            locData: ByteArray,
            square: MapSquareKey,
        ): Map<Int, Set<CoordGrid>> {
            val tiles = MapTileDecoder.decode(InlineByteBuf(tileData))
            val map = MapLocListDecoder.decode(InlineByteBuf(locData))
            val grouped = mutableMapOf<Int, MutableSet<CoordGrid>>()
            for (i in 0 until map.spawns.size) {
                val def = MapLocDefinition(map.spawns.getLong(i))
                val bridged =
                    tiles[def.localX, def.localZ, BRIDGE_FLAG_LEVEL].toInt() and
                        MapTileSimpleDefinition.LINK_BELOW != 0
                val level = if (bridged) def.level - 1 else def.level
                if (level < 0) {
                    continue
                }
                grouped.getOrPut(def.id) { mutableSetOf() } +=
                    square.toCoords(level).translate(def.localX, def.localZ)
            }
            return grouped
        }
    }
}
