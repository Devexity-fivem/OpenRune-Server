package org.rsmod.content.bosses.vorkath

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class VorkathSpecTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun loadCache() {
            ServerCacheManager.init(240)
        }
    }

    @Test
    fun `probe`() {
        val boss = ServerCacheManager.getNpc("npc.vorkath".asRSCM(RSCMType.NPC))
        val spawn = ServerCacheManager.getNpc("npc.vorkath_spawn".asRSCM(RSCMType.NPC))
        println("vorkath hp=${boss?.hitpoints} lvl=${boss?.combatLevel} size=${boss?.size}")
        println("spawn hp=${spawn?.hitpoints} lvl=${spawn?.combatLevel} size=${spawn?.size}")
    }

    @Test
    fun `spec symbols resolve`() {
        assertDoesNotThrow {
            for (table in
                listOf(
                    RSCMType.SEQ to
                        listOf(
                            "ds2_vorkath_attack_melee",
                            "ds2_vorkath_ranged",
                            "ds2_vorkath_fireball",
                            "ds2_vorkath_acid",
                            "ds2_vorkath_stomp",
                            "ds2_vorkath_spawn",
                        ),
                    RSCMType.PROJANIM to listOf("dragonfire"),
                    RSCMType.SPOTANIM to
                        listOf(
                            "vorkath_magic_travel",
                            "vorkath_magic_impact",
                            "vorkath_ranged_travel",
                            "vorkath_ranged_impact",
                            "vorkath_area_travel",
                            "vorkath_area_small_travel",
                            "vorkath_acid_travel",
                            "vorkath_spawn_travel",
                            "ice_barrage_impact",
                            "ice_minion_explode_spotanim",
                            "dragon_ranged_toxic_attack",
                            "dragon_ranged_fire_attack",
                            "dragon_ranged_ice_attack",
                        ),
                    RSCMType.NPC to listOf("vorkath", "vorkath_spawn"),
                    RSCMType.LOC to listOf("vorkath_acid"),
                )) {
                val (type, names) = table
                for (name in names) {
                    val symbol = "${type.prefix}.$name"
                    println("$symbol = ${symbol.asRSCM(type)}")
                }
            }
        }
    }

    @Test
    fun `vorkath stats match wiki`() {
        val boss = ServerCacheManager.getNpc("npc.vorkath".asRSCM(RSCMType.NPC))
        val spawn = ServerCacheManager.getNpc("npc.vorkath_spawn".asRSCM(RSCMType.NPC))
        assertNotNull(boss)
        assertNotNull(spawn)
        assertEquals(750, boss?.hitpoints)
        assertEquals(732, boss?.combatLevel)
        assertEquals(38, spawn?.hitpoints)
    }
}
