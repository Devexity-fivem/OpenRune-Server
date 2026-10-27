package org.rsmod.content.bosses.alchemicalhydra

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
class AlchemicalHydraSpecTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun loadCache() {
            ServerCacheManager.init(240)
        }
    }

    @Test
    fun `spec symbols resolve`() {
        for (npc in listOf("npc.hydraboss", "npc.hydraboss_2", "npc.hydraboss_3", "npc.hydraboss_4")) {
            assertDoesNotThrow { npc.asRSCM(RSCMType.NPC) }
        }
        for (stage in 1..4) {
            assertDoesNotThrow { "seq.hydra_stage_${stage}_attack_ranged".asRSCM(RSCMType.SEQ) }
            assertDoesNotThrow { "seq.hydra_stage_${stage}_attack_magic".asRSCM(RSCMType.SEQ) }
        }
        for (stage in 1..3) {
            assertDoesNotThrow { "seq.hydra_stage_${stage}_attack_special".asRSCM(RSCMType.SEQ) }
        }
        for (spot in
            listOf(
                "hydraboss_ranged_proj",
                "hydraboss_magic_proj",
                "hydraboss_pools_proj",
                "hydraboss_lightning_caster",
                "hydraboss_lightning_travel",
                "hydraboss_fire",
                "hydraboss_fire_30",
                "hydraboss_fire_travel",
            )) {
            assertDoesNotThrow { "spotanim.$spot".asRSCM(RSCMType.SPOTANIM) }
        }
    }

    @Test
    fun `vent locs resolve`() {
        for (loc in
            listOf(
                "karuulm_hydra_room_floor_vent_geyser_green",
                "karuulm_hydra_room_floor_vent_geyser_blue",
                "karuulm_hydra_room_floor_vent_geyser_red",
                "karuulm_hydra_room_floor_vent_middle_blank",
            )) {
            assertDoesNotThrow { "loc.$loc".asRSCM(RSCMType.LOC) }
        }
    }

    @Test
    fun `hydra phases match wiki stats`() {
        val boss = ServerCacheManager.getNpc("npc.hydraboss".asRSCM(RSCMType.NPC))
        assertEquals(1100, boss?.hitpoints)
        assertEquals(426, boss?.combatLevel)
        for (npc in listOf("npc.hydraboss_2", "npc.hydraboss_3", "npc.hydraboss_4")) {
            assertEquals(1100, ServerCacheManager.getNpc(npc.asRSCM(RSCMType.NPC))?.hitpoints)
        }
    }
}
