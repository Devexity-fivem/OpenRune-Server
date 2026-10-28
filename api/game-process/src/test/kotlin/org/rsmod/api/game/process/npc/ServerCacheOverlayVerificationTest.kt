package org.rsmod.api.game.process.npc

import dev.openrune.ServerCacheManager
import dev.openrune.types.NpcServerType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock

/**
 * Verifies that duplicate `[[npc]]` TOML blocks (same id across `npcs.toml` and pack
 * `extraDirectories`) merge into the packed SERVER cache instead of last-wins
 * dropping fields. Requires `.data/cache/SERVER` (run `gradlew :or-cache:buildCache`).
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class ServerCacheOverlayVerificationTest {
    private fun npc(id: Int): NpcServerType {
        val def = ServerCacheManager.getNpcs()[id]
        assertNotNull(def, "npc $id missing from server cache")
        return def!!
    }

    private fun param(def: NpcServerType, id: Int): Any? = def.paramsRaw?.get(id)

    @Test
    fun `callisto keeps hunt anims and killcount fields from both overlay blocks`() {
        val def = npc(6609)
        assertEquals(1, def.huntMode)
        assertEquals(30, def.huntRange)
        assertEquals(30, def.maxRange)
        assertEquals(1, def.attackRange)
        assertEquals(11, def.respawnRate)
        assertEquals(65534, param(def, PARAM_ATTACK_TYPE)) // attacktype_crush
        assertEquals(10017, param(def, PARAM_DEATH_ANIM)) // seq.npc_callisto_death01
        assertEquals(1510, param(def, PARAM_KC_VARP)) // varp.total_callisto_kills
        assertEquals(1, param(def, PARAM_BOSS_HP_BAR))
        assertEquals(3, param(def, PARAM_WEAKNESS_TYPE))
        assertEquals(30, param(def, PARAM_WEAKNESS_PERCENT))
    }

    @Test
    fun `bandos avatar keeps hunt config anims and killcount varp`() {
        val def = npc(2215)
        assertEquals(6, def.huntMode)
        assertEquals(30, def.huntRange)
        assertEquals(30, def.maxRange)
        assertEquals(7018, param(def, PARAM_ATTACK_ANIM)) // seq.godwars_bandos_attack
        assertEquals(7019, param(def, PARAM_DEFEND_ANIM)) // seq.godwars_bandos_defend
        assertEquals(7020, param(def, PARAM_DEATH_ANIM)) // seq.godwars_bandos_death
        assertEquals(1504, param(def, PARAM_KC_VARP)) // varp.total_bandos_kills
    }

    @Test
    fun `saltfish fishing spot keeps respawn rate`() {
        val def = npc(1530)
        assertEquals(50, def.respawnRate)
        assertEquals(65533, def.contentGroup) // content.net_bait_fishing_spot
    }

    @Test
    fun `lesser demon keeps server params under pack wanderRange overlay`() {
        val def = npc(2005)
        assertEquals(2, def.wanderRange)
        assertEquals(64, param(def, PARAM_ATTACK_ANIM)) // seq.demon_attack
        assertEquals(65533, param(def, PARAM_ATTACK_TYPE)) // attacktype_slash
        assertEquals(1, param(def, PARAM_DEMON))
        assertEquals(1, param(def, PARAM_WEAKNESS_TYPE))
        assertEquals(40, param(def, PARAM_WEAKNESS_PERCENT))
    }

    @Test
    fun `zamorak spiritual warrior keeps spiritual killcount varp`() {
        val def = npc(3159)
        // Duplicate blocks disagreed (total_armadyl_kills vs kc_spiritual_warrior);
        // the later block and the live cache agree on kc_spiritual_warrior (65602).
        assertEquals(65602, param(def, PARAM_KC_VARP))
    }

    companion object {
        private const val PARAM_ATTACK_TYPE = 65407
        private const val PARAM_ATTACK_ANIM = 65500
        private const val PARAM_DEFEND_ANIM = 65501
        private const val PARAM_DEATH_ANIM = 65502
        private const val PARAM_BOSS_HP_BAR = 65526
        private const val PARAM_KC_VARP = 65551
        private const val PARAM_WEAKNESS_TYPE = 65404
        private const val PARAM_WEAKNESS_PERCENT = 65405
        private const val PARAM_DEMON = 65432

        @JvmStatic
        @BeforeAll
        fun cache() {
            ServerCacheManager.init(240).close()
        }
    }
}
