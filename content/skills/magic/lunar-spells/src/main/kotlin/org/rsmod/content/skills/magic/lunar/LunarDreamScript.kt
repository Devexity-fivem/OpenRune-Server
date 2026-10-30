package org.rsmod.content.skills.magic.lunar

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.player.events.PlayerHitEvents
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.stat.statHeal
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.script.onPlayerTimer
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class LunarDreamScript
@Inject
constructor(private val spells: MagicSpellRegistry, private val runes: MagicRuneManager) :
    PluginScript() {
    private var Player.dreaming by boolVarBit("varbit.lunar_dreaming")

    override fun ScriptContext.startup() {
        onIfOverlayButton("component.magic_spellbook:dream") { cast() }
        onPlayerTimer(DREAM_TIMER) { tick() }
        onEvent<PlayerHitEvents.Impact> { wakeOnHit() }
    }

    private suspend fun ProtectedAccess.cast() {
        if (!QuestRequirements.hasCompleted(player, REQUIRED_QUEST)) {
            mes("You need to have completed Dream Mentor to cast this spell.")
            return
        }
        if (player.dreaming) {
            return
        }
        if (player.hitpoints >= player.statBase("stat.hitpoints")) {
            mes("You are already at full health.")
            return
        }
        val item = ServerCacheManager.getItem(SPELL_OBJ.asRSCM(RSCMType.OBJ)) ?: return
        val spell = spells.getObjSpell(item) ?: return
        if (runes.attemptCast(player, spell).isFailure()) {
            return
        }
        anim(CAST_ANIM)
        spotanim(CAST_SPOTANIM)
        statAdvance("stat.magic", spell.castXp)
        player.dreaming = true
        player.timer(DREAM_TIMER, DREAM_TICKS)
        mes("You fall asleep and start to dream.")
    }

    private fun ProtectedAccess.tick() {
        if (!player.dreaming) {
            return
        }
        if (player.isBusy2 || player.hasMovedThisCycle) {
            wake()
            return
        }
        if (player.hitpoints >= player.statBase("stat.hitpoints")) {
            wake()
            return
        }
        player.statHeal("stat.hitpoints", HEAL_AMOUNT, 0)
        player.timer(DREAM_TIMER, DREAM_TICKS)
    }

    private fun PlayerHitEvents.Impact.wakeOnHit() {
        if (player.dreaming && hit.damage > 0) {
            player.dreaming = false
            player.clearTimer(DREAM_TIMER)
            player.mes("You wake up from your dream.")
        }
    }

    private fun ProtectedAccess.wake() {
        player.dreaming = false
        player.clearTimer(DREAM_TIMER)
        mes("You wake up from your dream.")
    }

    private companion object {
        const val SPELL_OBJ = "obj.79_dream"
        const val REQUIRED_QUEST = "quest_dreammentor"
        const val CAST_ANIM = "seq.human_cast_selfimbue"
        const val CAST_SPOTANIM = "spotanim.dream_spellbook_spotanim"
        const val DREAM_TIMER = "timer.lunar_dream"
        const val DREAM_TICKS = 33
        const val HEAL_AMOUNT = 1
    }
}
