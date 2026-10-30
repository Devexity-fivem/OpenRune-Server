package org.rsmod.content.areas.misc.lobbies.pestcontrol

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.abs
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.game.MapClock
import org.rsmod.game.hit.HitType
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

// Pest Control game loop: three independent lander queues (one shared island
// each), the launch countdown, portal shields dropping in sequence, pest
// waves, knight defence, and commendation point awards. The island map is
// cache-baked at region 10536; the OSRS minimum party of five is relaxed to a
// single volunteer so the game stays playable on small worlds.
@Singleton
class PestControlGame
@Inject
constructor(
    private val collision: CollisionFlagMap,
    private val mapClock: MapClock,
    private val npcRepo: NpcRepository,
    private val random: GameRandom,
    private val worldRepo: WorldRepository,
    private val playerHitModifier: PlayerHitModifier,
) {
    enum class Tier(
        val lander: String,
        val combatLevel: Int,
        val pestTier: String,
        val portalSuffix: String,
        val reward: Int,
    ) {
        Novice("npc.pest_squire_lander", 40, "1", "", 2),
        Intermediate("npc.pest_squire_lander_2", 70, "3", "_bt1", 3),
        Veteran("npc.pest_squire_lander_3", 100, "5", "_bt1", 4),
    }

    private enum class State {
        IDLE,
        BOARDING,
        ACTIVE,
    }

    private inner class Slot {
        var state = State.IDLE
        val queued = mutableSetOf<Player>()
        val participants = mutableSetOf<Player>()
        val portals = mutableListOf<Npc>()
        val pests = mutableListOf<Npc>()
        var knight: Npc? = null
        var launchCycle = 0
        var endCycle = 0
        var nextWave = 0
        var nextShield = 0
        var shieldCursor = 0
    }

    private val slots = Tier.entries.associateWith { Slot() }

    fun join(player: Player, tier: Tier): Boolean {
        val slot = slots.getValue(tier)
        if (slot.state == State.ACTIVE || slot.queued.size >= MAX_VOLUNTEERS) return false
        slot.queued += player
        if (slot.state == State.IDLE) {
            slot.state = State.BOARDING
            slot.launchCycle = mapClock.cycle + LAUNCH_TICKS
        }
        val launchEarly = slot.queued.size >= LAUNCH_EARLY_AT
        if (launchEarly) slot.launchCycle = minOf(slot.launchCycle, mapClock.cycle + QUICK_LAUNCH_TICKS)
        player.mes(
            "You board the ${tier.name.lowercase()} lander. " +
                "It launches when the countdown ends or five volunteers have boarded."
        )
        return true
    }

    fun tick() {
        for ((tier, slot) in slots) {
            when (slot.state) {
                State.BOARDING -> tickBoarding(tier, slot)
                State.ACTIVE -> tickGame(tier, slot)
                State.IDLE -> Unit
            }
        }
    }

    private fun tickBoarding(tier: Tier, slot: Slot) {
        slot.queued.removeAll { !it.isSlotAssigned }
        if (mapClock.cycle < slot.launchCycle) return
        if (slot.queued.size < MIN_VOLUNTEERS) {
            slot.state = State.IDLE
            slot.queued.forEach { it.mes("The lander departs without enough volunteers.") }
            slot.queued.clear()
            return
        }
        launch(tier, slot)
    }

    private fun launch(tier: Tier, slot: Slot) {
        slot.state = State.ACTIVE
        slot.endCycle = mapClock.cycle + GAME_TICKS
        slot.nextShield = mapClock.cycle + SHIELD_FIRST_TICKS
        slot.nextWave = mapClock.cycle + WAVE_FIRST_TICKS
        slot.shieldCursor = 0
        slot.knight = Npc(KNIGHT, KNIGHT_TILE).also { npcRepo.add(it, Int.MAX_VALUE) }
        for ((index, tile) in PORTAL_TILES.withIndex()) {
            val shielded = "npc.pest_portal_shield_${index + 1}_active${tier.portalSuffix}"
            slot.portals += Npc(shielded, tile).also { npcRepo.add(it, Int.MAX_VALUE) }
        }
        for (player in slot.queued) {
            if (!player.isSlotAssigned) continue
            slot.participants += player
            PathingEntityCommon.teleport(player, collision, ISLAND_LAND)
            player.mes("The lander arrives on the island. Protect the Void Knight!")
        }
        slot.queued.clear()
    }

    private fun tickGame(tier: Tier, slot: Slot) {
        tickPestDeaths(slot)
        tickSpinners(slot)
        dropShield(slot, tier)
        spawnWave(slot, tier)
        tickKnight(slot)
        val knight = slot.knight
        if (knight == null || !knight.isSlotAssigned || knight.hitpoints <= 0) {
            finish(tier, slot, win = false)
        } else if (slot.portals.all { !it.isSlotAssigned || it.hitpoints <= 0 }) {
            finish(tier, slot, win = true)
        } else if (mapClock.cycle >= slot.endCycle) {
            finish(tier, slot, win = false)
        }
    }

    private fun dropShield(slot: Slot, tier: Tier) {
        if (slot.shieldCursor >= slot.portals.size || mapClock.cycle < slot.nextShield) return
        val portal = slot.portals[slot.shieldCursor]
        slot.shieldCursor++
        slot.nextShield = mapClock.cycle + SHIELD_PERIOD_TICKS
        if (!portal.isSlotAssigned || portal.hitpoints <= 0) return
        val active =
            ServerCacheManager.getNpc(
                "npc.pest_portal_${slot.shieldCursor}_active${tier.portalSuffix}".asRSCM(
                    RSCMType.NPC
                )
            ) ?: return
        portal.transmog(active, Int.MAX_VALUE)
    }

    private fun spawnWave(slot: Slot, tier: Tier) {
        if (mapClock.cycle < slot.nextWave || slot.pests.size >= MAX_PESTS) return
        slot.nextWave = mapClock.cycle + WAVE_PERIOD_TICKS
        for (portal in slot.portals) {
            if (!portal.isSlotAssigned || portal.hitpoints <= 0 || portal.isShielded()) continue
            repeat(PESTS_PER_PORTAL_WAVE) {
                val tile =
                    portal.coords.translate(
                        random.of(5) - 2,
                        random.of(5) - 2,
                    )
                val pest = Npc(pestSymbol(tier.pestTier), tile)
                npcRepo.add(pest, Int.MAX_VALUE)
                slot.pests += pest
            }
        }
    }

    private fun Npc.isShielded(): Boolean =
        try {
            RSCM.getReverseMapping(RSCMType.NPC, visType.id).contains("shield")
        } catch (e: Exception) {
            false
        }

    private fun pestSymbol(tier: String): String {
        val pool =
            listOf(
                "npc.pest_splatter_$tier",
                "npc.pest_ravager_$tier",
                "npc.pest_spinner_$tier",
                "npc.pest_brawler_$tier",
                "npc.pest_shifter_${tier}a",
                "npc.pest_torcher_${tier}a",
                "npc.pest_defiler_${tier}a",
            )
        return pool[random.of(pool.size)]
    }

    // Splatters detonate when they die, hurting anyone standing next to them.
    private fun tickPestDeaths(slot: Slot) {
        val dead = slot.pests.filter { !it.isSlotAssigned || it.hitpoints <= 0 }
        slot.pests.removeAll(dead.toSet())
        for (pest in dead) {
            val name =
                try {
                    RSCM.getReverseMapping(RSCMType.NPC, pest.visType.id)
                } catch (e: Exception) {
                    continue
                }
            if (!name.contains("splatter")) continue
            worldRepo.spotanimMap(
                SpotanimType("spotanim.pest_spinner_explode_spotanim".asRSCM(RSCMType.SPOTANIM)),
                pest.coords,
            )
            for (player in slot.participants) {
                if (
                    player.hitpoints > 0 &&
                        player.coords.chebyshevDistance(pest.coords) <= SPLATTER_REACH
                ) {
                    player.queueHit(
                        pest,
                        delay = 0,
                        HitType.Typeless,
                        SPLATTER_MIN_HIT + random.of(SPLATTER_HIT_SPAN),
                        playerHitModifier,
                    )
                }
            }
        }
    }

    // Spinners repair the closest damaged live portal in reach.
    private fun tickSpinners(slot: Slot) {
        if (mapClock.cycle % SPINNER_HEAL_PERIOD != 0) return
        for (pest in slot.pests) {
            if (!pest.isSlotAssigned || pest.hitpoints <= 0) continue
            val name =
                try {
                    RSCM.getReverseMapping(RSCMType.NPC, pest.visType.id)
                } catch (e: Exception) {
                    continue
                }
            if (!name.contains("spinner")) continue
            val portal =
                slot.portals.firstOrNull {
                    it.isSlotAssigned &&
                        it.hitpoints > 0 &&
                        it.hitpoints < it.visType.hitpoints &&
                        !it.isShielded() &&
                        it.coords.chebyshevDistance(pest.coords) <= SPINNER_REACH
                } ?: continue
            portal.hitpoints = minOf(portal.visType.hitpoints, portal.hitpoints + SPINNER_HEAL)
        }
    }

    // Every second tick each pest adjacent to the knight chips a point of his
    // health — the stand-in for the pests' own knight attack AI.
    private fun tickKnight(slot: Slot) {
        val knight = slot.knight ?: return
        if (!knight.isSlotAssigned || knight.hitpoints <= 0) return
        if (mapClock.cycle % KNIGHT_HIT_PERIOD != 0) return
        val attackers =
            slot.pests.count { pest ->
                pest.isSlotAssigned &&
                    pest.hitpoints > 0 &&
                    abs(pest.coords.x - knight.coords.x) <= KNIGHT_REACH &&
                    abs(pest.coords.z - knight.coords.z) <= KNIGHT_REACH
            }
        if (attackers > 0) {
            knight.hitpoints = maxOf(0, knight.hitpoints - attackers)
        }
    }

    private fun finish(tier: Tier, slot: Slot, win: Boolean) {
        for (player in slot.participants) {
            if (!player.isSlotAssigned) continue
            PathingEntityCommon.teleport(player, collision, OUTPOST_RETURN)
            if (win) {
                player.pestControlPoints += tier.reward
                player.mes(
                    "The portals are destroyed! You are awarded ${tier.reward} " +
                        "Void Knight commendation points."
                )
            } else {
                player.mes("The Void Knight has fallen. The island is lost.")
            }
        }
        slot.knight?.let { npcRepo.del(it, 0) }
        slot.portals.forEach { npcRepo.del(it, 0) }
        slot.pests.forEach { npcRepo.del(it, 0) }
        slot.portals.clear()
        slot.pests.clear()
        slot.participants.clear()
        slot.queued.clear()
        slot.knight = null
        slot.state = State.IDLE
    }

    private companion object {
        private const val MIN_VOLUNTEERS = 1
        private const val MAX_VOLUNTEERS = 25
        private const val LAUNCH_EARLY_AT = 5
        private const val LAUNCH_TICKS = 50
        private const val QUICK_LAUNCH_TICKS = 10
        private const val GAME_TICKS = 2000
        private const val SHIELD_FIRST_TICKS = 25
        private const val SHIELD_PERIOD_TICKS = 25
        private const val WAVE_FIRST_TICKS = 10
        private const val WAVE_PERIOD_TICKS = 20
        private const val PESTS_PER_PORTAL_WAVE = 2
        private const val MAX_PESTS = 40
        private const val KNIGHT_HIT_PERIOD = 2
        private const val KNIGHT_REACH = 2
        private const val SPLATTER_REACH = 2
        private const val SPLATTER_MIN_HIT = 1
        private const val SPLATTER_HIT_SPAN = 6
        private const val SPINNER_HEAL_PERIOD = 4
        private const val SPINNER_REACH = 6
        private const val SPINNER_HEAL = 5
        private const val KNIGHT = "npc.pest_voidknight_control1"

        private val ISLAND_LAND = CoordGrid(2656, 2615, 0)
        private val KNIGHT_TILE = CoordGrid(2656, 2592, 0)
        private val OUTPOST_RETURN = CoordGrid(2659, 2675, 0)
        private val PORTAL_TILES =
            listOf(
                CoordGrid(2628, 2591, 0),
                CoordGrid(2680, 2592, 0),
                CoordGrid(2645, 2569, 0),
                CoordGrid(2669, 2570, 0),
            )
    }
}
