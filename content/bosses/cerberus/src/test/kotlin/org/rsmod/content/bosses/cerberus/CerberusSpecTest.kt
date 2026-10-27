package org.rsmod.content.bosses.cerberus

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class CerberusSpecTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun loadCache() {
            ServerCacheManager.init(240)
        }
    }

    @Test
    fun `probe`() {
        for (name in
            listOf(
                "cerberus_sitting",
                "cerberus_attacking",
                "cerberus_resetting",
                "cerberus_spectre_magic",
                "cerberus_spectre_melee",
                "cerberus_spectre_ranged",
            )) {
            val npc = ServerCacheManager.getNpc("npc.$name".asRSCM(RSCMType.NPC))
            println("$name hp=${npc?.hitpoints} lvl=${npc?.combatLevel} size=${npc?.size} id=${
                "npc.$name".asRSCM(RSCMType.NPC)
            }")
        }
    }

    @Test
    fun `spec symbols resolve`() {
        assertDoesNotThrow {
            for (table in
                listOf(
                    RSCMType.SEQ to
                        listOf(
                            "cerberus_idle_sitting",
                            "cerberus_idle_to_stand",
                            "cerberus_idle",
                            "cerberus_walk",
                            "cerberus_bite",
                            "cerberus_attack_range",
                            "cerberus_fire_breath",
                            "cerberus_howl",
                            "cerberus_special_attack_flame",
                            "cerberus_special_attack_spray",
                            "cerberus_death",
                            "cerberus_defend",
                            "cerberus_stand_to_sit",
                        ),
                    RSCMType.PROJANIM to listOf("dragonfire"),
                    RSCMType.SPOTANIM to
                        listOf(
                            "dragon_ranged_fire_attack",
                            "dragon_ranged_ice_attack",
                            "vfx_flames_of_cerberus_1x1",
                            "vfx_flames_of_cerberus_3x3",
                            "vfx_flames_of_cerberus_5x5",
                        ),
                    RSCMType.NPC to
                        listOf(
                            "cerberus_sitting",
                            "cerberus_attacking",
                            "cerberus_resetting",
                            "cerberus_spectre_magic",
                            "cerberus_spectre_melee",
                            "cerberus_spectre_ranged",
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
