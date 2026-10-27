package org.rsmod.content.bosses.skotizo

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
class SkotizoSpecTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun loadCache() {
            ServerCacheManager.init(240)
        }
    }

    @Test
    fun `spec symbols resolve`() {
        assertDoesNotThrow { "npc.cata_boss".asRSCM(RSCMType.NPC) }
        assertDoesNotThrow { "npc.cata_boss_minion".asRSCM(RSCMType.NPC) }
        assertDoesNotThrow { "npc.cata_boss_ankou".asRSCM(RSCMType.NPC) }
        for (dir in listOf("n", "e", "s", "w")) {
            assertDoesNotThrow { "npc.cata_boss_altar_closed_$dir".asRSCM(RSCMType.NPC) }
            assertDoesNotThrow { "npc.cata_boss_altar_open_$dir".asRSCM(RSCMType.NPC) }
        }
        assertDoesNotThrow { "seq.demon_update_attack".asRSCM(RSCMType.SEQ) }
        assertDoesNotThrow { "seq.demon_update_fireball_cast".asRSCM(RSCMType.SEQ) }
        assertDoesNotThrow { "spotanim.godwars_black_demon_fireball_proj".asRSCM(RSCMType.SPOTANIM) }
        assertDoesNotThrow { "spotanim.godwars_black_demon_fireball_spot".asRSCM(RSCMType.SPOTANIM) }
        assertDoesNotThrow { "obj.arclight".asRSCM(RSCMType.OBJ) }
        assertDoesNotThrow { "obj.emberlight".asRSCM(RSCMType.OBJ) }
    }

    @Test
    fun `skotizo and minions match wiki stats`() {
        val boss = ServerCacheManager.getNpc("npc.cata_boss".asRSCM(RSCMType.NPC))
        val minion = ServerCacheManager.getNpc("npc.cata_boss_minion".asRSCM(RSCMType.NPC))
        val altar = ServerCacheManager.getNpc("npc.cata_boss_altar_open_n".asRSCM(RSCMType.NPC))
        assertEquals(450, boss?.hitpoints)
        assertEquals(321, boss?.combatLevel)
        assertEquals(85, minion?.hitpoints)
        assertEquals(100, altar?.hitpoints)
    }
}
