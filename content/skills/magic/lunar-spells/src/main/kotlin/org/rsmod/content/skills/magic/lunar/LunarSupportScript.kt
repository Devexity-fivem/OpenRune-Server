package org.rsmod.content.skills.magic.lunar

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.mechanics.toxins.impl.PlayerPoison
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.statHeal
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.script.onOpPlayerT
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class LunarSupportScript
@Inject
constructor(
    private val spells: MagicSpellRegistry,
    private val runes: MagicRuneManager,
    private val playerList: PlayerList,
) : PluginScript() {
    private var Player.vengeanceActive by boolVarBit("varbit.lunar_vengeance_active")

    override fun ScriptContext.startup() {
        onOpPlayerT("component.magic_spellbook:cure_other") {
            castOn(CURE_OTHER_OBJ, it.target, { t -> isPoisoned(t) }, { t -> cure(t) })
        }
        onOpPlayerT("component.magic_spellbook:heal_other") {
            castOn(HEAL_OTHER_OBJ, it.target, { hasHealHp() }, { t -> healOther(t) })
        }
        onOpPlayerT("component.magic_spellbook:vengeance_other") {
            castOn(VENGEANCE_OTHER_OBJ, it.target, { t -> canVengeance(t) }, { t -> vengeanceOther(t) })
        }
        onOpPlayerT("component.magic_spellbook:energy_trans") {
            castOn(ENERGY_TRANS_OBJ, it.target, { hasTransferEnergy() }, { t -> energyTransfer(t) })
        }
        onIfOverlayButton("component.magic_spellbook:cure_group") {
            cast(CURE_GROUP_OBJ, { true }, { cureGroup() })
        }
        onIfOverlayButton("component.magic_spellbook:heal_group") {
            cast(HEAL_GROUP_OBJ, { canHealGroup() }, { healGroup() })
        }
    }

    private suspend fun ProtectedAccess.castOn(
        spellObj: String,
        target: Player,
        canCast: ProtectedAccess.(Player) -> Boolean,
        action: ProtectedAccess.(Player) -> Unit,
    ) {
        if (!hasQuest()) {
            return
        }
        if (target.vars["varbit.option_acceptaid"] != 1) {
            mes("This player is not accepting aid.")
            return
        }
        val item = ServerCacheManager.getItem(spellObj.asRSCM(RSCMType.OBJ)) ?: return
        val spell = spells.getObjSpell(item) ?: return
        if (!canCast(target) || runes.attemptCast(player, spell).isFailure()) {
            return
        }
        anim(CAST_ANIM)
        delay(CAST_TICKS)
        action(target)
        statAdvance("stat.magic", spell.castXp)
    }

    private suspend fun ProtectedAccess.cast(
        spellObj: String,
        canCast: ProtectedAccess.() -> Boolean,
        action: ProtectedAccess.() -> Unit,
    ) {
        if (!hasQuest()) {
            return
        }
        val item = ServerCacheManager.getItem(spellObj.asRSCM(RSCMType.OBJ)) ?: return
        val spell = spells.getObjSpell(item) ?: return
        if (!canCast() || runes.attemptCast(player, spell).isFailure()) {
            return
        }
        anim(CAST_ANIM)
        delay(CAST_TICKS)
        action()
        statAdvance("stat.magic", spell.castXp)
    }

    private fun ProtectedAccess.hasQuest(): Boolean {
        if (!QuestRequirements.hasCompleted(player, REQUIRED_QUEST)) {
            mes("You need to have completed Lunar Diplomacy to cast this spell.")
            return false
        }
        return true
    }

    private fun ProtectedAccess.nearbyAllies(): List<Player> =
        playerList.filter {
            it != player &&
                it.vars["varbit.option_acceptaid"] == 1 &&
                it.coords.chebyshevDistance(player.coords) <= 1
        }

    private fun ProtectedAccess.isPoisoned(target: Player): Boolean {
        if (PlayerPoison.isPoisoned(target)) {
            return true
        }
        mes("That player is not poisoned.")
        return false
    }

    private fun ProtectedAccess.cure(target: Player) {
        PlayerPoison.clear(target)
        target.spotanim(CURE_OTHER_SPOTANIM)
    }

    private fun ProtectedAccess.cureGroup() {
        var cured = 0
        if (PlayerPoison.isPoisoned(player)) {
            PlayerPoison.clear(player)
            cured++
        }
        for (ally in nearbyAllies()) {
            if (PlayerPoison.isPoisoned(ally)) {
                PlayerPoison.clear(ally)
                ally.spotanim(CURE_GROUP_SPOTANIM)
                cured++
            }
        }
        if (cured == 0) {
            mes("No one nearby is poisoned.")
        }
    }

    private fun ProtectedAccess.hasHealHp(): Boolean {
        if (player.hitpoints < MIN_CASTER_HP) {
            mes("You need more hitpoints to cast this spell.")
            return false
        }
        return true
    }

    private fun ProtectedAccess.healOther(target: Player) {
        val amount = player.hitpoints * TRANSFER_PERCENT / 100
        player.statSub("stat.hitpoints", amount, 0)
        target.statHeal("stat.hitpoints", amount, 0)
        target.spotanim(HEAL_SPOTANIM)
    }

    private fun ProtectedAccess.canHealGroup(): Boolean {
        if (player.hitpoints < MIN_CASTER_HP) {
            mes("You need more hitpoints to cast this spell.")
            return false
        }
        if (nearbyAllies().isEmpty()) {
            mes("There are no players nearby accepting aid to heal.")
            return false
        }
        return true
    }

    private fun ProtectedAccess.healGroup() {
        val allies = nearbyAllies().take(MAX_HEAL_GROUP_TARGETS)
        val total = player.hitpoints * TRANSFER_PERCENT / 100
        val share = total / allies.size
        if (share <= 0) {
            return
        }
        player.statSub("stat.hitpoints", share * allies.size, 0)
        for (ally in allies) {
            ally.statHeal("stat.hitpoints", share, 0)
            ally.spotanim(HEAL_GROUP_SPOTANIM)
        }
    }

    private fun ProtectedAccess.canVengeance(target: Player): Boolean {
        if (target.vengeanceActive) {
            mes("That player already has vengeance cast.")
            return false
        }
        return true
    }

    private fun ProtectedAccess.vengeanceOther(target: Player) {
        target.vengeanceActive = true
        target.spotanim(VENGEANCE_OTHER_SPOTANIM)
    }

    private fun ProtectedAccess.hasTransferEnergy(): Boolean {
        if (player.runEnergy < ENERGY_TRANSFER_COST || player.vars["varp.sa_energy"] < 1000) {
            mes("You need 100% run energy and special attack energy to cast this spell.")
            return false
        }
        return true
    }

    private fun ProtectedAccess.energyTransfer(target: Player) {
        val spec = player.vars["varp.sa_energy"]
        val run = player.runEnergy
        VarPlayerIntMapSetter.set(player, "varp.sa_energy", 0)
        player.runEnergy = 0
        VarPlayerIntMapSetter.set(
            target,
            "varp.sa_energy",
            (target.vars["varp.sa_energy"] + spec).coerceAtMost(1000),
        )
        target.runEnergy = (target.runEnergy + run).coerceAtMost(1000)
        target.spotanim(ENERGY_SPOTANIM)
    }

    private companion object {
        const val REQUIRED_QUEST = "quest_lunardiplomacy"
        const val CAST_ANIM = "seq.human_cast_selfimbue"
        const val CAST_TICKS = 2
        const val TRANSFER_PERCENT = 75
        const val MIN_CASTER_HP = 11
        const val MAX_HEAL_GROUP_TARGETS = 4
        const val ENERGY_TRANSFER_COST = 1000

        const val CURE_OTHER_OBJ = "obj.68_cure_other"
        const val HEAL_OTHER_OBJ = "obj.92_heal_other"
        const val VENGEANCE_OTHER_OBJ = "obj.93_vengeance_other"
        const val ENERGY_TRANS_OBJ = "obj.91_energy_transfer"
        const val CURE_GROUP_OBJ = "obj.74_cure_group"
        const val HEAL_GROUP_OBJ = "obj.95_heal_group"

        const val CURE_OTHER_SPOTANIM =
            "spotanim.quest_lunar_spellbook_energy_cure_spot_anim"
        const val CURE_GROUP_SPOTANIM =
            "spotanim.quest_lunar_spellbook_cure_group_spot_anim"
        const val HEAL_SPOTANIM = "spotanim.quest_lunar_spellbook_energy_heal_spot_anim"
        const val HEAL_GROUP_SPOTANIM =
            "spotanim.quest_lunar_spellbook_heal_group_spot_anim"
        const val ENERGY_SPOTANIM = "spotanim.quest_lunar_spellbook_energy_exchange_spot_anim"
        const val VENGEANCE_OTHER_SPOTANIM =
            "spotanim.quest_lunar_spellbook_vengeance_other_spot_anim"
    }
}
