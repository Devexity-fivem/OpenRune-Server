package org.rsmod.content.bosses.bork

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ProjAnimType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.instances.InstanceAccess
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.InstanceSpec
import org.rsmod.api.instances.RegionLocal
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid

/**
 * Bork (The Fractured Archive) — duo encounter: Bork plus the Smork Brothers' cannon
 * cart.
 *
 * Wiki-driven mechanics (oldschool.runescape.wiki/w/Bork):
 * - The Smorks fire bones (ranged) and unstable potions (magic) at every player who is
 *   not Bork's current target, alternating style per volley.
 * - Periodically they lob special cannonballs, alternating between incendiary (AoE on a
 *   player tile that leaves burning oil splatters, and also damages Bork if it catches
 *   him) and the heavy cannonball aimed at Bork — which either stuns him (dropping his
 *   flat damage reduction and letting melee hits always land) or, on a miss, sends him
 *   into a triple stomp that covers the arena except the cart's shadow.
 * - Bork's defence is a flat per-hit reduction that grows as the Smorks lose health;
 *   magic damage is reduced much less.
 * - On Bork's death the Smorks enrage (faster, harder volleys); on the Smorks' death
 *   Bork stomps continuously for the rest of the fight.
 */
@Singleton
class BorkEncounter
@Inject
constructor(
    private val deps: BossDeps,
    private val manager: InstanceManager,
    private val npcRepo: NpcRepository,
    private val locRepo: LocRepository,
    private val npcHitModifier: StandardNpcHitModifier,
    private val mapClock: MapClock,
) {
    private val runs = HashMap<Npc, Run>()

    private class Run(
        val session: InstanceSession,
        val enter: CoordGrid,
        val bork: Npc,
        val smorks: Npc,
        val smorkMaxHp: Int,
    ) {
        var stunnedUntil = -1
        var smorkAttackCounter = 0
        var stompsLeft = 0
        var nextStompTick = 0
        val oilTiles = HashMap<CoordGrid, Int>()
        var borkDead = false
        var smorksDead = false
        var finished = false
    }

    fun create(player: Player, access: ProtectedAccess) {
        when (val result = manager.create(player, KEY, SPEC, InstanceAccess.Private, mapClock.cycle)) {
            is InstanceManager.Result.Created -> {
                val run = spawn(result.session, result.enter)
                deps.worldQueues.add(1) { tick(run) }
                access.telejump(result.enter)
                access.mes("You step through the archive portal.")
                manager.finalizeEntry(player, result.session, mapClock.cycle)
            }
            is InstanceManager.Result.Joined -> access.telejump(result.enter)
            is InstanceManager.Result.Failed -> access.mes(result.reason)
        }
    }

    private fun spawn(session: InstanceSession, enter: CoordGrid): Run {
        val borkType = ServerCacheManager.getNpc(BORK_SYMBOL.asRSCM(RSCMType.NPC))!!
        val smorkType = ServerCacheManager.getNpc(SMORK_SYMBOL.asRSCM(RSCMType.NPC))!!
        val bork = Npc(borkType, BorkArena.borkTile(enter))
        val smorks = Npc(smorkType, BorkArena.smorkTile(enter))
        bork.copyStats(borkType)
        smorks.copyStats(smorkType)
        bork.baseHitpointsLvl = BORK_HP
        bork.hitpoints = BORK_HP
        smorks.baseHitpointsLvl = SMORK_HP
        smorks.hitpoints = SMORK_HP
        smorks.movementLocked = true
        npcRepo.add(bork, Int.MAX_VALUE)
        npcRepo.add(smorks, Int.MAX_VALUE)
        manager.attachNpc(session.id, bork)
        manager.attachNpc(session.id, smorks)
        locRepo.add(
            BorkArena.exitLocTile(enter),
            EXIT_LOC_SYMBOL,
            Int.MAX_VALUE,
            LocAngle.West,
            LocShape.CentrepieceStraight,
        )
        val run = Run(session, enter, bork, smorks, SMORK_HP)
        runs[bork] = run
        return run
    }

    private fun runOf(npc: Npc): Run? =
        runs[npc] ?: runs.values.firstOrNull { it.smorks === npc }

    fun onBorkDead(bork: Npc) {
        val run = runs[bork] ?: return
        run.borkDead = true
        if (!run.smorksDead) {
            run.smorks.say("You'll pay for that!")
        }
        finish(run)
    }

    private fun finish(run: Run) {
        run.finished = true
        runs.remove(run.bork)
    }

    fun onSmorksDead(smorks: Npc) {
        val run = runOf(smorks) ?: return
        run.smorksDead = true
        run.bork.say("Bork SMASH!")
    }

    private fun players(run: Run): List<Player> =
        deps.playerList.filter {
            it.coords.level == run.enter.level &&
                it.coords.chebyshevDistance(run.enter) <= 64 &&
                it.hitpoints > 0
        }

    private fun tick(run: Run) {
        if (run.finished || manager.sessionForId(run.session.id) == null) {
            finish(run)
            return
        }
        deps.worldQueues.add(1) { tick(run) }
        val players = players(run)
        if (players.isEmpty()) {
            return
        }
        val tick = mapClock.cycle
        scaleBorkAttackRate(run)
        burnOil(run, players)
        if (run.smorksDead && tick >= run.nextStompTick) {
            queueStomp(run, 1)
            run.nextStompTick = tick + ENRAGED_STOMP_EVERY
        }
        if (!run.smorksDead) {
            run.smorkAttackCounter++
            if (run.smorkAttackCounter % smorkAttackRate(run) == 0) {
                smorkVolley(run, players)
            }
            if (run.smorkAttackCounter % SPECIAL_EVERY == 0) {
                if (run.smorkAttackCounter / SPECIAL_EVERY % 2 == 1) {
                    incendiary(run, players)
                } else {
                    heavyCannonball(run)
                }
            }
        }
        processStomps(run, players, tick)
    }

    private fun scaleBorkAttackRate(run: Run) {
        val encounter = deps.encounterRegistry.of(run.bork)
        if (mapClock.cycle < run.stunnedUntil) {
            encounter.busyUntil = run.stunnedUntil
            return
        }
        encounter.attackRateOverride =
            if (run.smorksDead) {
                BORK_ATTACK_RATE
            } else {
                val scaled =
                    4.0 +
                        (BORK_ATTACK_RATE - 4.0) * run.smorks.hitpoints / run.smorkMaxHp
                scaled.toInt().coerceIn(4, BORK_ATTACK_RATE)
            }
    }

    private fun smorkAttackRate(run: Run): Int =
        if (run.borkDead) SMORK_ENRAGED_RATE else SMORK_ATTACK_RATE

    private fun smorkVolley(run: Run, players: List<Player>) {
        if (mapClock.cycle < run.stunnedUntil) {
            return
        }
        val tank = deps.encounterRegistry.of(run.bork).lastTarget
        val targets =
            if (tank != null && players.size > 1) players.filter { it !== tank } else players
        if (targets.isEmpty()) {
            return
        }
        run.smorks.anim(SEQ_SMORK_ATTACK)
        run.smorks.spotanim(SPOT_SMORK_ATTACK, height = 90)
        val ranged = run.smorkAttackCounter / smorkAttackRate(run) % 2 == 0
        val bonus = if (run.borkDead) ENRAGE_BONUS else 0
        for (target in targets) {
            val travel = 1 + run.smorks.coords.chebyshevDistance(target.coords) / 2
            if (ranged) {
                deps.worldRepo.projAnimSourced(
                    run.smorks, target.coords, spot(SPOT_RANGE_PROJ), CANNON_TRAJECTORY,
                )
                target.queueHit(
                    run.smorks, travel, HitType.Ranged,
                    deps.random.of(RANGED_MAX + bonus), deps.playerHitModifier,
                )
            } else {
                deps.worldRepo.projAnimSourced(
                    run.smorks, target.coords, spot(SPOT_MAGE_PROJ), CANNON_TRAJECTORY,
                )
                deps.worldRepo.spotanimMap(spot(SPOT_MAGE_HIT), target.coords, delay = travel)
                target.queueHit(
                    run.smorks, travel, HitType.Magic,
                    deps.random.of(MAGIC_MAX + bonus), deps.playerHitModifier,
                )
            }
        }
    }

    private fun incendiary(run: Run, players: List<Player>) {
        val target = deps.random.pick(players)
        run.smorks.say(deps.random.pick(INCENDIARY_LINES))
        run.smorks.anim(SEQ_SMORK_BIG_ATTACK)
        run.smorks.spotanim(SPOT_SMORK_BIG_ATTACK, height = 90)
        val tile = target.coords
        val travel = 1 + run.smorks.coords.chebyshevDistance(tile) / 2
        deps.worldRepo.projAnimSourced(run.smorks, tile, spot(SPOT_NAPALM_PROJ), LOB_TRAJECTORY)
        deps.worldQueues.add(travel) {
            val splats =
                listOf(tile, tile.translate(1, 0, 0), tile.translate(0, 1, 0))
            for (splat in splats) {
                deps.worldRepo.spotanimMap(spot(SPOT_NAPALM_SPLAT), splat)
                run.oilTiles[splat] = mapClock.cycle + OIL_LINGER
            }
            for (player in players(run)) {
                if (player.coords.chebyshevDistance(tile) <= 1) {
                    player.queueHit(
                        run.smorks, 0, HitType.Typeless,
                        deps.random.of(INCENDIARY_MAX), deps.playerHitModifier,
                    )
                }
            }
            if (borkCaughtInBlast(run, tile)) {
                val damage =
                    deps.random
                        .of(INCENDIARY_BORK_MIN, INCENDIARY_BORK_MAX)
                        .coerceAtMost((run.bork.hitpoints - 1).coerceAtLeast(1))
                run.bork.queueHit(run.smorks, 0, HitType.Typeless, damage, npcHitModifier)
            }
        }
    }

    private fun heavyCannonball(run: Run) {
        run.smorks.say(deps.random.pick(CANNONBALL_LINES))
        run.smorks.anim(SEQ_SMORK_BIG_ATTACK)
        run.smorks.spotanim(SPOT_SMORK_BIG_ATTACK, height = 90)
        run.smorks.spotanim(SPOT_SMORK_BIG_FLASH, height = 90)
        val tile = borkCentre(run)
        val travel = 1 + run.smorks.coords.chebyshevDistance(tile) / 2 + 1
        deps.worldRepo.projAnimSourced(run.smorks, tile, spot(SPOT_NAPALM_PROJ), LOB_TRAJECTORY)
        deps.worldQueues.add(travel) {
            if (run.finished || run.borkDead || run.smorksDead) {
                return@add
            }
            if (deps.random.of(0, 1) == 0) {
                stunBork(run)
            } else {
                run.bork.say(deps.random.pick(STOMP_LINES))
                queueStomp(run, STOMP_COUNT)
            }
        }
    }

    private fun borkCentre(run: Run): CoordGrid =
        CoordGrid(
            run.bork.coords.x + run.bork.size / 2,
            run.bork.coords.z + run.bork.size / 2,
            run.bork.coords.level,
        )

    private fun borkCaughtInBlast(run: Run, tile: CoordGrid): Boolean {
        for (dx in 0 until run.bork.size) {
            for (dz in 0 until run.bork.size) {
                if (tile.chebyshevDistance(run.bork.coords.translate(dx, dz, 0)) <= 1) {
                    return true
                }
            }
        }
        return false
    }

    private fun stunBork(run: Run) {
        run.bork.anim(SEQ_BORK_STUN)
        run.bork.spotanim(SPOT_BORK_STUN, height = 120)
        run.stunnedUntil = mapClock.cycle + STUN_TICKS
        deps.encounterRegistry.of(run.bork).busyUntil = run.stunnedUntil
    }

    private fun queueStomp(run: Run, stomps: Int) {
        run.stompsLeft = maxOf(run.stompsLeft, stomps)
        if (run.nextStompTick < mapClock.cycle) {
            run.nextStompTick = mapClock.cycle + 1
        }
    }

    private fun processStomps(run: Run, players: List<Player>, tick: Int) {
        if (run.stompsLeft <= 0 || tick < run.nextStompTick || run.borkDead) {
            return
        }
        run.stompsLeft--
        run.nextStompTick = tick + STOMP_INTERVAL
        run.bork.anim(SEQ_BORK_STOMP)
        run.bork.spotanim(SPOT_BORK_STOMP, height = 90)
        val smorkCentre =
            CoordGrid(
                run.smorks.coords.x + run.smorks.size / 2,
                run.smorks.coords.z + run.smorks.size / 2,
                run.smorks.coords.level,
            )
        for (player in players) {
            if (player.coords.chebyshevDistance(smorkCentre) > BorkArena.CART_SHADOW) {
                player.queueHit(
                    run.bork, 1, HitType.Melee,
                    deps.random.of(STOMP_MAX), deps.playerHitModifier,
                )
            }
        }
    }

    private fun burnOil(run: Run, players: List<Player>) {
        val tick = mapClock.cycle
        run.oilTiles.entries.removeIf { tick >= it.value }
        for (player in players) {
            if (run.oilTiles.containsKey(player.coords)) {
                player.queueHit(
                    run.smorks, 0, HitType.Typeless,
                    deps.random.of(OIL_MIN, OIL_MAX), deps.playerHitModifier,
                )
            }
        }
    }

    fun retarget(bork: Npc) {
        val run = runs[bork] ?: return
        val players = players(run)
        val current = deps.encounterRegistry.of(bork).lastTarget
        val next =
            if (players.size > 1) {
                players.filter { it !== current }.let {
                    if (it.isEmpty()) current else deps.random.pick(it)
                }
            } else {
                players.firstOrNull()
            } ?: return
        bork.say("Bork smash ${next.displayName} now!")
        deps.playerInteractions.opTrigger(bork, next, InteractionOp.Op2)
    }

    fun flatArmourReduction(bork: Npc, type: HitType): Int {
        val run = runs[bork] ?: return 0
        if (mapClock.cycle < run.stunnedUntil) {
            return 0
        }
        if (run.smorksDead) {
            return if (type == HitType.Magic) {
                3 * MAGIC_REDUCTION_STEP
            } else {
                FLAT_ARMOUR_BASE + 3 * FLAT_ARMOUR_STEP
            }
        }
        val quarters = (run.smorkMaxHp - run.smorks.hitpoints) * 4 / run.smorkMaxHp
        return if (type == HitType.Magic) {
            quarters.coerceIn(0, 3) * MAGIC_REDUCTION_STEP
        } else {
            FLAT_ARMOUR_BASE + quarters.coerceIn(0, 3) * FLAT_ARMOUR_STEP
        }
    }

    fun isStunned(bork: Npc): Boolean =
        runs[bork]?.let { mapClock.cycle < it.stunnedUntil } ?: false

    fun exit(access: ProtectedAccess) {
        access.telejump(BorkArena.EXIT_RETURN_TILE)
    }

    fun placeEntrance() {
        locRepo.add(
            BorkArena.ENTRANCE_LOC_TILE,
            ENTRANCE_LOC_SYMBOL,
            Int.MAX_VALUE,
            LocAngle.West,
            LocShape.CentrepieceStraight,
        )
    }

    private fun spot(name: String) = SpotanimType(name.asRSCM(RSCMType.SPOTANIM))

    companion object {
        const val KEY = "bork_archive"

        val SPEC: InstanceSpec =
            InstanceSpec(
                fee = 0,
                maxPlayers = 5,
                reclaimTicks = 0,
                graceTicks = 0,
                destroyWhenEmpty = true,
                area =
                    InstanceArea.copyRegions(
                        regionIds = listOf(BorkArena.ENTRANCE_REGION),
                        level = BorkArena.ENTRANCE_LEVEL,
                        enterCoord = RegionLocal(2, 50, 148, 24, 47),
                        exitCoord = BorkArena.EXIT_RETURN_TILE,
                    ),
                settingsRowId = 0,
                bossName = "Bork",
                recommendedCombat = 70..110,
                description = "A troll and his cannon crew in the Fractured Archive.",
            )

        const val BORK_SYMBOL = "npc.tfa_bork"
        const val SMORK_SYMBOL = "npc.tfa_smork"
        const val ENTRANCE_LOC_SYMBOL = "loc.tfa_cave_entrance_op"
        const val EXIT_LOC_SYMBOL = "loc.tfa_cave_exit_op"

        const val BORK_HP = 1500
        const val SMORK_HP = 250

        const val BORK_ATTACK_RATE = 7
        const val SMORK_ATTACK_RATE = 7
        const val SMORK_ENRAGED_RATE = 3
        const val ENRAGE_BONUS = 8
        const val SPECIAL_EVERY = 14
        const val STUN_TICKS = 12
        const val STOMP_COUNT = 3
        const val STOMP_INTERVAL = 4
        const val ENRAGED_STOMP_EVERY = 10
        const val OIL_LINGER = 25

        const val RANGED_MAX = 15
        const val MAGIC_MAX = 18
        const val INCENDIARY_MAX = 30
        const val INCENDIARY_BORK_MIN = 25
        const val INCENDIARY_BORK_MAX = 30
        const val OIL_MIN = 6
        const val OIL_MAX = 7
        const val STOMP_MAX = 55
        const val FLAT_ARMOUR_BASE = 8
        const val FLAT_ARMOUR_STEP = 2
        const val MAGIC_REDUCTION_STEP = 2

        const val SEQ_SMORK_ATTACK = "seq.npc_smork_cannon_attack01"
        const val SEQ_SMORK_BIG_ATTACK = "seq.npc_smork_big_cannon_attack01"
        const val SEQ_BORK_STUN = "seq.npc_bork_12_cycle_stun01"
        const val SEQ_BORK_STOMP = "seq.npc_bork_enraged_stomp_attack01"

        const val SPOT_SMORK_ATTACK = "spotanim.npc_smork_cannon_attack_spotanim01"
        const val SPOT_SMORK_BIG_ATTACK = "spotanim.npc_smork_big_cannon_attack_spotanim01"
        const val SPOT_SMORK_BIG_FLASH = "spotanim.npc_smork_big_cannon_attack_flash_spotanim01"
        const val SPOT_RANGE_PROJ = "spotanim.npc_smork_range_attack_projanim01"
        const val SPOT_MAGE_PROJ = "spotanim.npc_smork_mage_attack_projanim01"
        const val SPOT_MAGE_HIT = "spotanim.npc_smork_mage_hit_spotanim01"
        const val SPOT_NAPALM_PROJ = "spotanim.npc_smork_napalm_attack_projanim01"
        const val SPOT_NAPALM_SPLAT = "spotanim.npc_smork_napalm_splat_spotanim01"
        const val SPOT_BORK_STUN = "spotanim.npc_bork_12_cycle_stun_spotanim01"
        const val SPOT_BORK_STOMP = "spotanim.npc_bork_enraged_stomp_spotanim01"

        val CANNON_TRAJECTORY =
            ProjAnimType(startHeight = 40, endHeight = 24, delay = 10, angle = 6, progress = 15, stepMultiplier = 6)
        val LOB_TRAJECTORY =
            ProjAnimType(startHeight = 110, endHeight = 16, delay = 15, angle = 15, progress = 20, stepMultiplier = 4)

        val INCENDIARY_LINES = listOf("Bork, duck!", "Catch!")
        val CANNONBALL_LINES = listOf("Bork, look out!", "Here it comes!")
        val STOMP_LINES = listOf("Bork smash puny humans flat!", "Bork make big stomp!")
    }
}
