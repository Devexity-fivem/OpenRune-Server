package org.rsmod.content.minigames.tearsofguthix

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.loc.MapLocDefinition
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.util.InlineByteBuf
import org.junit.jupiter.api.Assertions.assertEquals
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
class TearsOfGuthixSceneryTest {
    private lateinit var locs: List<MapLocDefinition>

    @BeforeAll
    fun loadMap() {
        val cache = ServerCacheManager.init(240)
        try {
            val square = MapSquareKey.from(CoordGrid(3252, 9517))
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
    fun `juna multiloc is placed on her ledge`() {
        val juna = locs.filter { it.id == TOG_JUNA }
        assertTrue(juna.isNotEmpty(), "tog_juna (id 3193) must be placed in map square 50_148")
        juna.forEach { println("tog_juna at $it") }
    }

    @Test
    fun `weeping wall multilocs are placed in the cave`() {
        val walls = locs.filter { it.id == TOG_WEEPINGWALL }
        assertEquals(9, walls.size, "expected 9 weeping-wall multiloc placements")
        walls.forEach { println("tog_weepingwall at $it") }
    }

    private companion object {
        private const val TOG_JUNA = 3193
        private const val TOG_WEEPINGWALL = 6660
    }
}
