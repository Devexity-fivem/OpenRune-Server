package org.rsmod.content.raids.tfa.raid.encounter.ezekiel

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
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.midiSong
import org.rsmod.api.player.output.mes
import org.rsmod.api.specials.energy.SpecialAttackEnergy
import org.rsmod.api.table.TfaEzekielScalingRow
import org.rsmod.content.raids.tfa.raid.TfaFight
import org.rsmod.content.raids.tfa.raid.TfaRoomCleared
import org.rsmod.content.raids.tfa.raid.encounter.ezekiel.EzekielVarns.CHARGE
import org.rsmod.content.raids.tfa.raid.encounter.ezekiel.EzekielVarns.DONE
import org.rsmod.content.raids.tfa.raid.encounter.ezekiel.EzekielVarns.HP_MAX
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionPlayer
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid

/**
 * One Ezekiel room: the boss, his animated swords (owned by his encounter) and the state the fight
 * keeps between ticks. Handlers register on a scoped [BossExtensionRegistry] so the shared spec
 * reaches this room only.
 */
internal class EzekielFight(
    override val session: InstanceSession,
    val origin: CoordGrid,
    sharedDeps: BossDeps,
    private val hpBar: BossHpBarScript,
    private val interactions: AiPlayerInteractions,
    private val events: EventBus,
    private val energy: SpecialAttackEnergy,
) : TfaFight {
    private val scope = BossExtensionRegistry()
    private val deps = sharedDeps.withExtensions(scope)
    private val registry = sharedDeps.extensionRegistry

    lateinit var boss: Npc
        private set

    private val swords = ArrayList<Npc>()
    private val barOpened = HashSet<Player>()
    private val laneMarked = HashMap<Player, CoordGrid>()
    private var barClosed = false
    private var started = false
    private var startedAt = 0
    private var endedAt: Int? = null

    var tank: Player? = null
        private set

    override val elapsedTicks: Int?
        get() = if (started) (endedAt ?: deps.mapClock.cycle) - startedAt else null

    override val finished: Boolean
        get() = endedAt != null

    fun contains(tile: CoordGrid): Boolean = EzekielArena.inRoom(origin, tile)

    fun fighters(): List<Player> = EzekielArena.players(origin, deps.playerList)

    fun arenaEntry(): CoordGrid = origin.translate(ARENA_ENTRY_DX, ARENA_ENTRY_DZ)

    private fun inFight(player: Player): Boolean =
        !finished && boss.isSlotAssigned && contains(player.coords)

    fun open(): List<Npc> {
        scope.register("ezekiel.bar") { refreshBar() }
        scope.register("ezekiel.bar_close") { closeBar() }
        scope.register("ezekiel.complete") { complete() }
        scope.register("ezekiel.swords") { spawnSwords() }
        scope.register("ezekiel.sword_mark") { ctx ->
            ctx.tile?.let { laneMarked[ctx.target] = it }
        }
        scope.register("ezekiel.sword_tick") { tickSwords() }
        scope.register("ezekiel.sword_resolve") { resolveExpiredSwords() }
        scope.register("ezekiel.enrage_swords") { spawnEnrageSwords() }
        scope.register("ezekiel.pull") { ctx -> pullToCentre(ctx.target) }

        val ezekiel = Npc(BOSS, origin)
        deps.npcRepo.add(ezekiel, Int.MAX_VALUE)
        registry.bind(ezekiel, scope)
        boss = ezekiel
        for (tile in EzekielArena.footprintTiles(origin)) {
            deps.spawnOwnedLoc(ezekiel, tile, BLOCKER, 0, false)
        }
        return listOf(ezekiel)
    }

    fun dispose() {
        registry.unbind(boss)
        for (npc in swords + boss) {
            if (npc.isSlotAssigned) deps.npcRepo.del(npc, Int.MAX_VALUE)
        }
    }

    fun ready(player: Player) {
        if (started || !boss.isSlotAssigned) return
        started = true
        startedAt = deps.mapClock.cycle
        tank = player
        val size = session.occupants.size.coerceIn(MIN_SCALING, MAX_SCALING)
        boss.vars[HP_MAX] = TfaEzekielScalingRow.getRow("dbrow.tfa_ezekiel_scaling_$size").ezekiel
        boss.apRangeOverride = EzekielArena.REACH
        boss.apRequiresLineOfSight = false
        boss.apPlayer2(player, interactions)
        deps.runAbility(boss, player, "begin")
        player.mes("Challenge started: Ezekiel.")
        heartbeat()
    }

    private fun heartbeat() {
        deps.worldQueues.add(1) {
            if (finished || !boss.isSlotAssigned) return@add
            val engaged = (boss.interaction as? InteractionPlayer)?.target
            val usable = engaged != null && engaged.isValidTarget() && contains(engaged.coords)
            if (!usable) {
                fighters()
                    .minByOrNull { EzekielArena.footprintDistance(origin, it.coords) }
                    ?.let {
                        boss.apPlayer2(it, interactions)
                        if (tank?.isValidTarget() != true || !contains(tank!!.coords)) tank = it
                    }
            }
            heartbeat()
        }
    }

    fun run(player: Player, ability: String) {
        deps.runAbility(boss, player, ability)
    }

    fun slotOf(sword: Npc): Int? =
        (0 until EzekielArena.SWORD_SLOTS).firstOrNull {
            EzekielArena.swordTile(origin, it) == sword.spawnCoords
        }

    fun swordList(): List<Npc> = swords

    fun laneOf(slot: Int): List<CoordGrid> = EzekielArena.laneTiles(origin, slot)

    private fun spawnSwords() {
        if (finished) return
        val count = if (session.occupants.size >= BIG_TEAM) SWORD_COUNT_BIG else SWORD_COUNT
        val slots = LinkedHashSet<Int>()
        while (slots.size < count) slots += deps.random.of(EzekielArena.SWORD_SLOTS)
        for (slot in slots) spawnSword(slot)
    }

    private fun spawnEnrageSwords() {
        if (finished) return
        for (slot in EzekielArena.ENRAGE_LANES) spawnSword(slot)
    }

    private fun spawnSword(slot: Int) {
        val sword = Npc(SWORD, EzekielArena.swordTile(origin, slot))
        sword.respawnDir = Direction.South
        sword.moveRestrict = MoveRestrict.NoMove
        sword.ignoreCombatInteractions = true
        sword.vars[CHARGE] = EzekielArena.SWORD_CHARGE_TICKS
        deps.npcRepo.add(sword, Int.MAX_VALUE)
        deps.adoptOwnedNpc(boss, sword)
        swords += sword
    }

    private fun tickSwords() {
        if (finished) return
        for (sword in swords) {
            if (sword.isSlotAssigned) sword.vars[CHARGE] = sword.vars[CHARGE] - 1
        }
    }

    /** Launch resolution: boss damage+stun when a lane crosses him, heal otherwise. */
    private fun resolveExpiredSwords() {
        if (finished) return
        val expired = swords.filter { it.vars[CHARGE] <= 0 }
        if (expired.isEmpty()) return
        var stunned = false
        for (sword in expired) {
            val slot = slotOf(sword)
            if (slot == null) {
                swords -= sword
                if (sword.isSlotAssigned) deps.npcRepo.del(sword, Int.MAX_VALUE)
                continue
            }
            val lane = EzekielArena.laneTiles(origin, slot)
            for (player in fighters()) {
                val marked = laneMarked[player]
                if (marked != null && lane.contains(marked) && !lane.contains(player.coords)) {
                    energy.addSpecialEnergy(player, DODGE_ENERGY)
                }
            }
            laneMarked.entries.removeIf { lane.contains(it.value) }
            swords -= sword
            if (lane.any { EzekielArena.inFootprint(boss.coords, it) }) {
                boss.hitpoints = (boss.hitpoints - EzekielArena.SWORD_HIT_DAMAGE).coerceAtLeast(1)
                stunned = true
            } else {
                val max = boss.vars[HP_MAX].takeIf { it > 0 } ?: boss.baseHitpointsLvl
                boss.hitpoints = minOf(boss.hitpoints + EzekielArena.SWORD_HEAL, max)
            }
        }
        val target = tank ?: fighters().firstOrNull() ?: return
        run(target, if (stunned) "stunned" else "healed")
        updateBar()
    }

    private fun pullToCentre(player: Player) {
        val centre = EzekielArena.centreTile(origin)
        if (!EzekielArena.inFootprint(origin, player.coords)) {
            player.coords = centre
        }
    }

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
        return listOf(
            "[debug] started=$started origin=$origin phase=${encounter.currentPhaseName} " +
                "hp=${boss.hitpoints}/${boss.baseHitpointsLvl} " +
                "swords=${swords.joinToString(",") { it.vars[CHARGE].toString() }} " +
                "tank=${tank?.displayName}",
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
            fighter.mes("Challenge complete: Ezekiel. Duration: <col=ef1020>$duration</col>")
        }
        closeBar()
        playVictorySong()
        events.publish(TfaRoomCleared(session.id, "ezekiel"))
    }

    private fun playVictorySong() {
        if (!boss.isSlotAssigned) return
        for (fighter in fighters()) fighter.midiSong(VICTORY_SONG, fadeOutSpeed = VICTORY_FADE_OUT)
        deps.worldQueues.add(1) { playVictorySong() }
    }

    private companion object {
        const val BOSS = "npc.tfa_ezekiel"
        const val SWORD = "npc.tfa_ezekiel_sword"
        const val MIN_SCALING = 2
        const val MAX_SCALING = 8
        const val BIG_TEAM = 5
        const val SWORD_COUNT = 3
        const val SWORD_COUNT_BIG = 4
        const val FULL_PERCENT = 100
        const val DODGE_ENERGY = 30
        const val ARENA_ENTRY_DX = -6
        const val ARENA_ENTRY_DZ = 1
        const val VICTORY_SONG = 147
        const val VICTORY_FADE_OUT = 50
        const val BLOCKER = "loc.invisible_type8_blocking"
    }
}
