package org.rsmod.content.bosses.thermy

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import org.rsmod.api.config.refs.done.hitmark_groups
import org.rsmod.api.player.events.PlayerHitEvents
import org.rsmod.api.player.hit.modifier.NoopPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.statDrain
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onPlayerHit
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// OSRS: fighting smoke devils without a facemask or slayer helm lets their
// smoke through - it chokes the player for extra typeless damage and drains
// combat stats on every hit.
class SmokeDevilProtection
@Inject
constructor(private val npcs: NpcList, private val random: GameRandom) : PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerHit { chokeUnprotected() }
    }

    private fun PlayerHitEvents.Impact.chokeUnprotected() {
        if (!hit.isFromNpc) return
        val npc = hit.resolveNpcSource(npcs) ?: return
        val key = RSCM.getReverseMapping(RSCMType.NPC, npc.type.id) ?: return
        if (key !in SMOKE_DEVILS || hasFaceProtection(player)) return

        player.queueHit(
            source = npc,
            delay = 0,
            type = HitType.Typeless,
            damage = random.of(1, CHOKE_MAX_DAMAGE),
            modifier = NoopPlayerHitModifier,
            hitmark = hitmark_groups.regular_damage,
        )
        for (stat in DRAINED_STATS) {
            player.statDrain(stat, constant = STAT_DRAIN, percent = 0)
        }
        player.mes("The devil's smoke chokes you!")
    }

    private companion object {
        const val CHOKE_MAX_DAMAGE = 8
        const val STAT_DRAIN = 1

        val SMOKE_DEVILS = setOf("npc.smoke_devil", "npc.superior_smoke_devil")
        val DRAINED_STATS =
            listOf("stat.attack", "stat.strength", "stat.defence", "stat.ranged", "stat.magic")
    }
}

internal fun hasFaceProtection(player: Player): Boolean {
    val hat = player.worn.objs[Wearpos.Hat.slot] ?: return false
    val key = RSCM.getReverseMapping(RSCMType.OBJ, hat.id) ?: return false
    return key == "obj.slayer_facemask" || key.startsWith("obj.slayer_helm")
}
