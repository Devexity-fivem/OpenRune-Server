package org.rsmod.content.bosses.zulrah

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.IdentityHashMap
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.repeatTick
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.instances.InstanceId
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.mechanics.toxins.impl.PlayerVenom
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.isValidTarget
import org.rsmod.content.bosses.zulrah.ZulrahRotations.Act
import org.rsmod.content.bosses.zulrah.ZulrahRotations.CLOUD_RING
import org.rsmod.content.bosses.zulrah.ZulrahRotations.Form
import org.rsmod.content.bosses.zulrah.ZulrahRotations.Phase
import org.rsmod.content.bosses.zulrah.ZulrahRotations.Pos
import org.rsmod.content.bosses.zulrah.ZulrahRotations.ROTATIONS
import org.rsmod.content.bosses.zulrah.ZulrahRotations.Style
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.LineValidator

internal class ZulrahFight(
    val npc: Npc,
    val sessionId: InstanceId,
    rotation: Int,
    val xOff: Int,
    val zOff: Int,
) {
    var rotationIdx = rotation
    val rotation: List<Phase>
        get() = ROTATIONS[rotationIdx]

    var phaseIdx = 0
    var actIdx = 0
    var actsLeft = 0
    var jadToggle = false
    val minions = mutableSetOf<Npc>()
    val clouds = mutableMapOf<CoordGrid, LocInfo>()
    var cloudExpiry = mutableMapOf<CoordGrid, Int>()
    var exitLoc: LocInfo? = null
    var ticks = 0
    var ended = false

    fun phase(): Phase = rotation[phaseIdx]

    fun tileOf(pos: Pos): CoordGrid = tileOf(pos.tile)

    fun tileOf(tile: CoordGrid): CoordGrid = tile.translate(xOff, zOff)
}

@Singleton
internal class ZulrahFights
@Inject
constructor(
    private val deps: BossDeps,
    private val instances: InstanceManager,
    private val aiPlayerInteractions: AiPlayerInteractions,
) {
    private val fights: MutableMap<Npc, ZulrahFight> = IdentityHashMap()
    private val lineValidator = LineValidator(deps.collision)

    fun start(player: Player) {
        val session = instances.sessionForPlayer(player) ?: return
        if (bossOf(session) != null) return
        val anchor = instances.resolveCoord(session, Pos.MIDDLE.tile) ?: return
        val type = ServerCacheManager.getNpc(Form.GREEN.npc.asRSCM(RSCMType.NPC)) ?: return

        val npc = Npc(type, anchor)
        npc.mode = NpcMode.None
        npc.movementLocked = true
        npc.apRequiresLineOfSight = false
        deps.npcRepo.add(npc, Int.MAX_VALUE)
        instances.registerSessionNpc(player, npc)

        val fight =
            ZulrahFight(
                npc = npc,
                sessionId = session.id,
                rotation = deps.random.of(ROTATIONS.size),
                xOff = anchor.x - Pos.MIDDLE.tile.x,
                zOff = anchor.z - Pos.MIDDLE.tile.z,
            )
        fights[npc] = fight

        npc.anim(EMERGE_SEQ)
        deps.worldQueues.add(EMERGE_TICKS) { runAct(fight) }
        cloudTicker(fight)
    }

    private fun bossOf(session: InstanceSession): Npc? =
        fights.keys.firstOrNull { it.isSlotAssigned && instances.instanceForNpc(it) == session.id }

    private fun active(fight: ZulrahFight): Boolean =
        !fight.ended && fight.npc.isSlotAssigned

    private fun players(fight: ZulrahFight): List<Player> =
        deps.playerList.filter { instances.sessionForPlayer(it)?.id == fight.sessionId }

    private fun target(fight: ZulrahFight): Player? = players(fight).firstOrNull { it.isValidTarget() }

    private fun step(fight: ZulrahFight, delay: Int, block: () -> Unit) {
        deps.worldQueues.add(delay) { if (active(fight)) block() }
    }

    private fun runAct(fight: ZulrahFight) {
        if (!active(fight)) return
        val phase = fight.phase()
        if (fight.actIdx >= phase.acts.size) {
            dive(fight)
            return
        }
        when (val act = phase.acts[fight.actIdx]) {
            is Act.Attack -> {
                if (fight.actsLeft == 0) fight.actsLeft = act.count
                fireAttack(fight, resolveStyle(fight, act.style))
                if (--fight.actsLeft <= 0) fight.actIdx++
                step(fight, ATTACK_GAP) { runAct(fight) }
            }
            is Act.Jad -> {
                if (fight.actsLeft == 0) fight.actsLeft = act.count
                val style = if (fight.jadToggle) otherStyle(act.start) else act.start
                fight.jadToggle = !fight.jadToggle
                fireAttack(fight, style)
                if (--fight.actsLeft <= 0) {
                    fight.jadToggle = false
                    fight.actIdx++
                }
                step(fight, ATTACK_GAP) { runAct(fight) }
            }
            is Act.TailSlam -> {
                if (fight.actsLeft == 0) fight.actsLeft = act.count
                tailSlam(fight)
                if (--fight.actsLeft <= 0) fight.actIdx++
                step(fight, SLAM_GAP) { runAct(fight) }
            }
            is Act.Orbs -> {
                if (fight.actsLeft == 0) fight.actsLeft = act.count
                launchOrb(fight)
                if (--fight.actsLeft <= 0) fight.actIdx++
                step(fight, ORB_GAP) { runAct(fight) }
            }
            is Act.Clouds -> {
                if (fight.actsLeft == 0) fight.actsLeft = act.count
                launchCloud(fight)
                if (--fight.actsLeft <= 0) fight.actIdx++
                step(fight, CLOUD_GAP) { runAct(fight) }
            }
        }
    }

    private fun resolveStyle(fight: ZulrahFight, style: Style): Style =
        when (style) {
            Style.TANZANITE -> if (deps.random.of(100) < TANZANITE_MAGIC_CHANCE) Style.MAGIC else Style.RANGED
            else -> style
        }

    private fun otherStyle(style: Style): Style =
        if (style == Style.RANGED) Style.MAGIC else Style.RANGED

    private fun fireAttack(fight: ZulrahFight, style: Style) {
        val npc = fight.npc
        val player = target(fight) ?: return
        npc.anim(if (style == Style.RANGED) RANGED_ATTACK_SEQ else MAGIC_ATTACK_SEQ)
        npc.facePlayer(player)
        val damage = deps.random.of(0, MAX_HIT)
        if (damage > 0) PlayerVenom.tryVenom(player)
        player.queueHit(
            npc,
            HIT_DELAY,
            if (style == Style.RANGED) HitType.Ranged else HitType.Magic,
            damage,
            deps.playerHitModifier,
        )
    }

    private fun tailSlam(fight: ZulrahFight) {
        val npc = fight.npc
        val player = target(fight) ?: return
        val marked = player.coords
        npc.faceSquare(marked)
        step(fight, SLAM_DELAY) {
            npc.anim(if (deps.random.of(2) == 0) TAIL_LEFT_SEQ else TAIL_RIGHT_SEQ)
            for (p in players(fight)) {
                if (p.coords.chebyshevDistance(marked) <= SLAM_RADIUS) {
                    p.queueHit(npc, 1, HitType.Typeless, deps.random.of(SLAM_MIN_HIT, SLAM_MAX_HIT), deps.playerHitModifier)
                    CombatEffects.freeze(p, SLAM_STUN_TICKS)
                }
            }
        }
    }

    private fun launchOrb(fight: ZulrahFight) {
        val player = target(fight) ?: return
        npcSpot(fight, ORB_SPOT)
        val tile = freeTileNear(player.coords)
        val snakeling = if (deps.random.of(2) == 0) MINION_MAGIC else MINION_MELEE
        step(fight, ORB_SPAWN_DELAY) { spawnSnakeling(fight, snakeling, tile) }
    }

    private fun spawnSnakeling(fight: ZulrahFight, snakeling: String, tile: CoordGrid) {
        val type = ServerCacheManager.getNpc(snakeling.asRSCM(RSCMType.NPC)) ?: return
        val minion = Npc(type, tile)
        minion.apRequiresLineOfSight = false
        deps.npcRepo.add(minion, Int.MAX_VALUE)
        fight.minions += minion
        fight.npc.let { boss -> players(fight).firstOrNull()?.let { minion.apPlayer2(it, aiPlayerInteractions) } }
        step(fight, SNAKELING_TTL) {
            if (minion.isSlotAssigned) deps.npcRepo.del(minion, Int.MAX_VALUE)
        }
    }

    private fun launchCloud(fight: ZulrahFight) {
        target(fight) ?: return
        npcSpot(fight, CLOUD_ORB_SPOT)
        val tile =
            deps.random.pickOrNull(
                CLOUD_RING.map(fight::tileOf).filterNot(fight.clouds::containsKey)
            )
        step(fight, CLOUD_SPAWN_DELAY) { tile?.let { spawnCloud(fight, it) } }
    }

    private fun spawnCloud(fight: ZulrahFight, tile: CoordGrid) {
        if (fight.clouds.containsKey(tile)) return
        val info = deps.locRepo.add(tile, CLOUD_LOC, Int.MAX_VALUE, LocAngle[0], LocShape.CentrepieceStraight)
        if (info != null) {
            fight.clouds[tile] = info
            fight.cloudExpiry[tile] = fight.ticks + CLOUD_TTL
        }
    }

    private fun cloudTicker(fight: ZulrahFight) {
        deps.repeatTick(
            ticks = Int.MAX_VALUE,
            onTick = { _ ->
                if (!active(fight)) return@repeatTick false
                fight.ticks++
                val expired = fight.cloudExpiry.filterValues { fight.ticks >= it }.keys
                expired.forEach { tile ->
                    fight.clouds.remove(tile)?.let { deps.locRepo.del(it, Int.MAX_VALUE) }
                    fight.cloudExpiry.remove(tile)
                }
                for (p in players(fight)) {
                    if (inCloud(fight, p.coords)) {
                        p.queueHit(fight.npc, 1, HitType.Typeless, deps.random.of(1, CLOUD_MAX_HIT), deps.playerHitModifier)
                    }
                }
                true
            },
        )
    }

    // The cloud loc anchors a 3x3 hazard extending north-east of its tile.
    private fun inCloud(fight: ZulrahFight, tile: CoordGrid): Boolean =
        fight.clouds.keys.any { tile.x - it.x in 0..2 && tile.z - it.z in 0..2 }

    private fun dive(fight: ZulrahFight) {
        val npc = fight.npc
        npc.anim(SINK_SEQ)
        step(fight, DIVE_TICKS) {
            // Each rotation's shared closing phase counts as the first phase of
            // the next rotation, so a fresh rotation resumes at index 1.
            if (fight.phaseIdx + 1 >= fight.rotation.size) {
                fight.rotationIdx = deps.random.of(ROTATIONS.size)
                fight.phaseIdx = 1
            } else {
                fight.phaseIdx++
            }
            fight.actIdx = 0
            fight.actsLeft = 0
            fight.jadToggle = false
            val phase = fight.phase()
            npc.telejump(deps.collision, fight.tileOf(phase.pos))
            val into = ServerCacheManager.getNpc(phase.form.npc.asRSCM(RSCMType.NPC))
            if (into != null) npc.transmog(into, Int.MAX_VALUE)
            npc.anim(EMERGE_SEQ)
            target(fight)?.let(npc::facePlayer)
            step(fight, EMERGE_TICKS) { runAct(fight) }
        }
    }

    private fun npcSpot(fight: ZulrahFight, spot: String) =
        fight.npc.spotanim(spot, 0, ORB_HEIGHT, 0)

    private fun freeTileNear(centre: CoordGrid): CoordGrid =
        centre.translate(deps.random.of(-3, 3), deps.random.of(-3, 3))

    // Drops land under the owner and the Zul-andra teleport scroll spawns on a
    // walkable tile nearby, matching OSRS. Returns the tile drops should use.
    fun onDeath(npc: Npc): CoordGrid? {
        val fight = fights[npc] ?: return null
        fight.ended = true
        npc.anim(DEATH_SEQ)
        fight.minions.forEach { if (it.isSlotAssigned) deps.npcRepo.del(it, Int.MAX_VALUE) }
        fight.minions.clear()
        fight.clouds.values.forEach { deps.locRepo.del(it, Int.MAX_VALUE) }
        fight.clouds.clear()
        fight.cloudExpiry.clear()
        val owner = players(fight).firstOrNull() ?: return null
        exitTile(owner.coords)?.let { tile ->
            fight.exitLoc =
                deps.locRepo.add(
                    tile,
                    EXIT_SCROLL_LOC,
                    Int.MAX_VALUE,
                    LocAngle.West,
                    LocShape.CentrepieceStraight,
                )
        }
        return owner.coords
    }

    private fun exitTile(origin: CoordGrid): CoordGrid? {
        for (radius in 1..2) {
            val candidates =
                buildList {
                    for (x in -radius..radius) for (z in -radius..radius) {
                        if (maxOf(kotlin.math.abs(x), kotlin.math.abs(z)) != radius) continue
                        val tile = origin.translate(x, z)
                        if (lineValidator.hasLineOfWalk(origin.level, origin.x, origin.z, tile.x, tile.z)) {
                            add(tile)
                        }
                    }
                }
            if (candidates.isNotEmpty()) return deps.random.pickOrNull(candidates)
        }
        return null
    }

    fun onDeleted(npc: Npc) {
        val fight = fights.remove(npc) ?: return
        fight.ended = true
        fight.minions.forEach { if (it.isSlotAssigned) deps.npcRepo.del(it, Int.MAX_VALUE) }
        fight.clouds.values.forEach { deps.locRepo.del(it, Int.MAX_VALUE) }
        fight.exitLoc?.let { deps.locRepo.del(it, Int.MAX_VALUE) }
    }

    private companion object {
        private const val EMERGE_SEQ = "seq.snakeboss_emergefast"
        private const val SINK_SEQ = "seq.snakeboss_sinkfast"
        private const val DEATH_SEQ = "seq.snakeboss_death"
        private const val TAIL_LEFT_SEQ = "seq.snakeboss_attack_tail_left"
        private const val TAIL_RIGHT_SEQ = "seq.snakeboss_attack_tail_right"

        private const val RANGED_ATTACK_SEQ = "seq.snakeboss_attack_acidx1"
        private const val MAGIC_ATTACK_SEQ = "seq.snakeboss_attack_acidx3"
        private const val ORB_SPOT = "spotanim.snakeboss_orb"
        private const val CLOUD_ORB_SPOT = "spotanim.snakeboss_double_orb"
        private const val ORB_HEIGHT = 100
        private const val CLOUD_LOC = "loc.snakeboss_poisoncloud"
        private const val EXIT_SCROLL_LOC = "loc.snakeboss_exit"

        private const val MINION_MELEE = "npc.snakeboss_minion_melee"
        private const val MINION_MAGIC = "npc.snakeboss_minion_magic"

        private const val EMERGE_TICKS = 3
        private const val DIVE_TICKS = 3
        private const val ATTACK_GAP = 4
        private const val ORB_GAP = 3
        private const val ORB_SPAWN_DELAY = 4
        private const val CLOUD_GAP = 2
        private const val CLOUD_SPAWN_DELAY = 3
        private const val SLAM_GAP = 8
        private const val SLAM_DELAY = 4
        private const val SLAM_RADIUS = 2
        private const val SLAM_MIN_HIT = 20
        private const val SLAM_MAX_HIT = 30
        private const val SLAM_STUN_TICKS = 5
        private const val MAX_HIT = 41
        private const val TANZANITE_MAGIC_CHANCE = 70
        private const val SNAKELING_TTL = 66
        private const val CLOUD_TTL = 30
        private const val CLOUD_MAX_HIT = 6
        private const val HIT_DELAY = 2
    }
}
