package org.rsmod.content.raids.tfa.raid.encounter.moia

import dev.openrune.types.MoveRestrict
import org.rsmod.api.bossbar.plugin.BossHpBarScript
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossExtensionRegistry
import org.rsmod.api.bosses.runtime.adoptOwnedNpc
import org.rsmod.api.bosses.runtime.runAbility
import org.rsmod.api.bosses.runtime.spawnOwnedLoc
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.timer.formatInstanceKillTime
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.midiSong
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.table.TfaMoiaScalingRow
import org.rsmod.content.raids.tfa.raid.TfaFight
import org.rsmod.content.raids.tfa.raid.TfaRoomCleared
import org.rsmod.content.raids.tfa.raid.encounter.moia.MoiaVarns.COUGH_CD
import org.rsmod.content.raids.tfa.raid.encounter.moia.MoiaVarns.DONE
import org.rsmod.content.raids.tfa.raid.encounter.moia.MoiaVarns.ENRAGE_ROUND
import org.rsmod.content.raids.tfa.raid.encounter.moia.MoiaVarns.HP_MAX
import org.rsmod.content.raids.tfa.raid.encounter.moia.MoiaVarns.INFECTED
import org.rsmod.content.raids.tfa.raid.encounter.moia.MoiaVarns.SIDE
import org.rsmod.content.raids.tfa.raid.encounter.moia.MoiaVarns.SLOT
import org.rsmod.content.raids.tfa.raid.encounter.moia.MoiaVarns.TP_CD
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.interact.InteractionPlayer
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid

/**
 * One Moia room: the boss, her undead binders (owned by her encounter) and the state the fight keeps
 * between ticks. Handlers register on a scoped [BossExtensionRegistry] so the shared spec reaches
 * this room only.
 */
internal class MoiaFight(
    override val session: InstanceSession,
    val origin: CoordGrid,
    sharedDeps: BossDeps,
    private val hpBar: BossHpBarScript,
    private val interactions: AiPlayerInteractions,
    private val npcHitModifier: NpcHitModifier,
    private val events: EventBus,
) : TfaFight {
    private val scope = BossExtensionRegistry()
    private val deps = sharedDeps.withExtensions(scope)
    private val registry = sharedDeps.extensionRegistry

    lateinit var boss: Npc
        private set

    private val binders = HashMap<Int, Npc>()
    private val respawns = HashMap<Int, Int>()
    private val infected = HashMap<Player, Int>()
    private val barOpened = HashSet<Player>()
    private var barClosed = false
    private var started = false
    private var startedAt = 0
    private var endedAt: Int? = null
    private var enraged = false
    private var countdown = 0
    private var countdownMax = MoiaArena.COUNTDOWN_FIRST
    private var round = 0
    private var shuffled = 0

    var tank: Player? = null
        private set

    override val elapsedTicks: Int?
        get() = if (started) (endedAt ?: deps.mapClock.cycle) - startedAt else null

    override val finished: Boolean
        get() = endedAt != null

    fun contains(tile: CoordGrid): Boolean = MoiaArena.inRoom(origin, tile)

    fun fighters(): List<Player> = MoiaArena.players(origin, deps.playerList)

    fun arenaEntry(): CoordGrid = MoiaArena.arenaEntry(origin)

    fun open(): List<Npc> {
        scope.register("moia.bar") { refreshBar() }
        scope.register("moia.bar_fade") { fighters().forEach(hpBar::fadeOut) }
        scope.register("moia.bar_close") { closeBar() }
        scope.register("moia.complete") { complete() }
        scope.register("moia.binders") { spawnBinders() }
        scope.register("moia.infect") { ctx -> infect(ctx.target) }
        scope.register("moia.diamond") { diamond() }
        scope.register("moia.enrage_tick") { countdownTick() }
        scope.register("moia.enrage_charge") { ctx ->
            enrageCharge()
            ctx.target?.let { deps.runAbility(boss, it, "enrage_charge") }
        }

        val moia = Npc(BOSS, origin)
        moia.respawnDir = Direction.South
        moia.moveRestrict = MoveRestrict.NoMove
        moia.setHunt(0)
        deps.npcRepo.add(moia, Int.MAX_VALUE)
        registry.bind(moia, scope)
        boss = moia
        for (tile in footprintTiles(origin)) {
            deps.spawnOwnedLoc(moia, tile, BLOCKER, 0, false)
        }
        return listOf(moia)
    }

    fun dispose() {
        registry.unbind(boss)
        for (npc in binders.values + boss) {
            if (npc.isSlotAssigned) deps.npcRepo.del(npc, Int.MAX_VALUE)
        }
    }

    fun ready(player: Player) {
        if (started || !boss.isSlotAssigned) return
        started = true
        startedAt = deps.mapClock.cycle
        tank = player
        val size = session.occupants.size.coerceIn(MIN_SCALING, MAX_SCALING)
        boss.vars[HP_MAX] = TfaMoiaScalingRow.getRow("dbrow.tfa_moia_scaling_$size").moia
        boss.vars[SIDE] = SIDE_SOUTH
        boss.apRangeOverride = MoiaArena.REACH
        boss.apRequiresLineOfSight = false
        boss.apPlayer2(player, interactions)
        deps.runAbility(boss, player, "begin")
        player.mes("Challenge started: Moia.")
        heartbeat()
    }

    private fun heartbeat() {
        deps.worldQueues.add(1) {
            if (finished || !boss.isSlotAssigned) return@add
            keepEngaged()
            tickBinders()
            tickRespawns()
            tickInfection()
            heartbeat()
        }
    }

    private fun keepEngaged() {
        val engaged = (boss.interaction as? InteractionPlayer)?.target
        val usable = engaged != null && engaged.isValidTarget() && contains(engaged.coords)
        if (!usable) {
            fighters()
                .minByOrNull { MoiaArena.footprintDistance(origin, it.coords) }
                ?.let {
                    boss.apPlayer2(it, interactions)
                    if (tank?.isValidTarget() != true || !contains(tank!!.coords)) tank = it
                }
        }
    }

    // ---- binders --------------------------------------------------------------------------

    private fun spawnBinders() {
        if (finished) return
        for (slot in 0 until MoiaArena.BINDER_SLOTS) {
            if (slot == MoiaArena.WEST_SLOT) continue
            spawnBinder(slot)
        }
    }

    private fun spawnBinder(slot: Int) {
        if (finished || binders.containsKey(slot)) return
        val binder = Npc(MINION, MoiaArena.binderTile(origin, slot))
        binder.respawnDir = Direction.South
        binder.moveRestrict = MoveRestrict.NoMove
        binder.vars[SLOT] = slot
        binder.apRangeOverride = MoiaArena.BINDER_REACH
        binder.apRequiresLineOfSight = false
        deps.npcRepo.add(binder, Int.MAX_VALUE)
        deps.adoptOwnedNpc(boss, binder)
        binders[slot] = binder
    }

    private fun freeBinder(slot: Int) {
        respawns[slot] = MoiaArena.RESPAWN_TICKS
    }

    private fun tickRespawns() {
        val iter = respawns.entries.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            val left = entry.value - 1
            if (left <= 0) {
                iter.remove()
                if (!binders.containsKey(entry.key)) spawnBinder(entry.key)
            } else {
                entry.setValue(left)
            }
        }
    }

    /** Perimeter slots rotate clockwise; dead slots are picked up by the respawn queue. */
    private fun tickBinders() {
        shuffled++
        for (binder in binders.values.toList()) {
            if (!binder.isSlotAssigned) {
                binders.values.remove(binder)
                freeBinder(binder.vars[SLOT])
                continue
            }
            engageBinder(binder)
        }
        if (shuffled >= MoiaArena.SHUFFLE_TICKS) {
            shuffled = 0
            shuffleBinders()
        }
        pushCheck()
    }

    private fun engageBinder(binder: Npc) {
        val engaged = (binder.interaction as? InteractionPlayer)?.target
        if (engaged != null && engaged.isValidTarget() && contains(engaged.coords)) return
        fighters()
            .filter { it.coords.chebyshevDistance(binder.coords) <= MoiaArena.BINDER_REACH + 2 }
            .minByOrNull { it.coords.chebyshevDistance(binder.coords) }
            ?.let { binder.apPlayer2(it, interactions) }
    }

    private fun shuffleBinders() {
        val cycle = MoiaArena.PERIMETER
        val next = HashMap<Int, Npc>()
        for (binder in binders.values.toList()) {
            if (!binder.isSlotAssigned) continue
            val slot = binder.vars[SLOT]
            if (slot == MoiaArena.CENTRE_SLOT) {
                next[slot] = binder
                continue
            }
            val pos = cycle.indexOf(slot)
            val dest = if (pos < 0) slot else cycle[(pos + 1) % cycle.size]
            if (dest != slot) {
                binder.spotanim(EMPOWER_OUT, 0, 0, 0)
                binder.coords = MoiaArena.binderTile(origin, dest)
                binder.spotanim(EMPOWER_IN, 0, 0, 0)
                binder.vars[SLOT] = dest
            }
            next[dest] = binder
        }
        binders.clear()
        binders.putAll(next)
    }

    /** Stacked players on a binder's tile get shoved off and share one damage instance. */
    private fun pushCheck() {
        for (binder in binders.values) {
            if (!binder.isSlotAssigned) continue
            val stacked = fighters().filter { it.coords == binder.coords }
            if (stacked.size < 2) continue
            val victim = stacked.first()
            victim.queueHit(
                binder,
                1,
                HitType.Melee,
                deps.random.of(1..MoiaArena.PUSH_DAMAGE),
                deps.playerHitModifier,
            )
            for (player in stacked) {
                player.coords = freeAdjacent(player, binder.coords) ?: continue
            }
            break
        }
    }

    private fun freeAdjacent(player: Player, tile: CoordGrid): CoordGrid? {
        for (dx in -1..1) {
            for (dz in -1..1) {
                if (dx == 0 && dz == 0) continue
                val dest = tile.translate(dx, dz)
                if (MoiaArena.contains(origin, dest) && binders.values.none { it.coords == dest }) {
                    return dest
                }
            }
        }
        return null
    }

    // ---- cough ----------------------------------------------------------------------------

    private fun infect(player: Player?) {
        if (player == null || finished || !contains(player.coords)) return
        if (infected.putIfAbsent(player, 0) != null) return
        player.mes("You feel an awful cough coming on.")
    }

    private fun tickInfection() {
        if (infected.isEmpty()) return
        val iter = infected.entries.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            val player = entry.key
            val stage = entry.value
            if (!player.isValidTarget() || !contains(player.coords)) {
                iter.remove()
                continue
            }
            val cycle = stage / MoiaArena.INFECT_TICKS
            if (stage % MoiaArena.INFECT_TICKS == 0) {
                if (cycle >= MoiaArena.INFECT_CYCLES) {
                    player.mes("Your cough dissipates.")
                    iter.remove()
                    continue
                }
                player.queueHit(
                    boss,
                    1,
                    HitType.Typeless,
                    MoiaArena.infectDamage(cycle),
                    deps.playerHitModifier,
                )
                player.statSub("stat.prayer", MoiaArena.INFECT_PRAYER_DRAIN, 0)
                spread(player)
            }
            entry.setValue(stage + 1)
        }
    }

    private fun spread(source: Player) {
        for (player in fighters()) {
            if (player === source || infected.containsKey(player)) continue
            if (player.coords.chebyshevDistance(source.coords) <= 1) {
                infect(player)
                player.mes("The cough spreads to you.")
            }
        }
        for (binder in binders.values) {
            if (!binder.isSlotAssigned || binder.vars[INFECTED] != 0) continue
            if (binder.coords.chebyshevDistance(source.coords) <= 1) {
                binder.vars[INFECTED] = 1
                binder.queueHit(
                    source,
                    1,
                    HitType.Typeless,
                    MoiaArena.INFECT_BINDER_DAMAGE,
                    npcHitModifier,
                )
            }
        }
    }

    // ---- boss ------------------------------------------------------------------------------

    /** Picks a side different from the current one and returns her hover tile there. */
    fun nextSideTile(): CoordGrid {
        val current = boss.vars[SIDE]
        var next = deps.random.of(MoiaArena.SIDE_EAST + 1)
        if (next == current) next = (next + 1) % (MoiaArena.SIDE_EAST + 1)
        boss.vars[SIDE] = next
        return MoiaArena.hoverSpawn(origin, next)
    }

    /** Enrage: binders collapse into the full perimeter diamond around the centre. */
    private fun diamond() {
        for (slot in MoiaArena.PERIMETER) {
            if (!binders.containsKey(slot) || !binders[slot]!!.isSlotAssigned) spawnBinder(slot)
        }
        for ((slot, binder) in binders) {
            if (slot == MoiaArena.CENTRE_SLOT || !binder.isSlotAssigned) continue
            binder.coords = MoiaArena.binderTile(origin, slot)
        }
        val centre = binders.remove(MoiaArena.CENTRE_SLOT)
        if (centre != null && centre.isSlotAssigned) deps.npcRepo.del(centre, Int.MAX_VALUE)
        boss.coords = MoiaArena.enrageSpawn(origin)
    }

    fun enrageCharge() {
        countdown = countdownMax
        countdownMax =
            (countdownMax - MoiaArena.COUNTDOWN_STEP).coerceAtLeast(MoiaArena.COUNTDOWN_MIN)
        boss.vars[ENRAGE_ROUND] = round++
        enraged = true
    }

    private fun countdownTick() {
        if (finished || !enraged) return
        countdown--
        if (countdown > 0) return
        val alive = binders.values.count { it.isSlotAssigned }
        if (alive > 0) {
            val perBinder = deps.random.of(MoiaArena.ENRAGE_BLOW_MIN..MoiaArena.ENRAGE_BLOW_MAX)
            for (player in fighters()) {
                player.queueHit(
                    boss,
                    1,
                    HitType.Typeless,
                    perBinder * alive,
                    deps.playerHitModifier,
                )
            }
        }
        respawnWave()
        enrageCharge()
        run(fighters().firstOrNull() ?: return, "enrage_charge")
    }

    private fun respawnWave() {
        for (slot in MoiaArena.PERIMETER) {
            val binder = binders[slot]
            if (binder == null || !binder.isSlotAssigned) {
                binders.remove(slot)
                respawns.remove(slot)
                spawnBinder(slot)
            }
        }
    }

    fun run(player: Player, ability: String) {
        deps.runAbility(boss, player, ability)
    }

    fun binderList(): List<Npc> = binders.values.toList()

    // ---- bar + completion -----------------------------------------------------------------

    fun refreshBar() {
        if (barClosed) return
        for (player in fighters()) {
            if (barOpened.add(player)) hpBar.onOpen(player, boss)
            pushHp(player)
        }
    }

    fun updateBar() {
        if (barClosed) return
        for (player in fighters()) pushHp(player)
    }

    private fun pushHp(player: Player) {
        val hp = if (boss.hitpoints <= 1) 0 else boss.hitpoints
        hpBar.onUpdate(player, boss, hp, boss.baseHitpointsLvl)
    }

    fun closeBar() {
        if (barClosed) return
        barClosed = true
        fighters().forEach(hpBar::onDeathClose)
    }

    fun setHealthPercent(percent: Int): Boolean {
        if (!started || !boss.isSlotAssigned) return false
        boss.hitpoints = maxOf(1, (boss.baseHitpointsLvl.toLong() * percent / FULL_PERCENT).toInt())
        updateBar()
        return true
    }

    fun debugState(): List<String> {
        val encounter = deps.encounterRegistry.of(boss)
        val nearest = fighters().minByOrNull { it.coords.chebyshevDistance(boss.coords) }
        return listOf(
            "[debug] started=$started enraged=$enraged countdown=$countdown origin=$origin " +
                "phase=${encounter.currentPhaseName} hp=${boss.hitpoints}/${boss.baseHitpointsLvl} " +
                "binders=${binders.values.count { it.isSlotAssigned }}/${binderList().size} " +
                "side=${boss.vars[SIDE]}",
            "[debug2] spawn=${boss.spawnCoords} targetDist=" +
                "${nearest?.coords?.let { boss.spawnCoords.chebyshevDistance(it) }} " +
                "visMax=${boss.visType.maxRange} visAtk=${boss.visType.attackRange} " +
                "atk=${boss.attackRange} mode=${boss.mode} scripted=${boss.scriptedMovement} " +
                "locked=${boss.movementLocked} interact=${boss.interaction?.javaClass?.simpleName} " +
                "tpcd=${boss.vars[TP_CD]} coughcd=${boss.vars[COUGH_CD]}",
        )
    }

    private fun complete() {
        if (finished) return
        val now = deps.mapClock.cycle
        endedAt = now
        boss.vars[DONE] = 1
        for (fighter in fighters()) {
            val precise = fighter.vars["varbit.option_precise_timing"] != 0
            val duration = formatInstanceKillTime(now - startedAt, precise)
            fighter.mes("Challenge complete: Moia. Duration: <col=ef1020>$duration</col>")
        }
        for (binder in binders.values) {
            if (binder.isSlotAssigned) deps.npcRepo.del(binder, Int.MAX_VALUE)
        }
        binders.clear()
        closeBar()
        playVictorySong()
        events.publish(TfaRoomCleared(session.id, "moia"))
    }

    private fun playVictorySong() {
        if (!boss.isSlotAssigned) return
        for (fighter in fighters()) fighter.midiSong(VICTORY_SONG, fadeOutSpeed = VICTORY_FADE_OUT)
        deps.worldQueues.add(1) { playVictorySong() }
    }

    private fun footprintTiles(origin: CoordGrid): List<CoordGrid> =
        buildList { for (dx in 0..2) for (dz in 0..2) add(origin.translate(dx, dz)) }

    private companion object {
        const val BOSS = "npc.tfa_moia"
        const val MINION = "npc.tfa_moia_minion"
        const val BLOCKER = "loc.invisible_type8_blocking"
        const val EMPOWER_IN = "spotanim.moia_minion_empower_in_spotanim_01"
        const val EMPOWER_OUT = "spotanim.moia_minion_empower_spotanim_out_01"
        const val SIDE_SOUTH = 0
        const val MIN_SCALING = 2
        const val MAX_SCALING = 8
        const val FULL_PERCENT = 100
        const val VICTORY_SONG = 147
        const val VICTORY_FADE_OUT = 50
    }
}
