package org.rsmod.content.bosses.corp

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
class CorporealBeastSpecTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun loadCache() {
            ServerCacheManager.init(240)
        }
    }

    @Test
    fun `spec symbols resolve`() {
        assertDoesNotThrow { "npc.corp_beast".asRSCM(RSCMType.NPC) }
        assertDoesNotThrow { "npc.dark_core".asRSCM(RSCMType.NPC) }
        assertDoesNotThrow { "seq.corp_double_stomp".asRSCM(RSCMType.SEQ) }
        assertDoesNotThrow { "spotanim.corp_spirit_beast_weak_proj".asRSCM(RSCMType.SPOTANIM) }
        assertDoesNotThrow { "spotanim.corp_spirit_beast_mid_proj".asRSCM(RSCMType.SPOTANIM) }
        assertDoesNotThrow { "spotanim.corp_spirit_beast_strong_proj".asRSCM(RSCMType.SPOTANIM) }
        assertDoesNotThrow { "spotanim.corp_ground_stomp".asRSCM(RSCMType.SPOTANIM) }
        assertDoesNotThrow { "spotanim.corp_magic_splash".asRSCM(RSCMType.SPOTANIM) }
        assertDoesNotThrow { "spotanim.dark_core_jump".asRSCM(RSCMType.SPOTANIM) }
    }

    @Test
    fun `corp and core are the expected types`() {
        assertEquals(319, "npc.corp_beast".asRSCM(RSCMType.NPC))
        assertEquals(320, "npc.dark_core".asRSCM(RSCMType.NPC))
    }
}
