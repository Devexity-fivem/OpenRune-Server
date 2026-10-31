package org.rsmod.api.npc.aggression

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcServerType
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.config.refs.params
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.type.getOrNull

/**
 * Data-driven conditions read from npc params/categories. Each check maps to an OSRS rule such as
 * god-item protection in the God Wars Dungeon, greegree disguises on Ape Atoll, or the Desert
 * Bandit camp's hostility toward god-aligned gear.
 */
@Singleton
public class ConditionalAggression @Inject constructor() : AggressionGate {
    override fun permitsAggression(npc: Npc, player: Player): Boolean {
        val type = npc.type
        if (!hasGate(type)) {
            return true
        }

        val faction = factionBits(type)
        val triggers = type.paramOrNull(params.aggro_if_goditem) ?: 0
        if (faction != 0 || triggers != 0) {
            val worn = wornFactionBits(player)
            if (faction != 0 && worn and faction != 0) {
                return false
            }
            if (triggers != 0 && worn and triggers == 0) {
                return false
            }
        }

        if (type.paramOrNull(params.aggro_unless_greegree) == 1 &&
            wornParamCount(player, params.greegree.id) > 0
        ) {
            return false
        }

        if (type.paramOrNull(params.aggro_unless_vyre_gear) == 1 &&
            wornParamCount(player, params.vyre_noble.id) >= VYRE_SET_SIZE
        ) {
            return false
        }

        if (type.paramOrNull(params.aggro_unless_mourner_gear) == 1 &&
            wornParamCount(player, params.mourner_gear.id) >= MOURNER_SET_SIZE
        ) {
            return false
        }

        if (type.paramOrNull(params.aggro_if_weapon) == 1 &&
            player.worn[Wearpos.RightHand.slot] == null
        ) {
            return false
        }

        if (type.paramOrNull(params.aggro_if_armed) == 1 && !player.isArmed()) {
            return false
        }

        val unlessObj = type.paramOrNull(params.aggro_unless_obj)
        if (unlessObj != null && player.worn.any { it?.id == unlessObj.id }) {
            return false
        }

        return true
    }

    private fun hasGate(type: NpcServerType): Boolean =
        factionBits(type) != 0 ||
            type.paramOrNull(params.aggro_if_goditem) != null ||
            type.paramOrNull(params.aggro_unless_greegree) != null ||
            type.paramOrNull(params.aggro_unless_vyre_gear) != null ||
            type.paramOrNull(params.aggro_unless_mourner_gear) != null ||
            type.paramOrNull(params.aggro_if_weapon) != null ||
            type.paramOrNull(params.aggro_if_armed) != null ||
            type.paramOrNull(params.aggro_unless_obj) != null

    private fun factionBits(type: NpcServerType): Int {
        val category = CATEGORY_BITS[type.category] ?: 0
        val param = type.paramOrNull(params.aggro_faction) ?: 0
        return category or param
    }

    private fun wornFactionBits(player: Player): Int {
        var bits = 0
        for (obj in player.worn) {
            val type = getOrNull(obj) ?: continue
            bits = bits or ((type.paramOrNull(params.godwars_faction)) ?: 0)
        }
        return bits
    }

    private fun wornParamCount(player: Player, paramId: Int): Int {
        var count = 0
        for (obj in player.worn) {
            val type = getOrNull(obj) ?: continue
            val value = type.paramMap?.primitiveMap?.get(paramId) ?: continue
            if (value is Int && value != 0) {
                count++
            }
        }
        return count
    }

    private fun Player.isArmed(): Boolean {
        if (worn[Wearpos.RightHand.slot] != null || worn[Wearpos.LeftHand.slot] != null) {
            return true
        }
        return ARMOUR_WEARPOS.any { worn[it.slot] != null }
    }

    public companion object {
        public const val FACTION_SARADOMIN: Int = 1
        public const val FACTION_ZAMORAK: Int = 2
        public const val FACTION_BANDOS: Int = 4
        public const val FACTION_ARMADYL: Int = 8
        public const val FACTION_ZAROS: Int = 16

        private const val VYRE_SET_SIZE = 3
        private const val MOURNER_SET_SIZE = 5

        private val ARMOUR_WEARPOS =
            listOf(
                Wearpos.Hat,
                Wearpos.Back,
                Wearpos.Torso,
                Wearpos.Legs,
                Wearpos.Hands,
                Wearpos.Feet,
                Wearpos.Quiver,
            )

        private val CATEGORY_BITS: Map<Int, Int> =
            mapOf(
                "category.godwars_saradomin_follower".asRSCM(RSCMType.CATEGORY) to FACTION_SARADOMIN,
                "category.godwars_zamorak_follower".asRSCM(RSCMType.CATEGORY) to FACTION_ZAMORAK,
                "category.godwars_bandos_follower".asRSCM(RSCMType.CATEGORY) to FACTION_BANDOS,
                "category.godwars_armadyl_follower".asRSCM(RSCMType.CATEGORY) to FACTION_ARMADYL,
                "category.godwars_saradomin_bodyguard".asRSCM(RSCMType.CATEGORY) to FACTION_SARADOMIN,
                "category.godwars_zamorak_bodyguard".asRSCM(RSCMType.CATEGORY) to FACTION_ZAMORAK,
                "category.godwars_bandos_bodyguard".asRSCM(RSCMType.CATEGORY) to FACTION_BANDOS,
                "category.godwars_armadyl_bodyguard".asRSCM(RSCMType.CATEGORY) to FACTION_ARMADYL,
            )
    }
}
