package org.rsmod.content.areas.misc.tutorial

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.player.hook.PlayerInvUpdateHook
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.hit.HitType
import org.rsmod.game.inv.Inventory
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class TutorialIslandCombatScript @Inject constructor(
    private val players: PlayerList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<NpcHitEvents.Impact>("npc.newbiegiantrat".asRSCM(RSCMType.NPC)) { ratHit() }
        onEvent<NpcHitEvents.Impact>("npc.newbiegiantrat2".asRSCM(RSCMType.NPC)) { ratHit() }
        onEvent<NpcHitEvents.Impact>("npc.newbiegiantrat3".asRSCM(RSCMType.NPC)) { ratHit() }
        onEvent<NpcHitEvents.Impact>("npc.newbiechicken".asRSCM(RSCMType.NPC)) {
            val player = tutorialAttacker() ?: return@onEvent
            if (player.tutorialStage != TutorialIsland.RUNES || hit.type != HitType.Magic) {
                return@onEvent
            }
            if (hit.secondaryType()?.id != "obj.01_wind_strike".asRSCM(RSCMType.OBJ)) {
                return@onEvent
            }
            player.tutorialStage = TutorialIsland.CHICKEN_KILL
            player.mes("You cast Wind Strike! Return to the Magic Instructor to finish the tutorial.")
        }
    }

    private fun NpcHitEvents.Impact.ratHit() {
        val player = tutorialAttacker() ?: return
        if (npc.hitpoints != 0 || hit.damage <= 0) return
        val next = ratKillStage(player.tutorialStage, hit.type) ?: return
        player.tutorialStage = next
        player.mes("You killed the rat! Return to the Combat Instructor for your next lesson.")
    }

    private fun NpcHitEvents.Impact.tutorialAttacker(): Player? {
        if (!hit.isFromPlayer) return null
        val player = hit.resolvePlayerSource(players) ?: return null
        return player.takeIf { it.coords.onTutorialIsland() && npc.coords.onTutorialIsland() }
    }
}

internal fun ratKillStage(stage: Int, style: HitType): Int? =
    when {
        stage == TutorialIsland.COMBAT_READY && style == HitType.Melee -> TutorialIsland.MELEE_KILL
        stage == TutorialIsland.BOW_ARROWS && style == HitType.Ranged -> TutorialIsland.RANGED_KILL
        else -> null
    }

internal class TutorialIslandInvHook @Inject constructor() : PlayerInvUpdateHook {
    override fun onInvUpdated(player: Player, inv: Inventory) {
        if (inv !== player.inv || !player.coords.onTutorialIsland()) return
        val stage = player.tutorialStage
        when {
            stage == TutorialIsland.HAMMER && "obj.bronze_dagger" in inv -> {
                player.tutorialStage = TutorialIsland.DAGGER
                player.mes("You smithed a bronze dagger! Return to the Mining Instructor.")
            }
            stage == TutorialIsland.DEZZICK_PICK &&
                "obj.copper_ore" in inv &&
                "obj.tin_ore" in inv -> {
                player.tutorialStage = TutorialIsland.ORES
                player.mes("You have both ores! Smelt them at the furnace.")
            }
            stage == TutorialIsland.ORES && "obj.bronze_bar" in inv -> {
                player.tutorialStage = TutorialIsland.BAR
                player.mes("You made a bronze bar! Show it to the Mining Instructor.")
            }
            stage in TutorialIsland.LOGS..TutorialIsland.FIRE_LIT &&
                "obj.shrimp" in inv -> {
                player.tutorialStage = TutorialIsland.SHRIMP_COOKED
                player.mes("Your shrimp is cooked! Take it back to the Survival Expert.")
            }
            stage == TutorialIsland.DOUGH && "obj.bread" in inv -> {
                player.tutorialStage = TutorialIsland.BREAD
                player.mes("Your bread is baked! Take it back to the Master Chef.")
            }
        }
    }
}
