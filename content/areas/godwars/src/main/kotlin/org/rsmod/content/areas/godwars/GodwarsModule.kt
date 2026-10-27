package org.rsmod.content.areas.godwars

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.varp.bits
import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.module.PluginModule
import org.rsmod.utils.bits.bitMask

public class GodwarsModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcDeathKillHook>(GodwarsKillCountHook::class.java)
    }
}

internal class GodwarsKillCountHook @Inject constructor(private val areas: AreaChecker) :
    NpcDeathKillHook {
    override fun onKill(context: NpcDeathKillContext) {
        val player = context.hero
        val npc = context.npc
        if (!areas.inArea("area.godwars_dungeon", player.coords)) return
        ZAROS_ESSENCE[npc.visType.id]?.let {
            player.increment("varbit.godwars_counter_zaros", it)
            return
        }
        val faction = FACTIONS.firstOrNull { it.matches(npc) } ?: return
        player.increment(faction.counterVarbit, 1)
    }

    private fun Player.increment(varbit: String, amount: Int) {
        val next = vars[varbit] + amount
        val type = ServerCacheManager.getVarbit(varbit.asRSCM(RSCMType.VARBIT))
        val max = type?.bits?.bitMask ?: Int.MAX_VALUE.toLong()
        VarPlayerIntMapSetter.set(this, varbit, next.coerceAtMost(max.toInt()))
    }

    private class Faction(
        avatarName: String,
        private val bodyguardCategory: String,
        private val followerCategory: String,
        val counterVarbit: String,
    ) {
        val avatarId: Int by lazy { avatarName.asRSCM(RSCMType.NPC) }

        fun matches(npc: Npc): Boolean =
            npc.id == avatarId ||
                npc.visType.isCategoryType(bodyguardCategory) ||
                npc.visType.isCategoryType(followerCategory)
    }

    private companion object {
        private val FACTIONS =
            listOf(
                Faction(
                    avatarName = "npc.godwars_bandos_avatar",
                    bodyguardCategory = "category.godwars_bandos_bodyguard",
                    followerCategory = "category.godwars_bandos_follower",
                    counterVarbit = "varbit.godwars_counter_bandos",
                ),
                Faction(
                    avatarName = "npc.godwars_armadyl_avatar",
                    bodyguardCategory = "category.godwars_armadyl_bodyguard",
                    followerCategory = "category.godwars_armadyl_follower",
                    counterVarbit = "varbit.godwars_counter_armadyl",
                ),
                Faction(
                    avatarName = "npc.godwars_saradomin_avatar",
                    bodyguardCategory = "category.godwars_saradomin_bodyguard",
                    followerCategory = "category.godwars_saradomin_follower",
                    counterVarbit = "varbit.godwars_counter_saradomin",
                ),
                Faction(
                    avatarName = "npc.godwars_zamorak_avatar",
                    bodyguardCategory = "category.godwars_zamorak_bodyguard",
                    followerCategory = "category.godwars_zamorak_follower",
                    counterVarbit = "varbit.godwars_counter_zamorak",
                ),
            )

        // Zaros essence per npc id: warriors/rangers/bodyguards 2, mages/Nex 5, reavers 3.
        private val ZAROS_ESSENCE: Map<Int, Int> by lazy {
            mapOf(
                "npc.nex_prison_warrior" to 2,
                "npc.nex_prison_ranger" to 2,
                "npc.nex_prison_mage" to 5,
                "npc.nex_prison_blood_reaver" to 3,
                "npc.nex_prison_blood_reaver_boss" to 3,
                "npc.nex" to 5,
                "npc.nex_soulsplit" to 5,
                "npc.nex_deflect" to 5,
                "npc.nex_smokemage" to 2,
                "npc.nex_shadowmage" to 2,
                "npc.nex_bloodmage" to 2,
                "npc.nex_icemage" to 2,
            ).mapKeys { it.key.asRSCM(RSCMType.NPC) }
        }
    }
}
