package org.rsmod.content.bosses.nex

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
class NexSpecTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun loadCache() {
            ServerCacheManager.init(240)
        }
    }

    @Test
    fun `spec symbols resolve`() {
        for (npc in
            listOf(
                "npc.nex",
                "npc.nex_soulsplit",
                "npc.nex_deflect",
                "npc.nex_smokemage",
                "npc.nex_shadowmage",
                "npc.nex_bloodmage",
                "npc.nex_icemage",
                "npc.nex_prison_blood_reaver_boss",
            )) {
            assertDoesNotThrow { npc.asRSCM(RSCMType.NPC) }
        }
        for (seq in
            listOf(
                "seq.nex_attack",
                "seq.nex_cast_attack",
                "seq.nex_alternate_cast_attack",
                "seq.nex_blood_siphon",
                "seq.nex_smash_attack",
                "seq.nex_dash_attack",
            )) {
            assertDoesNotThrow { seq.asRSCM(RSCMType.SEQ) }
        }
        for (spot in
            listOf(
                "spotanim.nex_smoke_attack_proj",
                "spotanim.nex_shadow_attack_proj",
                "spotanim.nex_blood_attack_proj",
                "spotanim.nex_ice_attack_proj",
                "spotanim.nex_ice_prison_proj",
                "spotanim.nex_finale_attack_proj",
                "spotanim.nex_turmoil",
                "spotanim.nex_blood_siphon",
            )) {
            assertDoesNotThrow { spot.asRSCM(RSCMType.SPOTANIM) }
        }
        assertDoesNotThrow { "loc.nex_shadow_smash".asRSCM(RSCMType.LOC) }
        assertDoesNotThrow { "loc.nex_icicle_1".asRSCM(RSCMType.LOC) }
        assertDoesNotThrow { "loc.nex_fight_barrier_outer".asRSCM(RSCMType.LOC) }
        assertDoesNotThrow { "loc.nex_fight_barrier_outer_priv".asRSCM(RSCMType.LOC) }
        assertDoesNotThrow { "loc.nex_inner_prison_door".asRSCM(RSCMType.LOC) }
        assertDoesNotThrow { "loc.nex_outer_prison_door".asRSCM(RSCMType.LOC) }
        for (loc in
            listOf(
                "loc.nex_frozen_door_inner_1",
                "loc.nex_frozen_door_inner_2",
                "loc.nex_frozen_door_outer_1",
                "loc.nex_frozen_door_outer_2",
            )) {
            assertDoesNotThrow { loc.asRSCM(RSCMType.LOC) }
        }
        assertDoesNotThrow { "varp.total_nex_kills".asRSCM(RSCMType.VARP) }
        assertDoesNotThrow { "varp.nex_perm".asRSCM(RSCMType.VARP) }
        assertDoesNotThrow { "varbit.godwars_counter_zaros".asRSCM(RSCMType.VARBIT) }
        assertDoesNotThrow { "dbrow.instance_nex".asRSCM(RSCMType.DBROW) }
        for (varn in
            listOf(
                "varn.nex_curse",
                "varn.nex_mage_called",
                "varn.nex_siphon",
                "varn.nex_darkness",
                "varn.nex_choked",
            )) {
            assertDoesNotThrow { varn.asRSCM(RSCMType.VARN) }
        }
        for (obj in
            listOf(
                "obj.nex_frozen_key",
                "obj.nex_frozen_key_armadyl",
                "obj.nex_frozen_key_bandos",
                "obj.nex_frozen_key_saradomin",
                "obj.nex_frozen_key_zamorak",
            )) {
            assertDoesNotThrow { obj.asRSCM(RSCMType.OBJ) }
        }
    }

    @Test
    fun `nex matches wiki stats`() {
        val nex = ServerCacheManager.getNpc("npc.nex".asRSCM(RSCMType.NPC))
        assertNotNull(nex)
        assertEquals(3400, nex?.hitpoints)
        assertEquals(1001, nex?.combatLevel)
    }
}
