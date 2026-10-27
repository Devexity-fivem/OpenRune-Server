package org.rsmod.content.bosses.tztokjad

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
class TzTokJadSpecTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun loadCache() {
            ServerCacheManager.init(240)
        }
    }

    @Test
    fun `npc stats`() {
        val jad = ServerCacheManager.getNpc("npc.tzhaar_fightcave_swarm_boss".asRSCM(RSCMType.NPC))
        assertEquals("TzTok-Jad", jad?.name)
        assertEquals(250, jad?.hitpoints)
        assertEquals(702, jad?.combatLevel)
        assertEquals(5, jad?.size)

        val healer =
            ServerCacheManager.getNpc("npc.tzhaar_fightcave_swarm_boss_cleric".asRSCM(RSCMType.NPC))
        assertEquals("Yt-HurKot", healer?.name)
        assertEquals(60, healer?.hitpoints)
    }

    @Test
    fun `spec symbols resolve`() {
        assertDoesNotThrow {
            for (table in
                listOf(
                    RSCMType.SEQ to
                        listOf(
                            "lordmagmus_ready",
                            "lordmagmus_walk",
                            "lordmagmus_attack",
                            "lordmagmus_fire",
                            "lordmagmus_smash",
                            "lordmagmus_defend",
                            "lordmagmus_death",
                            "lizard_cleric_walk",
                            "lizard_cleric_ready",
                            "lizard_cleric_attack",
                            "lizard_cleric_death",
                            "lizard_cleric_heal",
                        ),
                    RSCMType.SPOTANIM to
                        listOf(
                            "tzhaar_fire_launch_travel",
                            "tzhaar_fire_launch_impact",
                            "tzhaar_rock_smash",
                            "tzhaar_heal",
                        ),
                    RSCMType.NPC to
                        listOf(
                            "tzhaar_fightcave_swarm_boss",
                            "tzhaar_fightcave_swarm_boss_cleric",
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
