package org.rsmod.content.raids.tfa.raid.encounter.ocellus

import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.isValidTarget
import org.rsmod.game.entity.Npc
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid

/**
 * The void left on the floor where an insolence slash swept. Each swept tile gets a loc that burns
 * anyone standing on it every tick, then plays its despawn animation; the slash itself is the spec's.
 */
internal class OcellusInsolence(private val deps: BossDeps) {
    private val lingering = HashSet<LocInfo>()

    fun register() {
        deps.extensionRegistry.register(LINGER) {
            val tile = checkNotNull(it.tile) { "$LINGER needs a tile." }
            linger(it.npc, tile, it.params as Int)
        }
    }

    fun clear() {
        for (loc in lingering) deps.locRepo.del(loc, Int.MAX_VALUE)
        lingering.clear()
    }

    private fun linger(npc: Npc, tile: CoordGrid, step: Int) {
        val angle = LocAngle[deps.random.of(ROTATIONS)]
        val loc = deps.locRepo.add(tile, locFor(step), Int.MAX_VALUE, angle, LocShape.GroundDecor)
        lingering.add(loc)
        burn(npc, loc, tile, BURN_TICKS)
        deps.worldQueues.add(LINGER_TICKS) {
            if (loc in lingering) {
                deps.worldRepo.locAnim(loc, DESPAWN_SEQ)
                deps.worldQueues.add(DESPAWN_TICKS) {
                    if (lingering.remove(loc)) deps.locRepo.del(loc, Int.MAX_VALUE)
                }
            }
        }
    }

    private fun burn(npc: Npc, loc: LocInfo, tile: CoordGrid, ticksLeft: Int) {
        deps.worldQueues.add(1) {
            if (loc !in lingering || !npc.isSlotAssigned) return@add
            for (player in deps.playerList) {
                if (!player.isValidTarget() || player.coords != tile) continue
                val damage = deps.random.of(BURN_DAMAGE)
                player.queueHit(npc, 1, HitType.Typeless, damage, deps.playerHitModifier)
            }
            if (ticksLeft > 1) burn(npc, loc, tile, ticksLeft - 1)
        }
    }

    private fun locFor(step: Int): String =
        when {
            step >= FAR_STEP -> "loc.tfa_ocellus_void_degen_aoe_insolence_7"
            step >= MID_STEP -> "loc.tfa_ocellus_void_degen_aoe_insolence_4"
            else -> "loc.tfa_ocellus_void_degen_aoe_insolence_1"
        }

    companion object {
        const val LINGER = "ocellus.insolence_linger"

        private const val ROTATIONS = 4
        private const val LINGER_TICKS = 15
        private const val BURN_TICKS = LINGER_TICKS - 1
        private val BURN_DAMAGE = 5..15
        private const val DESPAWN_TICKS = 1
        private const val MID_STEP = 3
        private const val FAR_STEP = 6
        private const val DESPAWN_SEQ = "seq.fx_ocellus_shadow_slash_degen_despawn"
    }
}
