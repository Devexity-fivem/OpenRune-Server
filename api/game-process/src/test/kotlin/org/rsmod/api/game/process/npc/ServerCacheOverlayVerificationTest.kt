package org.rsmod.api.game.process.npc

import dev.openrune.ServerCacheManager
import dev.openrune.types.NpcServerType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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

    @Test
    fun `hellhound is tolerant melee aggressive`() {
        val def = npc(104)
        assertEquals(1, def.huntMode)
        assertEquals(5, def.huntRange)
        assertEquals(15, def.maxRange)
        assertEquals(1, param(def, PARAM_AGGRESSION_TOLERANT))
    }

    @Test
    fun `green dragon is wilderness aggressive without tolerance`() {
        val def = npc(260)
        assertEquals(2, def.huntMode)
        assertEquals(8, def.huntRange)
        assertEquals(10, def.attackRange)
        assertEquals(15, def.maxRange)
        assertNull(param(def, PARAM_AGGRESSION_TOLERANT))
    }

    @Test
    fun `chaos druid is tolerant ranged aggressive`() {
        val def = npc(520)
        assertEquals(2, def.huntMode)
        assertEquals(1, param(def, PARAM_AGGRESSION_TOLERANT))
    }

    @Test
    fun `chaos fanatic is aggressive but never tolerant in wilderness`() {
        val def = npc(6619)
        assertEquals(2, def.huntMode)
        assertNull(param(def, PARAM_AGGRESSION_TOLERANT))
    }

    @Test
    fun `aggressive black knight variant hunts while passive variant does not`() {
        assertEquals(1, npc(517).huntMode)
        assertEquals(1, param(npc(517), PARAM_AGGRESSION_TOLERANT))
        assertNull(npc(516).huntMode)
    }

    @Test
    fun `humanoid NPCs use human defend and death anims`() {
        // An earlier anim-fill pass assigned animal/monster anims to humans:
        // regicide elves and Tyras guards (rabbit), Khazard arena guards (scorpion),
        // Knights of Ardougne and Prifddinas guards (giant), barbarians (goblin),
        // paladins (spider), dark wizards and warriors (dark beast).
        val humans = intArrayOf(3428, 3429, 1208, 3297, 3055, 9182, 2851, 531, 4758, 1576, 8987, 517)
        for (id in humans) {
            assertEquals(425, param(npc(id), PARAM_DEFEND_ANIM), "npc $id defend") // human_unarmed_def
            assertEquals(836, param(npc(id), PARAM_DEATH_ANIM), "npc $id death") // human_death
        }
    }

    @Test
    fun `misc rooster uses rooster anim set`() {
        assertEquals(2300, param(npc(3663), PARAM_DEFEND_ANIM)) // roosterparry
        assertEquals(2301, param(npc(3663), PARAM_DEATH_ANIM)) // roosterdeath
    }

    @Test
    fun `zombies use reworked zombie anim set`() {
        assertEquals(5568, param(npc(26), PARAM_ATTACK_ANIM)) // zombie_update_attack_normal
        assertEquals(5567, param(npc(26), PARAM_DEFEND_ANIM)) // zombie_update_defend_normal
        assertEquals(5569, param(npc(26), PARAM_DEATH_ANIM)) // zombie_update_death_normal
        assertEquals(5571, param(npc(49), PARAM_ATTACK_ANIM)) // zombie_update_attack_weapon
        assertEquals(5574, param(npc(49), PARAM_DEFEND_ANIM)) // zombie_update_defend_weapon
        assertEquals(5575, param(npc(49), PARAM_DEATH_ANIM)) // zombie_update_death_weapon
    }

    @Test
    fun `wilderness slayer cave monsters use family anims`() {
        assertEquals(89, param(npc(7940), PARAM_DEFEND_ANIM)) // dragon_block
        assertEquals(92, param(npc(7940), PARAM_DEATH_ANIM)) // dragon_death
    }

    @Test
    fun `slayer monsters use family anims not swan queen`() {
        assertEquals(1581, param(npc(433), PARAM_DEFEND_ANIM)) // pyrefiend_defend
        assertEquals(1550, param(npc(484), PARAM_DEFEND_ANIM)) // bloodveld_defend
        assertEquals(1553, param(npc(484), PARAM_DEATH_ANIM)) // bloodveld_death
        assertEquals(1509, param(npc(2), PARAM_DEFEND_ANIM)) // specter_parry
        assertEquals(1508, param(npc(2), PARAM_DEATH_ANIM)) // specter_death
        assertEquals(1585, param(npc(437), PARAM_DEFEND_ANIM)) // jelly_defend
    }

    @Test
    fun `arena bouncer uses hellhound anims`() {
        assertEquals(158, param(npc(1224), PARAM_ATTACK_ANIM)) // hell_attack
        assertEquals(159, param(npc(1224), PARAM_DEFEND_ANIM)) // hell_block
        assertEquals(161, param(npc(1224), PARAM_DEATH_ANIM)) // hell_death
    }

    @Test
    fun `varlamore jaguar uses jaguar family anims`() {
        assertEquals(10848, param(npc(12976), PARAM_DEFEND_ANIM)) // npc_jaguar_human_unarmed_def
        assertEquals(12499, param(npc(12976), PARAM_DEATH_ANIM)) // npc_jaguar_combat_death
    }

    @Test
    fun `zamorakian creatures keep dedicated anim families`() {
        assertEquals(6950, param(npc(1610), PARAM_ATTACK_ANIM)) // godwars_zamorak_magic_attack
        assertEquals(4322, param(npc(2237), PARAM_DEFEND_ANIM)) // ork_update_defend
        assertEquals(4321, param(npc(2237), PARAM_DEATH_ANIM)) // ork_update_death
    }

    @Test
    fun `suqka uses suqka anims not giant`() {
        assertEquals(4384, param(npc(787), PARAM_DEFEND_ANIM)) // suqka_defend
        assertEquals(4389, param(npc(787), PARAM_DEATH_ANIM)) // suqka_death
    }

    @Test
    fun `zmi cultists use human anims not lizard`() {
        assertEquals(425, param(npc(7418), PARAM_DEFEND_ANIM)) // human_unarmed_def
        assertEquals(836, param(npc(7418), PARAM_DEATH_ANIM)) // human_death
    }

    @Test
    fun `demons use demon anim set not luc2 or leech`() {
        val demons = intArrayOf(415, 2005, 2006, 2007, 2008, 12362, 12364)
        for (id in demons) {
            assertEquals(65, param(npc(id), PARAM_DEFEND_ANIM), "npc $id defend") // demon_block
            assertEquals(67, param(npc(id), PARAM_DEATH_ANIM), "npc $id death") // demon_death
        }
    }

    @Test
    fun `slayer monsters use own family anims`() {
        assertEquals(1547, param(npc(417), PARAM_DEFEND_ANIM)) // basilisk_defend
        assertEquals(1548, param(npc(417), PARAM_DEATH_ANIM)) // basilisk_death
        assertEquals(1519, param(npc(412), PARAM_DEFEND_ANIM)) // gargoyle_parry
        assertEquals(1518, param(npc(412), PARAM_DEATH_ANIM)) // gargoyle_death
        assertEquals(1596, param(npc(427), PARAM_DEFEND_ANIM)) // turoth_defend
        assertEquals(1597, param(npc(427), PARAM_DEATH_ANIM)) // turoth_death
        assertEquals(2777, param(npc(458), PARAM_DEFEND_ANIM)) // slayer_lizard_defend
        assertEquals(2778, param(npc(458), PARAM_DEATH_ANIM)) // slayer_lizard_death
    }

    @Test
    fun `krakens use kraken anim set`() {
        for (id in intArrayOf(492, 494)) {
            assertEquals(13214, param(npc(id), PARAM_DEFEND_ANIM), "npc $id defend")
            assertEquals(13219, param(npc(id), PARAM_DEATH_ANIM), "npc $id death")
        }
    }

    @Test
    fun `strykewyrm uses lava strykewyrm anim set`() {
        assertEquals(13654, param(npc(15500), PARAM_ATTACK_ANIM)) // melee01
        assertEquals(13646, param(npc(15500), PARAM_DEFEND_ANIM)) // idle01
        assertEquals(13652, param(npc(15500), PARAM_DEATH_ANIM)) // death01
    }

    @Test
    fun `dragons use dragon anim set not canine`() {
        val dragons = intArrayOf(2918, 5878, 5879, 5880, 5881, 5882)
        for (id in dragons) {
            assertEquals(89, param(npc(id), PARAM_DEFEND_ANIM), "npc $id defend") // dragon_block
            assertEquals(92, param(npc(id), PARAM_DEATH_ANIM), "npc $id death") // dragon_death
        }
    }

    @Test
    fun `trolls use troll anim set not ghost or wolf`() {
        val trolls = intArrayOf(4120, 4121, 4122, 4123, 4124)
        for (id in trolls) {
            assertEquals(285, param(npc(id), PARAM_DEFEND_ANIM), "npc $id defend") // troll_block
            assertEquals(287, param(npc(id), PARAM_DEATH_ANIM), "npc $id death") // troll_death
        }
    }

    @Test
    fun `nex prison humans use human anims not blood reaver`() {
        val humans = intArrayOf(11290, 11291, 11292)
        for (id in humans) {
            assertEquals(425, param(npc(id), PARAM_DEFEND_ANIM), "npc $id defend")
            assertEquals(836, param(npc(id), PARAM_DEATH_ANIM), "npc $id death")
        }
    }

    @Test
    fun `elite vyrewatch use vampire flying anims not ork`() {
        assertEquals(4691, param(npc(9756), PARAM_DEFEND_ANIM)) // myq3_vampire_flying_defend
        assertEquals(4697, param(npc(9756), PARAM_DEATH_ANIM)) // myq3_vampire_flying_death
    }

    @Test
    fun `canine monsters use matching canine anims`() {
        assertEquals(76, param(npc(114), PARAM_DEFEND_ANIM)) // dog_block
        assertEquals(78, param(npc(114), PARAM_DEATH_ANIM)) // dog_death
        assertEquals(6562, param(npc(4185), PARAM_ATTACK_ANIM)) // small_dog_attack
        assertEquals(76, param(npc(4185), PARAM_DEFEND_ANIM))
        assertEquals(78, param(npc(4185), PARAM_DEATH_ANIM))
        assertEquals(241, param(npc(2492), PARAM_DEFEND_ANIM)) // rat_block
        assertEquals(243, param(npc(2492), PARAM_DEATH_ANIM)) // rat_death
        assertEquals(12825, param(npc(2481), PARAM_DEFEND_ANIM)) // lore_minotaur_defend
        assertEquals(12827, param(npc(2481), PARAM_DEATH_ANIM)) // lore_minotaur_death
    }

    @Test
    fun `misc creature family anims`() {
        assertEquals(1677, param(npc(319), PARAM_DEFEND_ANIM)) // corpbeast_defend
        assertEquals(1676, param(npc(319), PARAM_DEATH_ANIM)) // corpbeast_death
        assertEquals(12703, param(npc(8610), PARAM_DEFEND_ANIM)) // lore_wyrm_defend
        assertEquals(8272, param(npc(8610), PARAM_DEATH_ANIM)) // wyrm_death
        assertEquals(1340, param(npc(973), PARAM_DEFEND_ANIM)) // horror_dagannoth_defend
        assertEquals(1342, param(npc(973), PARAM_DEATH_ANIM)) // horror_dagannoth_death
        assertEquals(4936, param(npc(1792), PARAM_ATTACK_ANIM)) // desert_goat_attack
        assertEquals(5342, param(npc(1792), PARAM_DEFEND_ANIM)) // goat_update_defend
        assertEquals(2039, param(npc(4184), PARAM_ATTACK_ANIM)) // croc_attack
        assertEquals(2035, param(npc(4184), PARAM_DEFEND_ANIM)) // croc_parry
        assertEquals(2038, param(npc(4184), PARAM_DEATH_ANIM)) // croc_death
        assertEquals(221, param(npc(1038), PARAM_DEFEND_ANIM)) // monkey_block
        assertEquals(276, param(npc(3544), PARAM_DEFEND_ANIM)) // snake_block
        assertEquals(278, param(npc(3544), PARAM_DEATH_ANIM)) // snake_death
        assertEquals(261, param(npc(4494), PARAM_DEFEND_ANIM)) // skeleton_block
        assertEquals(263, param(npc(4494), PARAM_DEATH_ANIM)) // skeleton_death
        assertEquals(1200, param(npc(4708), PARAM_DEFEND_ANIM)) // lion_block (Shaikahan)
        assertEquals(1201, param(npc(4708), PARAM_DEATH_ANIM)) // lion_death
        assertEquals(1581, param(npc(2917), PARAM_DEFEND_ANIM)) // pyrefiend_defend
        assertEquals(1580, param(npc(2917), PARAM_DEATH_ANIM)) // pyrefiend_death
    }

    @Test
    fun `quest and boss NPCs keep correct family anims`() {
        assertEquals(6090, param(npc(3902), PARAM_ATTACK_ANIM)) // rabbit_update_attack (Bunny)
        assertEquals(6091, param(npc(3902), PARAM_DEFEND_ANIM)) // rabbit_update_defend
        assertEquals(6092, param(npc(3902), PARAM_DEATH_ANIM)) // rabbit_update_death
        assertEquals(425, param(npc(4922), PARAM_DEFEND_ANIM)) // Ice Queen human_unarmed_def
        assertEquals(836, param(npc(4922), PARAM_DEATH_ANIM)) // human_death
        assertEquals(8834, param(npc(10506), PARAM_DEFEND_ANIM)) // bear_headless_beast_defend
        assertEquals(2733, param(npc(10506), PARAM_DEATH_ANIM)) // dark_beast_update_death
        assertEquals(12492, param(npc(13011), PARAM_DEATH_ANIM)) // blood_jaguar_combat_death
        assertEquals(12499, param(npc(12978), PARAM_DEATH_ANIM)) // npc_jaguar_combat_death
    }

    @Test
    fun `al kharid warrior is non-aggressive but calls same-type allies`() {
        val def = npc(3292)
        assertNull(def.huntMode)
        assertEquals(5, param(def, PARAM_AGGRO_CALL_HELP))
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
        private const val PARAM_AGGRESSION_TOLERANT = 65560
        private const val PARAM_AGGRO_CALL_HELP = 65574

        @JvmStatic
        @BeforeAll
        fun cache() {
            ServerCacheManager.init(240).close()
        }
    }
}
