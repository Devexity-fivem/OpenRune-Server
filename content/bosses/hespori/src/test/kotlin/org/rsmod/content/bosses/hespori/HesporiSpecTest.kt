package org.rsmod.content.bosses.hespori

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
class HesporiSpecTest {
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
                "npc.hespori",
                "npc.hespori_healer_inactive",
                "npc.hespori_healer_active",
            )) {
            assertDoesNotThrow { npc.asRSCM(RSCMType.NPC) }
        }
        assertDoesNotThrow { "seq.hespori_attack_ranged".asRSCM(RSCMType.SEQ) }
        assertDoesNotThrow { "seq.hespori_attack_special".asRSCM(RSCMType.SEQ) }
        assertDoesNotThrow { "spotanim.hespori_range_proj".asRSCM(RSCMType.SPOTANIM) }
        assertDoesNotThrow { "spotanim.hespori_magic_proj".asRSCM(RSCMType.SPOTANIM) }
        assertDoesNotThrow { "spotanim.hespori_magic_impact".asRSCM(RSCMType.SPOTANIM) }
        assertDoesNotThrow { "spotanim.hespori_vine_proj".asRSCM(RSCMType.SPOTANIM) }
        assertDoesNotThrow { "spotanim.hespori_vine_impact".asRSCM(RSCMType.SPOTANIM) }
    }

    @Test
    fun `hespori stats match wiki`() {
        val boss = ServerCacheManager.getNpc("npc.hespori".asRSCM(RSCMType.NPC))
        assertEquals(284, boss?.combatLevel)
        assertEquals(300, boss?.hitpoints)
    }

    @Test
    fun `hespori engages at ranged distance`() {
        val boss = ServerCacheManager.getNpc("npc.hespori".asRSCM(RSCMType.NPC))
        assertTrue((boss?.attackRange ?: 0) > 1) {
            "hespori type attackRange=${boss?.attackRange} — needs an npcs.toml override"
        }
    }

    @Test
    fun `flower buds die in one hit`() {
        val bud = ServerCacheManager.getNpc("npc.hespori_healer_active".asRSCM(RSCMType.NPC))
        assertEquals(10, bud?.hitpoints)
    }
}
