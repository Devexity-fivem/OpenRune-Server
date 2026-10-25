package org.rsmod.content.raids.tfa.raid.encounter.ocellus

import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.output.spotanimMapPrivate
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid

/**
 * The void chain that follows each soul fragment. Three ticks after the drain an orb opens on
 * every player in the quadrant; two ticks later, and every [GAP] ticks after that, the tile each
 * stands on is marked and goes off [GAP] ticks later, [EXPLOSIONS] times in all. A player still on
 * the tile takes the blast, one who has stepped off gets their attack cooldown back.
 */
internal class OcellusVoidChain(private val deps: BossDeps) {
    private var generation = 0

    fun register() {
        deps.extensionRegistry.register(START) { start(it.npc) }
    }

    fun clear() {
        generation++
    }

    private fun start(boss: Npc) {
        val run = generation
        val targets = OcellusArena.quadrantPlayers(boss, deps.playerList)
        deps.worldQueues.add(TELEGRAPH_AT) {
            if (run != generation) return@add
            for (player in targets) {
                if (player.isValidTarget()) player.spotanim(TELEGRAPH_SPOT, slot = TELEGRAPH_SLOT)
            }
        }
        repeat(EXPLOSIONS) { index ->
            deps.worldQueues.add(FIRST_MARK_AT + GAP * index) {
                if (run != generation) return@add
                for (player in targets) mark(boss, player, run)
            }
        }
    }

    private fun mark(boss: Npc, player: Player, run: Int) {
        if (!player.isValidTarget()) return
        val tile = player.coords
        player.spotanimMapPrivate(tile, EXPLOSION_SPOT)
        deps.worldQueues.add(GAP) {
            if (run == generation) detonate(boss, player, tile)
        }
    }

    private fun detonate(boss: Npc, player: Player, tile: CoordGrid) {
        if (!player.isValidTarget() || !boss.isSlotAssigned) return
        if (player.coords == tile) {
            val damage = deps.random.of(DAMAGE)
            player.queueHit(boss, 1, HitType.Typeless, damage, deps.playerHitModifier)
        } else {
            player.actionDelay = player.currentMapClock
        }
    }

    companion object {
        const val START = "ocellus.void_chain_start"

        private const val TELEGRAPH_SPOT = "spotanim.vfx_void_chain_player_telegraph"
        private const val TELEGRAPH_SLOT = 2
        private const val EXPLOSION_SPOT = "spotanim.vfx_void_chain_explosion"
        private const val TELEGRAPH_AT = 3
        private const val FIRST_MARK_AT = TELEGRAPH_AT + 2
        private const val GAP = 2
        private const val EXPLOSIONS = 6
        private val DAMAGE = 14..16
    }
}
