package org.rsmod.content.bosses.chaosfanatic

import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.DamageExpr
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.TargetExpr
import org.rsmod.api.player.output.spam
import org.rsmod.game.entity.Player

// OSRS wiki: ranged-magic attack (max 21) at 2 ticks. Specials: an
// unavoidable green disarm bolt (negated only by a full inventory) and three
// green explosions on the floor, one always on the player's tile.
class ChaosFanatic @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {

    override val spec: BossSpec = boss("npc.chaos_fanatic") {
        stats(attackRate = 2)

        val magic =
            ability("magic") {
                projectile(
                    spotanim = "spotanim.chaoselemental_spotanim_confusion_travel",
                    hit = Effect.Hit(damage = DamageExpr.Roll(0..21), type = Magic),
                )
            }

        val disarm =
            ability("disarm") {
                say("I have a special for you!")
                include(external("chaos_fanatic.disarm"))
            }

        val explosion =
            ability("explosion") {
                say("Explosive!")
                debris(
                    telegraph = "spotanim.chaoselemental_spotanim_madness_travel",
                    damage = DamageExpr.Roll(0..31),
                    type = Typeless,
                    windup = 4,
                    targetRadius = 12,
                    scatterRadius = 5,
                    count = 3..3,
                    center = TargetExpr.CurrentTarget,
                )
            }

        phase("combat") {
            weightedSelectorRandom {
                +random(magic, weight = 6)
                +random(disarm, weight = 1)
                +random(explosion, weight = 1)
            }
        }
    }

    init {
        deps.extensionRegistry.register("chaos_fanatic.disarm") { _, _, target, _ -> disarm(target) }
    }

    private fun disarm(player: Player) {
        val weapon = player.worn.objs[Wearpos.RightHand.slot] ?: return
        val free = player.inv.objs.indexOfFirst { it == null }
        if (free == -1) return
        player.worn[Wearpos.RightHand.slot] = null
        player.inv[free] = weapon
        player.spam("The Chaos Fanatic has knocked your weapon out of your hands!")
    }
}
