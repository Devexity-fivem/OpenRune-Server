package org.rsmod.content.bosses.calvarion

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
class CalvarionSpecTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun loadCache() {
            ServerCacheManager.init(240)
        }
    }

    @Test
    fun `spec symbols resolve`() {
        assertDoesNotThrow { "npc.vetion_single".asRSCM(RSCMType.NPC) }
        assertDoesNotThrow { "npc.vetion_2_single".asRSCM(RSCMType.NPC) }
        assertDoesNotThrow { "npc.vetion_hellhound_jnr_singles".asRSCM(RSCMType.NPC) }
        assertDoesNotThrow { "npc.vetion_hellhound_snr_singles".asRSCM(RSCMType.NPC) }
        assertDoesNotThrow { "seq.skeleton_update_giant_attack".asRSCM(RSCMType.SEQ) }
        assertDoesNotThrow { "seq.skeleton_update_giant_vary3_attack".asRSCM(RSCMType.SEQ) }
        assertDoesNotThrow { "spotanim.kalphite_glow_impact".asRSCM(RSCMType.SPOTANIM) }
    }

    @Test
    fun `forms and hellhounds match wiki stats`() {
        val normal = ServerCacheManager.getNpc("npc.vetion_single".asRSCM(RSCMType.NPC))
        val enraged = ServerCacheManager.getNpc("npc.vetion_2_single".asRSCM(RSCMType.NPC))
        val jnr = ServerCacheManager.getNpc("npc.vetion_hellhound_jnr_singles".asRSCM(RSCMType.NPC))
        val snr = ServerCacheManager.getNpc("npc.vetion_hellhound_snr_singles".asRSCM(RSCMType.NPC))
        assertEquals(150, normal?.hitpoints)
        assertEquals(150, enraged?.hitpoints)
        assertEquals(30, jnr?.hitpoints)
        assertEquals(30, snr?.hitpoints)
        assertEquals(264, normal?.combatLevel)
    }
}
