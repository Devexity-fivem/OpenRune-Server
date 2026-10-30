package org.rsmod.content.skills.magic.lunar

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.magic.MagicSpell
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.player.output.UpdateInventory.resendSlot
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.player.ui.IfOverlayButtonT
import org.rsmod.api.script.onEvent
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.isType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class LunarPlankMakeScript
@Inject
constructor(
    private val eventBus: EventBus,
    private val protectedAccess: ProtectedAccessLauncher,
    private val spells: MagicSpellRegistry,
    private val runes: MagicRuneManager,
) : PluginScript() {
    override fun ScriptContext.startup() {
        val spell = resolveSpell() ?: return
        val key = EventBus.composeLongKey(spell.component.packed, InventoryItemsComponent)
        onEvent<IfOverlayButtonT>(key) { opPlankMake(spell) }
    }

    private fun resolveSpell(): MagicSpell? {
        val type = ServerCacheManager.getItem(SPELL_OBJ.asRSCM(RSCMType.OBJ)) ?: return null
        return spells.getObjSpell(type)
    }

    private fun IfOverlayButtonT.opPlankMake(spell: MagicSpell) {
        val targetObj = targetObj ?: return resendSlot(player.inv, 0)
        player.opPlankMake(targetSlot, targetObj, spell)
    }

    private fun Player.opPlankMake(targetSlot: Int, targetObj: ItemServerType, spell: MagicSpell) {
        if (isDelayed) {
            resendSlot(inv, 0)
            return
        }
        clearPendingAction(eventBus)
        resetFaceEntity()
        if (isAccessProtected) {
            resendSlot(inv, 0)
            return
        }
        protectedAccess.launch(this) { castPlankMake(targetSlot, targetObj, spell) }
    }

    private fun ProtectedAccess.castPlankMake(
        targetSlot: Int,
        targetObj: ItemServerType,
        spell: MagicSpell,
    ) {
        if (!QuestRequirements.hasCompleted(player, REQUIRED_QUEST)) {
            mes("You need to have completed Dream Mentor to cast this spell.")
            resendSlot(inv, 0)
            return
        }

        if (targetSlot !in inv.indices) {
            resendSlot(inv, 0)
            return
        }

        if (!inv[targetSlot].isType(targetObj)) {
            resendSlot(inv, 0)
            return
        }

        val plank = PLANKS[targetObj.internalName]
        if (plank == null) {
            mes("You can't make that into a plank.")
            resendSlot(inv, 0)
            return
        }

        if (inv.count("obj.coins") < plank.cost) {
            mes("You don't have enough coins to make that plank.")
            resendSlot(inv, 0)
            return
        }

        if (runes.attemptCast(player, spell).isFailure()) {
            resendSlot(inv, 0)
            return
        }

        invDel(inv, targetObj.internalName, 1, targetSlot)
        invDel(inv, "obj.coins", plank.cost)
        invAdd(inv, plank.plank, 1)
        anim(CAST_ANIM)
        actionDelay = mapClock + CAST_TICKS
        statAdvance("stat.magic", spell.castXp)
    }

    private class Plank(val plank: String, val cost: Int)

    private companion object {
        const val SPELL_OBJ = "obj.86_plank_make"
        const val REQUIRED_QUEST = "quest_dreammentor"
        val InventoryItemsComponent = "component.inventory:items".asRSCM(RSCMType.COMPONENT)
        const val CAST_ANIM = "seq.dream_player_make_plank_spell"
        const val CAST_TICKS = 3

        val PLANKS =
            mapOf(
                "obj.logs" to Plank("obj.woodplank", 70),
                "obj.oak_logs" to Plank("obj.plank_oak", 175),
                "obj.teak_logs" to Plank("obj.plank_teak", 350),
                "obj.mahogany_logs" to Plank("obj.plank_mahogany", 1050),
                "obj.camphor_logs" to Plank("obj.plank_camphor", 1750),
                "obj.ironwood_logs" to Plank("obj.plank_ironwood", 3500),
                "obj.rosewood_logs" to Plank("obj.plank_rosewood", 5250),
            )
    }
}
