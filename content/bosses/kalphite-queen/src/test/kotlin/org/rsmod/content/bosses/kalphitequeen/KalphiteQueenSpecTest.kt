package org.rsmod.content.bosses.kalphitequeen

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
class KalphiteQueenSpecTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun loadCache() {
            ServerCacheManager.init(240)
        }
    }

    @Test
    fun `spec symbols resolve`() {
        assertDoesNotThrow { "npc.kalphite_queen".asRSCM(RSCMType.NPC) }
        assertDoesNotThrow { "npc.kalphite_flyingqueen".asRSCM(RSCMType.NPC) }
        assertDoesNotThrow { "npc.kalphite_worker".asRSCM(RSCMType.NPC) }
        assertDoesNotThrow { "seq.kalphite_queen_attack_claws".asRSCM(RSCMType.SEQ) }
        assertDoesNotThrow { "seq.kalphite_queen_lightning".asRSCM(RSCMType.SEQ) }
        assertDoesNotThrow { "seq.kalphite_queen_ranged_attack".asRSCM(RSCMType.SEQ) }
        assertDoesNotThrow { "seq.kalphite_queen_glow".asRSCM(RSCMType.SEQ) }
        assertDoesNotThrow { "spotanim.kalphite_queen_glow".asRSCM(RSCMType.SPOTANIM) }
        assertDoesNotThrow { "spotanim.kalphite_queen_spine".asRSCM(RSCMType.SPOTANIM) }
    }
}
