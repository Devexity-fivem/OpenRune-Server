package org.rsmod.content.skills.magic.lunar

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.player.events.PlayerHitEvents
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.script.onPlayerQueue
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.hit.HitType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class LunarVengeanceScript
@Inject
constructor(
    private val spells: MagicSpellRegistry,
    private val runes: MagicRuneManager,
    private val npcHitModifier: NpcHitModifier,
    private val playerHitModifier: PlayerHitModifier,
    private val npcList: NpcList,
    private val playerList: PlayerList,
) : PluginScript() {
    private var Player.vengeanceActive by boolVarBit("varbit.lunar_vengeance_active")
    private var Player.vengeanceCooldown by boolVarBit("varbit.lunar_vengeance_cooldown")

    override fun ScriptContext.startup() {
        onIfOverlayButton("component.magic_spellbook:vengeance") { cast() }
        onPlayerQueue(COOLDOWN_QUEUE) { player.vengeanceCooldown = false }
        onEvent<PlayerHitEvents.Impact> { reflect() }
    }

    private suspend fun ProtectedAccess.cast() {
        if (!QuestRequirements.hasCompleted(player, REQUIRED_QUEST)) {
            mes("You need to have completed Lunar Diplomacy to cast this spell.")
            return
        }
        if (player.vengeanceCooldown) {
            mes("You can only cast vengeance once every 30 seconds.")
            return
        }
        if (player.vengeanceActive) {
            mes("You already have vengeance cast.")
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
        player.vengeanceActive = true
        player.vengeanceCooldown = true
        queue(COOLDOWN_QUEUE, COOLDOWN_TICKS)
    }

    private fun PlayerHitEvents.Impact.reflect() {
        if (!player.vengeanceActive || hit.type == HitType.Typeless || hit.damage <= 0) {
            return
        }
        player.vengeanceActive = false
        val reflected = hit.damage * REFLECT_PERCENT / 100
        if (reflected <= 0) {
            return
        }
        if (hit.isFromNpc) {
            val npc = hit.resolveNpcSource(npcList) ?: return
            npc.queueHit(player, 0, HitType.Typeless, reflected, npcHitModifier)
        } else if (hit.isFromPlayer) {
            val attacker = hit.resolvePlayerSource(playerList) ?: return
            attacker.queueHit(player, 0, HitType.Typeless, reflected, playerHitModifier)
        }
    }

    private companion object {
        const val SPELL_OBJ = "obj.94_vengeance"
        const val REQUIRED_QUEST = "quest_lunardiplomacy"
        const val CAST_ANIM = "seq.human_cast_selfimbue"
        const val CAST_SPOTANIM = "spotanim.quest_lunar_spellbook_vengeance_spot_anim"
        const val COOLDOWN_QUEUE = "queue.lunar_vengeance_cooldown"
        const val COOLDOWN_TICKS = 50
        const val REFLECT_PERCENT = 75
    }
}
