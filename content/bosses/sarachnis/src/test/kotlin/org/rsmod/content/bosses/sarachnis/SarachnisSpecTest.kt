package org.rsmod.content.bosses.sarachnis

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
        for (npc in
            listOf("npc.sarachnis", "npc.sarachnis_melee_spawn", "npc.sarachnis_mage_spawn")) {
            assertDoesNotThrow { npc.asRSCM(RSCMType.NPC) }
        }
        assertDoesNotThrow { "seq.giantspider_attack".asRSCM(RSCMType.SEQ) }
        assertDoesNotThrow { "spotanim.sarachnis_rangeproj".asRSCM(RSCMType.SPOTANIM) }
        assertDoesNotThrow { "spotanim.sarachnis_web_proj".asRSCM(RSCMType.SPOTANIM) }
    }

    @Test
    fun `sarachnis stats match wiki`() {
        val boss = ServerCacheManager.getNpc("npc.sarachnis".asRSCM(RSCMType.NPC))
        assertEquals(318, boss?.combatLevel)
    }

    @Test
    fun `sarachnis engages at ranged distance`() {
        val boss = ServerCacheManager.getNpc("npc.sarachnis".asRSCM(RSCMType.NPC))
        assertTrue((boss?.attackRange ?: 0) > 1) {
            "sarachnis type attackRange=${boss?.attackRange} — needs an npcs.toml override"
        }
    }
}
