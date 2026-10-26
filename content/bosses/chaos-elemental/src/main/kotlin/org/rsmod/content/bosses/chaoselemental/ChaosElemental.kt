package org.rsmod.content.bosses.chaoselemental

import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.boss
import org.rsmod.api.bosses.dsl.external
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.DamageExpr
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.HitType
import org.rsmod.api.player.output.spam
import org.rsmod.api.player.worn.WornUnequipOp
import org.rsmod.api.player.worn.WornUnequipResult
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.map.collision.isWalkBlocked

// OSRS wiki + Kronos-184 reference (io.ruin ChaosElemental): 4t attack rate, max
// 28. Three bolt attacks picked at random — Discord deals damage as magic
// (~60%), ranged (~20%) or melee (~20%); Madness unequips up to four worn items
// while inventory space allows; Confusion knocks the target 1-4 tiles directly
// away from the elemental. Teleport attack is rarer than the disarm since the
// 2018 rebalance.
class ChaosElemental @Inject constructor(
    deps: BossDeps,
    private val unequipOp: WornUnequipOp,
) : BossPluginScript(deps) {

    override val spec: BossSpec = chaosElementalSpec()

    init {
        deps.extensionRegistry.register("chaos_elemental.confusion") { _, npc, target, _ ->
            teleportTarget(npc, target)
        }
        deps.extensionRegistry.register("chaos_elemental.madness") { _, _, target, _ ->
            disarmTarget(target)
        }
    }

    private fun teleportTarget(npc: Npc, player: Player) {
        val bounds = npc.coords
        val dx = direction(player.coords.x, bounds.x, bounds.x + npc.size - 1)
        val dz = direction(player.coords.z, bounds.z, bounds.z + npc.size - 1)
        if (dx == 0 && dz == 0) return

        var dest = player.coords
        repeat(1 + deps.random.of(MAX_KNOCKBACK)) {
            val next = dest.translate(dx, dz)
            if (deps.collision.isWalkBlocked(next)) return@repeat
            dest = next
        }
        if (dest != player.coords) {
            PathingEntityCommon.teleport(player, deps.collision, dest)
            player.spam("The Chaos Elemental teleports you!")
        }
    }

    private fun direction(value: Int, min: Int, max: Int): Int =
        when {
            value < min -> -1
            value > max -> 1
            else -> 0
        }

    private fun disarmTarget(player: Player) {
        var unequipped = 0
        for (wearpos in DISARM_SLOTS) {
            if (unequipped >= MAX_DISARMED) break
            player.worn.objs[wearpos.slot] ?: continue
            val result = unequipOp.unequip(player, wearpos.slot, player.worn, player.inv)
            if (result is WornUnequipResult.Success) {
                unequipped++
            } else {
                break
            }
        }
        if (unequipped > 0) {
            player.spam("The Chaos Elemental has disarmed you!")
        }
    }

    private companion object {
        private const val MAX_KNOCKBACK = 4
        private const val MAX_DISARMED = 4

        private val DISARM_SLOTS =
            listOf(
                Wearpos.RightHand,
                Wearpos.LeftHand,
                Wearpos.Torso,
                Wearpos.Hat,
                Wearpos.Legs,
                Wearpos.Hands,
                Wearpos.Feet,
            )
    }
}

internal fun chaosElementalSpec(): BossSpec = boss("npc.chaoselemental") {
    stats(attackRate = 4)

    fun discordBolt(type: HitType) =
        Effect.Sequence(
            listOf(
                Effect.Anim("seq.chaoselemental_attack"),
                Effect.Projectile(
                    spotanim = "spotanim.chaoselemental_spotanim_discord_travel",
                    launch = "spotanim.chaoselemental_spotanim_discord_casting",
                    impact = "spotanim.chaoselemental_spotanim_discord_impact",
                    hit = Effect.Hit(damage = DamageExpr.Roll(0..28), type = type),
                ),
            ),
        )

    val discordMagic = ability("discord_magic", discordBolt(HitType.Magic))
    val discordRanged = ability("discord_ranged", discordBolt(HitType.Ranged))
    val discordMelee = ability("discord_melee", discordBolt(HitType.Melee))

    val confusion =
        ability("confusion") {
            anim("seq.chaoselemental_attack")
            projectile(
                spotanim = "spotanim.chaoselemental_spotanim_confusion_travel",
                launch = "spotanim.chaoselemental_spotanim_confusion_casting",
                impact = "spotanim.chaoselemental_spotanim_confusion_impact",
                onImpact = external("chaos_elemental.confusion"),
            )
        }

    val madness =
        ability("madness") {
            anim("seq.chaoselemental_attack")
            projectile(
                spotanim = "spotanim.chaoselemental_spotanim_madness_travel",
                launch = "spotanim.chaoselemental_spotanim_madness_casting",
                impact = "spotanim.chaoselemental_spotanim_madness_impact",
                onImpact = external("chaos_elemental.madness"),
            )
        }

    phase("combat") {
        weightedSelectorRandom {
            +random(discordMagic, weight = 6)
            +random(discordRanged, weight = 2)
            +random(discordMelee, weight = 2)
            +random(madness, weight = 1)
            +random(confusion, weight = 1)
        }
    }
}
