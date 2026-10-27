package org.rsmod.content.bosses.vetion

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
class VetionSpecTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun loadCache() {
            ServerCacheManager.init(240)
        }
    }

    @Test
    fun `spec symbols resolve`() {
        assertDoesNotThrow { "npc.vetion".asRSCM(RSCMType.NPC) }
        assertDoesNotThrow { "npc.vetion_2".asRSCM(RSCMType.NPC) }
        assertDoesNotThrow { "npc.vetion_hellhound_jnr".asRSCM(RSCMType.NPC) }
        assertDoesNotThrow { "npc.vetion_hellhound_snr".asRSCM(RSCMType.NPC) }
        assertDoesNotThrow { "seq.skeleton_update_giant_attack".asRSCM(RSCMType.SEQ) }
        assertDoesNotThrow { "seq.skeleton_update_giant_vary3_attack".asRSCM(RSCMType.SEQ) }
        assertDoesNotThrow { "spotanim.kalphite_glow_impact".asRSCM(RSCMType.SPOTANIM) }
    }

    @Test
    fun `forms and hellhounds match wiki stats`() {
        val normal = ServerCacheManager.getNpc("npc.vetion".asRSCM(RSCMType.NPC))
        val enraged = ServerCacheManager.getNpc("npc.vetion_2".asRSCM(RSCMType.NPC))
        val jnr = ServerCacheManager.getNpc("npc.vetion_hellhound_jnr".asRSCM(RSCMType.NPC))
        val snr = ServerCacheManager.getNpc("npc.vetion_hellhound_snr".asRSCM(RSCMType.NPC))
        assertEquals(255, normal?.hitpoints)
        assertEquals(255, enraged?.hitpoints)
        assertEquals(30, jnr?.hitpoints)
        assertEquals(30, snr?.hitpoints)
    }
}
