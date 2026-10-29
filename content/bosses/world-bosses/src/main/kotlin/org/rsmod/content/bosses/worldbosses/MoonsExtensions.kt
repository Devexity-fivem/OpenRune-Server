package org.rsmod.content.bosses.worldbosses

import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossExtensionContext
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// Moons of Peril specials that the boss DSL can't express: persistent blood
// pools (Blood Rain + Blood Jaguar phases), the Weapon Freeze disarm into
// loc.pmoon_boss_ice_block, and Frost Storm extinguishing the chamber
// braziers (loc.pmoon_boss_brazier_unlit) while the Blue Moon heals off
// them until the player relights them via their Light op.
class MoonsExtensions @Inject constructor(
    private val deps: BossDeps,
    private val objRepo: ObjRepository,
) : PluginScript() {
    private val frozenWeapons = mutableMapOf<Player, InvObj>()
    private val unlitBraziers = mutableSetOf<CoordGrid>()

    override fun ScriptContext.startup() {
        deps.extensionRegistry.register("pmoon.blood_pools") { spawnPools(it, healMultiplier = 0) }
        deps.extensionRegistry.register("pmoon.jaguar_pools") { spawnPools(it, healMultiplier = 3) }
        deps.extensionRegistry.register("pmoon.weapon_freeze", ::weaponFreeze)
        deps.extensionRegistry.register("pmoon.frost_storm", ::frostStorm)
        onOpLoc1("loc.pmoon_boss_brazier_unlit") { lightBrazier(it.loc) }
    }

    private fun spawnPools(ctx: BossExtensionContext, healMultiplier: Int) {
        val npc = ctx.npc
        val centre = ctx.target.coords
        val taken = mutableSetOf<CoordGrid>()
        repeat(POOL_COUNT) { index ->
            deps.worldQueues.add(index * POOL_STAGGER) {
                if (!npc.isSlotAssigned || npc.hitpoints <= 0) return@add
                val tile = freePoolTile(centre, taken)
                deps.locRepo.add(
                    tile,
                    "loc.pmoon_boss_blood_pool",
                    POOL_TICKS,
                    LocAngle.West,
                    LocShape.CentrepieceStraight,
                )
                monitorPool(tile, npc, healMultiplier, POOL_TICKS)
            }
        }
    }

    private fun freePoolTile(centre: CoordGrid, taken: MutableSet<CoordGrid>): CoordGrid {
        repeat(POOL_PLACE_ATTEMPTS) {
            val tile =
                centre.translate(
                    deps.random.of(POOL_RADIUS * 2 + 1) - POOL_RADIUS,
                    deps.random.of(POOL_RADIUS * 2 + 1) - POOL_RADIUS,
                )
            if (taken.add(tile)) return tile
        }
        return centre
    }

    private fun monitorPool(tile: CoordGrid, npc: Npc, healMultiplier: Int, ticksLeft: Int) {
        if (ticksLeft <= 0 || !npc.isSlotAssigned || npc.hitpoints <= 0) return
        for (player in deps.playerList) {
            if (player.coords == tile && player.hitpoints > 0) {
                val damage = 1 + deps.random.of(POOL_MAX_HIT)
                player.queueHit(npc, delay = 0, HitType.Typeless, damage, deps.playerHitModifier)
                if (healMultiplier > 0) {
                    npc.hitpoints =
                        minOf(npc.visType.hitpoints, npc.hitpoints + damage * healMultiplier)
                }
            }
        }
        deps.worldQueues.add(1) { monitorPool(tile, npc, healMultiplier, ticksLeft - 1) }
    }

    // Weapon Freeze traps the wielded weapon in an ice block on the player's
    // tile. The cache loc has no ops so the player can't click to break it;
    // the block gives way on its own shortly after the bind ends and the
    // weapon returns to the hand, or inventory if the slot was refilled.
    private fun weaponFreeze(ctx: BossExtensionContext) {
        val player = ctx.target
        val weapon = player.worn[Wearpos.RightHand.slot] ?: return
        player.worn[Wearpos.RightHand.slot] = null
        frozenWeapons[player] = weapon
        deps.locRepo.add(
            player.coords,
            "loc.pmoon_boss_ice_block",
            ICE_BLOCK_TICKS,
            LocAngle.West,
            LocShape.CentrepieceStraight,
        )
        deps.worldQueues.add(ICE_BLOCK_TICKS) {
            frozenWeapons.remove(player)?.let { returnWeapon(player, it) }
        }
    }

    private fun returnWeapon(player: Player, weapon: InvObj) {
        if (player.worn[Wearpos.RightHand.slot] == null) {
            player.worn[Wearpos.RightHand.slot] = weapon
            return
        }
        val slot = player.inv.objs.indexOfFirst { it == null }
        if (slot >= 0) {
            player.inv[slot] = weapon
        } else {
            objRepo.add(weapon, player.coords, duration = 200, receiver = player)
        }
    }

    // Frost Storm puts out the chamber braziers: she heals a pulse while any
    // stay unlit, so relighting all of them ends the heal.
    private fun frostStorm(ctx: BossExtensionContext) {
        val npc = ctx.npc
        for (offset in BRAZIER_OFFSETS) {
            val tile = npc.coords.translate(offset.first, offset.second)
            deps.locRepo.add(
                tile,
                "loc.pmoon_boss_brazier_unlit",
                BRAZIER_TICKS,
                LocAngle.West,
                LocShape.CentrepieceStraight,
                onDespawn = { unlitBraziers.remove(tile) },
            )
            unlitBraziers += tile
        }
        monitorBraziers(npc)
    }

    private fun monitorBraziers(npc: Npc) {
        if (!npc.isSlotAssigned || npc.hitpoints <= 0 || unlitBraziers.isEmpty()) return
        npc.hitpoints = minOf(npc.visType.hitpoints, npc.hitpoints + 1 + deps.random.of(3))
        deps.worldQueues.add(BRAZIER_HEAL_PERIOD) { monitorBraziers(npc) }
    }

    private fun lightBrazier(loc: BoundLocInfo) {
        unlitBraziers.remove(loc.coords)
        deps.locRepo.del(loc, 0)
        deps.locRepo.add(
            loc.coords,
            "loc.pmoon_boss_brazier_lit",
            BRAZIER_TICKS,
            LocAngle.West,
            LocShape.CentrepieceStraight,
        )
    }

    private companion object {
        private const val POOL_COUNT = 5
        private const val POOL_STAGGER = 2
        private const val POOL_RADIUS = 7
        private const val POOL_TICKS = 30
        private const val POOL_MAX_HIT = 7
        private const val POOL_PLACE_ATTEMPTS = 8
        private const val ICE_BLOCK_TICKS = 12
        private const val BRAZIER_TICKS = 50
        private const val BRAZIER_HEAL_PERIOD = 4
        private val BRAZIER_OFFSETS = listOf(-2 to 0, 2 to 0)
    }
}
