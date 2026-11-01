package org.rsmod.content.minigames.mess

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.loc.MapLocDefinition
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.util.InlineByteBuf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MessSceneryTest {
    private lateinit var locs: List<MapLocDefinition>

    @BeforeAll
    fun loadMap() {
        val cache = ServerCacheManager.init(240)
        try {
            val square = MapSquareKey.from(CoordGrid(1645, 3625))
            val map =
                MapLocListDecoder.decode(
                    InlineByteBuf(checkNotNull(cache.data(MAPS, square.id, 1)))
                )
            locs = map.spawns.map(::MapLocDefinition)
        } finally {
            cache.close()
        }
    }

    @Test
    fun `mess kitchen locs are placed`() {
        val square = MapSquareKey.from(CoordGrid(1645, 3625))
        locs.forEach {
            println(
                "square loc id=${it.id} at " +
                    "${square.x * 64 + it.localX},${square.z * 64 + it.localZ} " +
                    "level=${it.level} shape=${it.shape} angle=${it.angle}"
            )
        }
        val kitchen =
            locs.filter {
                val wx = square.x * 64 + it.localX
                val wz = square.z * 64 + it.localZ
                wx in 1634..1670 && wz in 3613..3636
            }
        for (id in intArrayOf(FOOD_CUPBOARD, UTENSIL_CUPBOARD, MEAT_TABLE, BUFFET, SINK, CLAY_OVEN)) {
            assertTrue(kitchen.any { it.id == id }, "loc $id must be placed in the Mess kitchen")
        }
    }

    private companion object {
        private const val FOOD_CUPBOARD = 27375
        private const val UTENSIL_CUPBOARD = 27376
        private const val MEAT_TABLE = 27377
        private const val BUFFET = 27378
        private const val SINK = 9684
        private const val CLAY_OVEN = 21302
    }
}
