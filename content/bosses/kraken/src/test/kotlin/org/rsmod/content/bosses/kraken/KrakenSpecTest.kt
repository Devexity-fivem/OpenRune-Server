package org.rsmod.content.bosses.kraken

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class KrakenSpecTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun loadCache() {
            ServerCacheManager.init(240)
        }
    }

    @Test
    fun `npc stats`() {
        val kraken = ServerCacheManager.getNpc("npc.slayer_kraken_boss".asRSCM(RSCMType.NPC))
        assertEquals("Kraken", kraken?.name)
        assertEquals(255, kraken?.hitpoints)
        assertEquals(291, kraken?.combatLevel)
        assertEquals(4, kraken?.size)

        val tentacle =
            ServerCacheManager.getNpc("npc.slayer_kraken_boss_tentacle".asRSCM(RSCMType.NPC))
        assertEquals("Enormous Tentacle", tentacle?.name)
        assertEquals(120, tentacle?.hitpoints)

        val pool =
            ServerCacheManager.getNpc("npc.slayer_kraken_boss_whirlpool".asRSCM(RSCMType.NPC))
        assertEquals("Whirlpool", pool?.name)
    }

    @Test
    fun `spec symbols resolve`() {
        assertDoesNotThrow {
            for (table in
                listOf(
                    RSCMType.SEQ to
                        listOf(
                            "slayer_kraken_arise",
                            "human_caststrike",
                            "tentacle_monster_attack",
                            "tentacle_monster_ready",
                            "tentacle_monster_death",
                        ),
                    RSCMType.SPOTANIM to
                        listOf("waterwave_travel", "waterwave_impact"),
                    RSCMType.NPC to
                        listOf(
                            "slayer_kraken_boss",
                            "slayer_kraken_boss_whirlpool",
                            "slayer_kraken_boss_tentacle",
                            "slayer_kraken_boss_tentacle_whirlpool",
                        ),
                )) {
                val (type, names) = table
                for (name in names) {
                    val symbol = "${type.prefix}.$name"
                    println("$symbol = ${symbol.asRSCM(type)}")
                }
            }
        }
    }
}
