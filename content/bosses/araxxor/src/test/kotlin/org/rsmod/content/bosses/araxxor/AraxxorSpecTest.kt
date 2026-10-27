package org.rsmod.content.bosses.araxxor

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
class AraxxorSpecTest {
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
                "araxxor",
                "araxxor_minion_egg_mirrorback",
                "araxxor_minion_mirrorback",
                "araxxor_minion_egg_explode",
                "araxxor_minion_explode",
                "araxxor_minion_egg_venom",
                "araxxor_minion_venom",
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
                            "npc_araxxor_01_idle_01",
                            "npc_araxxor_01_walk_01",
                            "npc_araxxor_01_attack_ranged_01",
                            "npc_araxxor_01_attack_acid_leak_01",
                            "npc_araxxor_01_attack_acid_spray_01",
                            "npc_araxxor_01_attack_magic_01",
                            "npc_araxxor_01_attack_melee_01",
                            "npc_araxxor_01_death_01",
                            "npc_araxxor_01_spawn_01",
                            "npc_araxxor_01_attack_melee_enraged_01",
                            "npc_araxxor_01_enrage_transition_01",
                            "npc_araxxor_01_enrage_transition_02",
                            "npc_araxxor_01_acid_cannon_01",
                            "npc_araxyte02_hatch",
                            "npc_araxyte_explode",
                            "egg_araxyte_spawn_01",
                            "egg_araxyte_idle_01",
                            "egg_araxyte_hatch_01",
                        ),
                    RSCMType.SPOTANIM to
                        listOf(
                            "araxxor_ranged_projectile",
                            "araxxor_magic_projectile",
                            "araxxor_magic_impact",
                            "araxxor_venom_drip",
                            "araxxor_pools_splash",
                            "araxxor_pools_proj",
                            "araxyte_explosive_spider_explosion",
                            "araxyte_acid_spider_explosion",
                        ),
                    RSCMType.NPC to
                        listOf(
                            "araxxor",
                            "araxxor_minion_egg_mirrorback",
                            "araxxor_minion_mirrorback",
                            "araxxor_minion_egg_explode",
                            "araxxor_minion_explode",
                            "araxxor_minion_egg_venom",
                            "araxxor_minion_venom",
                        ),
                    RSCMType.LOC to listOf("araxxor_acidpool"),
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
