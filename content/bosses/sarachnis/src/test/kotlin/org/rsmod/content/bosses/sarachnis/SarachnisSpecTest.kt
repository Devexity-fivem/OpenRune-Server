package org.rsmod.content.bosses.sarachnis

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
class SarachnisSpecTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun loadCache() {
            ServerCacheManager.init(240)
        }
    }

    @Test
    fun `spec symbols resolve`() {
        assertDoesNotThrow { "npc.sarachnis".asRSCM(RSCMType.NPC) }
        assertDoesNotThrow { "npc.sarachnis_melee_spawn".asRSCM(RSCMType.NPC) }
        assertDoesNotThrow { "npc.sarachnis_mage_spawn".asRSCM(RSCMType.NPC) }
        assertDoesNotThrow { "seq.spider_attack".asRSCM(RSCMType.SEQ) }
        assertDoesNotThrow { "spotanim.ice_burst_impact".asRSCM(RSCMType.SPOTANIM) }
    }
}
