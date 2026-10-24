package org.rsmod.content.raids.tfa.raid.encounter.ocellus

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.MoveRestrict
import dev.openrune.types.NpcMode
import java.util.IdentityHashMap
import kotlin.math.abs
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.npc.heal
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.stat.statBoost
import org.rsmod.api.player.stat.statHeal
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.headbar.Headbar
import org.rsmod.game.hit.HitType
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * The void siphons and augmenters of the second and third quadrants, and the void rifts of the
 * last. A pair opens on the floor three ticks into the quadrant and again whenever [WAVE_GAP]
 * ticks have passed since the last pair, as soon as the boss isn't in the middle of a special.
 * Each is a 44 hp rift the party can kill: a siphon feeds the boss until it does and heals whoever
 * kills it, an augmenter or rift charges and, if left to finish, goes off for the whole quadrant,
 * but empowers whoever kills it first.
 */
internal class OcellusRifts(private val deps: BossDeps) {
    private val waves = IdentityHashMap<Npc, Wave>()
    private val alive = ArrayList<Rift>()

    fun register() {
        deps.extensionRegistry.register(RESET) { reset(it.npc, it.params as Int) }
        deps.extensionRegistry.register(BUSY) { waves[it.npc]?.busy = it.params as Boolean }
        deps.extensionRegistry.register(STOP) { stop(it.npc) }
    }

    fun clear() {
        for (rift in alive.toList()) remove(rift)
        waves.clear()
    }

    private enum class Kind(
        val npc: String,
        val spawnSeq: String,
        val despawnSeq: String,
        val forcedMaxHitStyle: Int,
        val maxCount: Int = COUNT,
        val waveGap: Int = WAVE_GAP,
        val detonateSeq: String? = null,
    ) {
        Siphon(
            "npc.tfa_ocellus_p3_bombportal_heal",
            "seq.vfx_ocellus_voidflare_heal_spawn",
            "seq.vfx_ocellus_voidflare_heal_despawn",
            FORCED_RANGED,
        ),
        Augmenter(
            "npc.tfa_ocellus_p3_bombportal_buff",
            "seq.vfx_ocellus_voidflare_buff_spawn",
            "seq.vfx_ocellus_voidflare_buff_despawn",
            FORCED_MAGIC,
            detonateSeq = "seq.vfx_ocellus_voidflare_buff_detonate",
        ),
        Rift(
            "npc.tfa_ocellus_p3_bombportal_basic",
            "seq.vfx_ocellus_voidflare_default_spawn",
            "seq.vfx_ocellus_voidflare_default_despawn",
            FORCED_MELEE,
            maxCount = RIFT_MAX_COUNT,
            waveGap = RIFT_WAVE_GAP,
            detonateSeq = "seq.vfx_ocellus_voidflare_default_detonate",
        ),
    }

    private class Wave(
        val kind: Kind,
        var nextAt: Int,
        var busy: Boolean = false,
        var opened: Int = 0,
    )

    private class Rift(val npc: Npc, val kind: Kind, val wave: Wave, var age: Int = 0)

    private fun reset(boss: Npc, kindIndex: Int) {
        stop(boss)
        val kind = Kind.entries.getOrNull(kindIndex) ?: return
        val wave = Wave(kind, deps.mapClock.cycle + FIRST_AT)
        waves[boss] = wave
        tick(boss, wave)
    }

    private fun stop(boss: Npc) {
        val wave = waves.remove(boss) ?: return
        for (rift in alive.filter { it.wave === wave }) remove(rift)
    }

    private fun tick(boss: Npc, wave: Wave) {
        deps.worldQueues.add(1) {
            if (waves[boss] !== wave) return@add
            if (!boss.isSlotAssigned) {
                waves.remove(boss)
                return@add
            }
            val now = deps.mapClock.cycle
            if (!wave.busy && now >= wave.nextAt) {
                wave.nextAt = now + wave.kind.waveGap
                open(boss, wave)
            }
            tick(boss, wave)
        }
    }

    private fun open(boss: Npc, wave: Wave) {
        if (wave.kind == Kind.Rift) {
            for (old in alive.filter { it.wave === wave }) remove(old)
        }
        val count = minOf(COUNT + wave.opened++, wave.kind.maxCount)
        for (tile in pickTiles(boss, count)) {
            val npc = Npc(wave.kind.npc, tile)
            npc.moveRestrict = MoveRestrict.NoMove
            npc.mode = NpcMode.None
            npc.vars[FORCED_MAX_HIT_STYLES] = wave.kind.forcedMaxHitStyle
            deps.npcRepo.add(npc, Int.MAX_VALUE)
            npc.hideAllOps()
            npc.anim(wave.kind.spawnSeq)
            if (wave.kind.detonateSeq != null) {
                npc.showHeadbar(Headbar.fromNoSource(CHARGE_BAR, CHARGE_BAR, 0, CHARGE_SEGMENTS, 0, CHARGE_CYCLES))
            }
            val rift = Rift(npc, wave.kind, wave)
            alive.add(rift)
            deps.worldQueues.add(1) { if (rift in alive) npc.showAllOps() }
            watch(boss, rift)
        }
    }

    private fun pickTiles(boss: Npc, count: Int): List<CoordGrid> {
        val centre = boss.coords.translate(boss.size / 2, boss.size / 2)
        val taken = deps.playerList.map { it.coords }.toSet() + alive.map { it.npc.coords }
        val free = ArrayList<CoordGrid>()
        for (dx in -REACH..REACH) {
            for (dz in -REACH..REACH) {
                val tile = centre.translate(dx, dz)
                if (maxOf(abs(dx), abs(dz)) < MIN_DISTANCE) continue
                if (!OcellusArena.sameQuadrantFloor(boss, tile) || tile in taken) continue
                if (deps.collision[tile.x, tile.z, tile.level] and BLOCKED != 0) continue
                free.add(tile)
            }
        }
        return List(minOf(count, free.size)) { free.removeAt(deps.random.of(free.size)) }
    }

    private fun watch(boss: Npc, rift: Rift) {
        deps.worldQueues.add(1) {
            if (rift !in alive) return@add
            val npc = rift.npc
            when {
                npc.hitpoints <= 0 -> {
                    killed(rift)
                    return@add
                }
                !npc.isSlotAssigned || !boss.isSlotAssigned -> {
                    remove(rift)
                    return@add
                }
            }
            rift.age++
            if (rift.kind == Kind.Siphon && rift.age % SIPHON_GAP == 0) siphon(boss, npc)
            val detonates = rift.kind.detonateSeq != null
            if (detonates && rift.age == DETONATE_AT - 1) overload(boss)
            if (detonates && rift.age == DETONATE_AT) {
                detonate(boss, rift)
                return@add
            }
            watch(boss, rift)
        }
    }

    private fun siphon(boss: Npc, from: Npc) {
        val start = from.coords
        val end = boss.coords.translate(boss.size / 2, boss.size / 2)
        deps.worldRepo.projAnim(
            ProjAnim(
                spotanim = SIPHON_PROJECTILE.asRSCM(RSCMType.SPOTANIM),
                startHeight = SIPHON_START_HEIGHT,
                endHeight = SIPHON_END_HEIGHT,
                startTime = 0,
                endTime = SIPHON_CYCLES,
                angle = SIPHON_ANGLE,
                progress = 0,
                sourceIndex = 0,
                targetIndex = 0,
                startCoord = start,
                endCoord = end,
            )
        )
        boss.heal(SIPHON_HEAL, showHitsplat = true)
    }

    private fun overload(boss: Npc) {
        for (player in OcellusArena.quadrantPlayers(boss, deps.playerList)) {
            val damage = deps.random.of(DETONATE_DAMAGE)
            player.queueHit(boss, 1, HitType.Typeless, damage, deps.playerHitModifier)
        }
    }

    private fun detonate(boss: Npc, rift: Rift) {
        val npc = rift.npc
        npc.hideAllOps()
        npc.anim(rift.kind.detonateSeq ?: return)
        if (rift.kind == Kind.Augmenter) {
            for (player in OcellusArena.quadrantPlayers(boss, deps.playerList)) {
                player.spotanim(DETONATE_PLAYER_SPOT, delay = DETONATE_SPOT_DELAY)
            }
        }
        deps.worldQueues.add(DETONATE_REMOVAL) { remove(rift) }
    }

    private fun killed(rift: Rift) {
        val killer = rift.npc.findHero(deps.playerList)
        rift.npc.anim(rift.kind.despawnSeq)
        alive.remove(rift)
        if (killer == null || !killer.isValidTarget()) return
        when (rift.kind) {
            Kind.Siphon -> killer.statHeal(HITPOINTS, SIPHON_KILL_HEAL, 0)
            Kind.Augmenter -> {
                killer.statBoost(ATTACK, MELEE_CONSTANT, MELEE_PERCENT)
                killer.statBoost(STRENGTH, MELEE_CONSTANT, MELEE_PERCENT)
                killer.statBoost(RANGED, MAGIC_CONSTANT, MAGIC_PERCENT)
                killer.statBoost(MAGIC, MAGIC_CONSTANT, MAGIC_PERCENT)
            }
            Kind.Rift -> killer.actionDelay = killer.currentMapClock
        }
    }

    private fun remove(rift: Rift) {
        alive.remove(rift)
        if (rift.npc.isSlotAssigned) deps.npcRepo.del(rift.npc, Int.MAX_VALUE)
    }

    companion object {
        const val RESET = "ocellus.rifts_reset"
        const val BUSY = "ocellus.rifts_busy"
        const val STOP = "ocellus.rifts_stop"

        private const val FORCED_MAX_HIT_STYLES = "varn.forced_max_hit_styles"
        private const val FORCED_MELEE = 1
        private const val FORCED_RANGED = 2
        private const val FORCED_MAGIC = 4

        private const val FIRST_AT = 3
        private const val WAVE_GAP = 48
        private const val COUNT = 2
        private const val RIFT_MAX_COUNT = 4
        private const val RIFT_WAVE_GAP = 60
        private const val MIN_DISTANCE = 3
        private const val REACH = 12
        private const val BLOCKED = CollisionFlag.BLOCK_WALK or CollisionFlag.LOC

        private const val CHARGE_BAR = 75
        private const val CHARGE_SEGMENTS = 30
        private const val CHARGE_CYCLES = 840
        private const val DETONATE_AT = 30
        private const val DETONATE_REMOVAL = 2
        private const val DETONATE_PLAYER_SPOT = "spotanim.vfx_human_ocellus_voidflare_buff"
        private const val DETONATE_SPOT_DELAY = 30
        private val DETONATE_DAMAGE = 35..55

        private const val SIPHON_GAP = 4
        private const val SIPHON_HEAL = 14
        private const val SIPHON_KILL_HEAL = 22
        private const val SIPHON_PROJECTILE = "spotanim.vfx_ocellus_voidflare_healing_projanim"
        private const val SIPHON_START_HEIGHT = 160
        private const val SIPHON_END_HEIGHT = 140
        private const val SIPHON_CYCLES = 30
        private const val SIPHON_ANGLE = 2

        private const val HITPOINTS = "stat.hitpoints"
        private const val ATTACK = "stat.attack"
        private const val STRENGTH = "stat.strength"
        private const val RANGED = "stat.ranged"
        private const val MAGIC = "stat.magic"
        private const val MELEE_CONSTANT = 5
        private const val MELEE_PERCENT = 13
        private const val MAGIC_CONSTANT = 4
        private const val MAGIC_PERCENT = 10
    }
}
