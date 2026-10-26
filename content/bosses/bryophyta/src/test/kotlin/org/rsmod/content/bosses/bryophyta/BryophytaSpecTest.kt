package org.rsmod.content.bosses.bryophyta

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
class BryophytaSpecTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun loadCache() {
            ServerCacheManager.init(240)
        }
    }

    @Test
    fun `spec symbols resolve`() {
        assertDoesNotThrow { "npc.gb_mossgiant".asRSCM(RSCMType.NPC) }
        assertDoesNotThrow { "npc.gb_growthling".asRSCM(RSCMType.NPC) }
        assertDoesNotThrow { "seq.giant_attack".asRSCM(RSCMType.SEQ) }
        assertDoesNotThrow { "spotanim.earthbolt_travel".asRSCM(RSCMType.SPOTANIM) }
        assertDoesNotThrow { "varn.bryophyta_summon_cd".asRSCM(RSCMType.VARN) }
        assertDoesNotThrow { "obj.secateurs".asRSCM(RSCMType.OBJ) }
        assertDoesNotThrow { "obj.fairy_enchanted_secateurs".asRSCM(RSCMType.OBJ) }
        assertDoesNotThrow { "content.woodcutting_axe".asRSCM(RSCMType.CONTENT) }
    }
}
