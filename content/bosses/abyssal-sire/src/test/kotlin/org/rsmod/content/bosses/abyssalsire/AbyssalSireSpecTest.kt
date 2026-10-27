package org.rsmod.content.bosses.abyssalsire

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
class AbyssalSireSpecTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun loadCache() {
            ServerCacheManager.init(240)
        }
    }

    @Test
    fun `npc stats`() {
        val sire =
            ServerCacheManager.getNpc("npc.abyssalsire_sire_wandering".asRSCM(RSCMType.NPC))
        assertEquals("Abyssal Sire", sire?.name)
        assertEquals(425, sire?.hitpoints)
        assertEquals(350, sire?.combatLevel)
        assertEquals(6, sire?.size)

        val spawn = ServerCacheManager.getNpc("npc.abyssalsire_spawn".asRSCM(RSCMType.NPC))
        assertEquals("Spawn", spawn?.name)
        val scion = ServerCacheManager.getNpc("npc.abyssalsire_scion".asRSCM(RSCMType.NPC))
        assertEquals("Scion", scion?.name)
    }

    @Test
    fun `spec symbols resolve`() {
        assertDoesNotThrow {
            for (table in
                listOf(
                    RSCMType.SEQ to
                        listOf(
                            "sire_attack_double_whips",
                            "sire_attack_double_hook",
                            "sire_attack_right_whip",
                            "sire_attack_miasma",
                            "sire_attack_miasma_two",
                            "sire_attack_spawns",
                            "sire_attack_spawns_two",
                            "sire_attack_teleport_player",
                            "sire_apocalypse",
                            "sire_death",
                            "sire_idle_one",
                            "sire_idle_three",
                            "sire_idle_four",
                            "abyssal_spawn_attack",
                            "abyssal_spawn_defend",
                            "abyssal_spawn_death",
                            "abyssal_spawn_idle",
                            "abyssal_spawn_walk",
                            "abyssal_scion_spawn",
                            "abyssal_scion_walk",
                            "abyssal_scion_idle",
                            "abyssal_scion_attack_melee",
                            "abyssal_scion_attack_ranged",
                            "abyssal_scion_defend",
                            "abyssal_scion_death",
                        ),
                    RSCMType.NPC to
                        listOf(
                            "abyssalsire_sire_wandering",
                            "abyssalsire_sire_panicking",
                            "abyssalsire_spawn",
                            "abyssalsire_scion",
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
