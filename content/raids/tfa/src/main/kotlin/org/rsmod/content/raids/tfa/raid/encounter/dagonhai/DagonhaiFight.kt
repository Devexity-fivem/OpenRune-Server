package org.rsmod.content.raids.tfa.raid.encounter.dagonhai

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.MoveRestrict
import dev.openrune.types.NpcServerType
import dev.openrune.types.aconverted.SpotanimType
import org.rsmod.api.bossbar.plugin.BossHpBarScript
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossExtensionRegistry
import org.rsmod.api.bosses.runtime.runAbility
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.instances.timer.formatInstanceKillTime
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.midiSong
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.statAdd
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.table.TfaDagonhaiScalingRow
import org.rsmod.content.raids.tfa.raid.TfaFight
import org.rsmod.content.raids.tfa.raid.TfaRoomCleared
import org.rsmod.content.raids.tfa.raid.encounter.dagonhai.DagonhaiVarns.DONE
import org.rsmod.content.raids.tfa.raid.encounter.dagonhai.DagonhaiVarns.ELEM
import org.rsmod.content.raids.tfa.raid.encounter.dagonhai.DagonhaiVarns.FINAL
import org.rsmod.content.raids.tfa.raid.encounter.dagonhai.DagonhaiVarns.HP_MAX
import org.rsmod.content.raids.tfa.raid.encounter.dagonhai.DagonhaiVarns.SLOT
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.headbar.Headbar
import org.rsmod.game.hit.HitType
import org.rsmod.game.interact.InteractionPlayer
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid

/**
 * One Dagon'hai ritual chamber: the Elder, two Monks, the cauldron, and the rift/imp phases that
 * play out between shield breaks. All three ritualists share one health pool; their hitpoints are
 * kept in sync after every hit so the boss bar reads the pool.
 */
internal class DagonhaiFight(
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

    private val monks = HashMap<Int, Npc>()
    private var cauldron: Npc? = null
    private val rifts = HashMap<Int, Npc>()
    private val imps = HashSet<Npc>()
    private val impsSpawned = HashMap<Int, Int>()
    private val members = HashSet<Npc>()

    private val barOpened = HashSet<Player>()
    private var barClosed = false
    private var started = false
    private var startedAt = 0
    private var endedAt: Int? = null

    private var trioHp = 0
    private var hpMax = 0
    private var breaks = 0
    private var ritual = 0
    private var ritualTimer = 0
    private var rotation = 0
    private var nextElem = 0
    private var nextGod = 0
    private var cauldronTimer = 0
    private var cauldronSlot = -1
    private var restoreTimer = -1
    private var impTimer = 0
    private var empowerTimer = -1
    private var channel: Channel? = null
    private var tome: Tome? = null
    private val stunned = HashMap<Npc, Int>()
    private val boosted = HashSet<Player>()
    private var finalPhase = false

    private data class Channel(val npc: Npc, val elem: Int, val endTick: Int)

    private data class Tome(val npc: Npc, val god: Int)

    var tank: Player? = null
        private set

    override val elapsedTicks: Int?
        get() = if (started) (endedAt ?: deps.mapClock.cycle) - startedAt else null

    override val finished: Boolean
        get() = endedAt != null

    fun contains(tile: CoordGrid): Boolean = DagonhaiArena.inRoom(origin, tile)

    fun fighters(): List<Player> = DagonhaiArena.players(origin, deps.playerList)

    fun arenaEntry(): CoordGrid = DagonhaiArena.arenaEntry(origin)

    fun memberOf(npc: Npc): Boolean = npc in members

    fun cauldronCoords(): CoordGrid = cauldron?.coords ?: DagonhaiArena.cauldronHome(origin)

    fun laneTiles(slot: Int): List<CoordGrid> = DagonhaiArena.tomeLane(origin, slot)

    private fun trioAll(): List<Npc> =
        (listOf(boss) + monks.values).filter { it.isSlotAssigned }

    fun open(): List<Npc> {
        scope.register("dagon.trio") { awaken() }
        scope.register("dagon.bar") { refreshBar() }
        scope.register("dagon.tome_zamorak") { ctx -> tomeResolve(ctx.npc, DagonhaiArena.GOD_ZAMORAK) }
        scope.register("dagon.tome_saradomin") { ctx ->
            tomeResolve(ctx.npc, DagonhaiArena.GOD_SARADOMIN)
        }
        scope.register("dagon.tome_bandos") { ctx -> tomeResolve(ctx.npc, DagonhaiArena.GOD_BANDOS) }

        val elder = Npc(ELDER, origin)
        elder.respawnDir = Direction.South
        elder.moveRestrict = MoveRestrict.NoMove
        elder.setHunt(0)
        elder.vars[SLOT] = DagonhaiArena.SLOT_ELDER
        deps.npcRepo.add(elder, Int.MAX_VALUE)
        elder.respawns = false
        registry.bind(elder, scope)
        boss = elder
        members += elder

        for (slot in intArrayOf(DagonhaiArena.SLOT_NORTH, DagonhaiArena.SLOT_SOUTH)) {
            val type = if (slot == DagonhaiArena.SLOT_NORTH) MONK_N else MONK_S
            val monk = Npc(type, DagonhaiArena.monkTile(origin, slot))
            monk.respawnDir = if (slot == DagonhaiArena.SLOT_NORTH) Direction.South else Direction.North
            monk.moveRestrict = MoveRestrict.NoMove
            monk.setHunt(0)
            monk.vars[SLOT] = slot
            deps.npcRepo.add(monk, Int.MAX_VALUE)
            monk.respawns = false
            registry.bind(monk, scope)
            monks[slot] = monk
            members += monk
        }

        val focus = Npc(CAULDRON, DagonhaiArena.cauldronHome(origin))
        focus.respawnDir = Direction.South
        focus.moveRestrict = MoveRestrict.NoMove
        focus.setHunt(0)
        deps.npcRepo.add(focus, Int.MAX_VALUE)
        focus.respawns = false
        registry.bind(focus, scope)
        cauldron = focus
        members += focus
        return listOf(elder) + monks.values + focus
    }

    fun dispose() {
        for (npc in members.toList()) {
            if (npc.isSlotAssigned) {
                registry.unbind(npc)
                deps.npcRepo.del(npc, Int.MAX_VALUE)
            }
        }
        members.clear()
    }

    fun ready(player: Player) {
        if (started || !boss.isSlotAssigned) return
        started = true
        startedAt = deps.mapClock.cycle
        tank = player
        val size = session.occupants.size.coerceIn(MIN_SCALING, MAX_SCALING)
        hpMax = TfaDagonhaiScalingRow.getRow("dbrow.tfa_dagonhai_scaling_$size").dagonhai
        trioHp = hpMax
        boss.vars[HP_MAX] = hpMax
        boss.apRangeOverride = DagonhaiArena.REACH
        boss.apRequiresLineOfSight = false
        for (monk in monks.values) {
            monk.hitpoints = hpMax
            monk.apRangeOverride = DagonhaiArena.REACH
            monk.apRequiresLineOfSight = false
        }
        boss.hitpoints = hpMax
        boss.apPlayer2(player, interactions)
        deps.runAbility(boss, player, "begin")
        player.mes("Challenge started: The Dagon'hai.")
        heartbeat()
    }

    private fun awaken() {
        for (monk in monks.values) {
            if (monk.isSlotAssigned) {
                val target = fighters().minByOrNull { it.coords.chebyshevDistance(monk.coords) }
                if (target != null) monk.apPlayer2(target, interactions)
            }
        }
    }

    private fun heartbeat() {
        deps.worldQueues.add(1) {
            if (finished) return@add
            heartbeat()
            keepEngaged()
            tickCauldron()
            tickRitual()
            tickChannel()
            tickRestore()
            tickEmpower()
            tickStuns()
            tickImps()
            checkDeaths()
        }
    }

    private fun keepEngaged() {
        if (finalPhase) return
        val engaged = (boss.interaction as? InteractionPlayer)?.target
        val usable = engaged != null && engaged.isValidTarget() && contains(engaged.coords)
        if (!usable) {
            fighters()
                .minByOrNull { it.coords.chebyshevDistance(boss.coords) }
                ?.let {
                    boss.apPlayer2(it, interactions)
                    if (tank?.isValidTarget() != true || !contains(tank!!.coords)) tank = it
                }
        }
    }

    // ---- shared pool ------------------------------------------------------------------------

    fun modifyTrioHit(event: NpcHitEvents.Modify) {
        val hit = event.hit
        if (stunned.containsKey(event.npc)) {
            hit.damage = hit.damage * (FULL_PERCENT + DagonhaiArena.STUN_BONUS) / FULL_PERCENT
        }
        val source = hit.sourceSlot?.let { deps.playerList[it] }
        if (source != null && source in boosted) {
            hit.damage = hit.damage * (FULL_PERCENT + DagonhaiArena.IMP_BONUS) / FULL_PERCENT
        }
    }

    fun impactTrio(event: NpcHitEvents.Impact) {
        if (finished || finalPhase) return
        val active = channel
        if (active != null && active.npc === event.npc && event.hit.type == HitType.Magic) {
            val spell = event.hit.secondaryType()?.id
            if (spell != null && DagonhaiAbilities.elemOfSpell(spell) == active.elem) {
                interrupt(active.npc)
            }
        }
        if (restoreTimer >= 0 || trioHp <= 0) return
        trioHp = maxOf(0, trioHp - event.hit.damage)
        syncTrioHp()
        updateBar()
        if (trioHp <= 0) shieldBreak()
    }

    private fun syncTrioHp() {
        for (member in trioAll()) member.hitpoints = maxOf(1, trioHp)
    }

    // ---- imps -------------------------------------------------------------------------------

    fun modifyImpHit(event: NpcHitEvents.Modify) {
        hitImpMax(event)
    }

    private fun hitImpMax(event: NpcHitEvents.Modify) {
        event.hit.damage = event.npc.hitpoints
    }

    fun impactImp(event: NpcHitEvents.Impact) {
        val imp = event.npc
        if (finished || imp.hitpoints > 0) return
        val source = event.hit.resolvePlayerSource(deps.playerList) ?: return
        if (boosted.add(source)) {
            source.spotanim("spotanim.vfx_dagonhai_demon_buff_player_spotanim_01", 0, 0, 0)
            source.mes("You feel empowered by the imp's remains.")
        }
    }

    private fun tickImps() {
        val iter = imps.iterator()
        while (iter.hasNext()) {
            val imp = iter.next()
            if (!imp.isSlotAssigned) {
                iter.remove()
                continue
            }
            val engaged = (imp.interaction as? InteractionPlayer)?.target
            if (engaged == null || !engaged.isValidTarget() || !contains(engaged.coords)) {
                fighters()
                    .minByOrNull { it.coords.chebyshevDistance(imp.coords) }
                    ?.let { imp.apPlayer2(it, interactions) }
            }
        }
    }

    // ---- ritual -----------------------------------------------------------------------------

    private fun tickRitual() {
        if (!started || finished || finalPhase || restoreTimer >= 0 || empowerTimer >= 0) return
        if (channel != null || tome != null) return
        ritualTimer++
        if (ritualTimer < DagonhaiArena.RITUAL_EVERY) return
        ritualTimer = 0
        val caster = rotation % ROTATION.size
        rotation++
        when (ROTATION[caster]) {
            DagonhaiArena.SLOT_ELDER -> channelStart(boss)
            DagonhaiArena.SLOT_NORTH -> monkAction(DagonhaiArena.SLOT_NORTH)
            else -> monkAction(DagonhaiArena.SLOT_SOUTH)
        }
    }

    private fun monkAction(slot: Int) {
        val monk = monks[slot]
        if (monk == null || !monk.isSlotAssigned || finished) return
        if (tomeNext) {
            tomeNext = false
            tomeStart(monk)
        } else {
            tomeNext = true
            channelStart(monk)
        }
    }

    private var tomeNext = true

    private fun channelStart(npc: Npc, elem: Int = nextElem) {
        nextElem = (nextElem + 1) % ELEM_COUNT
        npc.vars[ELEM] = elem
        channel = Channel(npc, elem, deps.mapClock.cycle + DagonhaiArena.CHANNEL_TICKS)
        runOn(npc, "channel")
    }

    private fun tomeStart(monk: Npc) {
        val god = nextGod
        nextGod = (nextGod + 1) % GOD_COUNT
        tome = Tome(monk, god)
        runOn(monk, "tome_${DagonhaiAbilities.godName(god)}")
    }

    private fun tickChannel() {
        val active = channel ?: return
        if (deps.mapClock.cycle < active.endTick) return
        channel = null
        if (!active.npc.isSlotAssigned) return
        runOn(active.npc, "channel_end")
        chargeRitual()
    }

    fun interrupt(caster: Npc) {
        if (channel?.npc !== caster) return
        channel = null
        runOn(caster, "channel_stop")
        stunned[caster] = deps.mapClock.cycle + DagonhaiArena.STUN_TICKS
        for (player in fighters()) {
            player.mes("The ${DagonhaiAbilities.elemName(caster.vars[ELEM])} channel is interrupted!")
        }
    }

    private fun tickStuns() {
        val iter = stunned.entries.iterator()
        while (iter.hasNext()) {
            if (deps.mapClock.cycle >= iter.next().value) iter.remove()
        }
    }

    private fun chargeRitual() {
        ritual += DagonhaiArena.RITUAL_STEP
        refreshRitualBar()
        if (ritual >= FULL_PERCENT) empower()
    }

    private fun runOn(npc: Npc, ability: String) {
        if (!npc.isSlotAssigned || npc.hitpoints <= 0) return
        val target =
            fighters().minByOrNull { it.coords.chebyshevDistance(npc.coords) } ?: tank ?: return
        deps.runAbility(npc, target, ability)
    }

    // ---- tomes ------------------------------------------------------------------------------

    private fun tomeResolve(caster: Npc, god: Int) {
        tome = null
        if (finished || finalPhase || !caster.isSlotAssigned) return
        val lane = laneTiles(caster.vars[SLOT])
        val blockers = fighters().filter { it.coords in lane }
        if (blockers.isNotEmpty()) {
            caster.say("Curse you!")
            blockedTome(god, blockers)
        } else {
            cauldronTome(god)
            chargeRitual()
        }
    }

    private fun blockedTome(god: Int, blockers: List<Player>) {
        val blocker = blockers.first()
        when (god) {
            DagonhaiArena.GOD_BANDOS -> {
                deps.worldQueues.add(SHOCK_DELAY) {
                    for (player in fighters()) {
                        if (player === blocker) continue
                        if (player.coords.chebyshevDistance(blocker.coords) <= SHOCK_RANGE) {
                            hitPlayer(
                                boss,
                                player,
                                HitType.Melee,
                                deps.random.of(0..DagonhaiArena.BANDOS_SHOCK_MAX),
                            )
                        }
                    }
                }
            }
            DagonhaiArena.GOD_ZAMORAK -> {
                deps.worldQueues.add(FIRE_DELAY) {
                    for (player in fighters()) {
                        if (player.coords.chebyshevDistance(blocker.coords) <= FIRE_RANGE) {
                            hitPlayer(
                                boss,
                                player,
                                HitType.Magic,
                                deps.random.of(0..DagonhaiArena.ZAMORAK_FIRE_MAX),
                            )
                            burn(player)
                        }
                    }
                }
            }
            else -> {
                blocker.statAdd("stat.hitpoints", SARADOMIN_BLOCK_HEAL, 0)
                blocker.mes("Saradomin's light mends your wounds.")
                saradominLightning(blocker.coords, SARADOMIN_SCATTER)
            }
        }
    }

    private fun hitPlayer(source: Npc, player: Player, type: HitType, damage: Int) {
        if (source.isSlotAssigned) {
            player.queueHit(source, 1, type, damage, deps.playerHitModifier)
        }
    }

    private fun burn(player: Player) {
        deps.worldQueues.add(BURN_DELAY) {
            if (player.isValidTarget()) {
                hitPlayer(boss, player, HitType.Typeless, BURN_DAMAGE)
            }
        }
    }

    private fun cauldronTome(god: Int) {
        val focus = cauldron ?: return
        when (god) {
            DagonhaiArena.GOD_ZAMORAK -> {
                focus.anim("seq.npc_dagonhai_cauldron_attack_01_zamorak", 0)
                focus.spotanim("spotanim.vfx_dagonhai_cauldron_attack_01_zamorak", 0, 0, 0)
                for (player in fighters()) {
                    hitPlayer(
                        boss,
                        player,
                        HitType.Magic,
                        deps.random.of(0..DagonhaiArena.ZAMORAK_OPEN_MAX),
                    )
                    burn(player)
                }
            }
            DagonhaiArena.GOD_BANDOS -> {
                focus.anim("seq.npc_dagonhai_cauldron_attack_03_bandos", 0)
                focus.spotanim("spotanim.vfx_dagonhai_cauldron_attack_03_bandos", 0, 0, 0)
                for (player in fighters()) {
                    hitPlayer(
                        boss,
                        player,
                        HitType.Melee,
                        deps.random.of(0..DagonhaiArena.BANDOS_OPEN_MAX),
                    )
                }
            }
            else -> {
                focus.anim("seq.npc_dagonhai_cauldron_attack_02_saradomin", 0)
                focus.spotanim("spotanim.vfx_dagonhai_cauldron_attack_02_saradomin", 0, 0, 0)
                for (member in trioAll()) {
                    if (member.hitpoints <= 0) continue
                    member.hitpoints =
                        minOf(hpMax, member.hitpoints + DagonhaiArena.SARADOMIN_OPEN_HEAL)
                }
                trioHp = minOf(hpMax, trioHp + DagonhaiArena.SARADOMIN_OPEN_HEAL)
                updateBar()
                saradominLightning(focus.coords, SARADOMIN_SCATTER_OPEN)
            }
        }
    }

    private fun saradominLightning(centre: CoordGrid, count: Int) {
        val tiles = HashSet<CoordGrid>()
        var guard = 0
        while (tiles.size < count && guard++ < ATTEMPTS) {
            val tile =
                centre.translate(
                    deps.random.of(-LIGHTNING_RANGE..LIGHTNING_RANGE),
                    deps.random.of(-LIGHTNING_RANGE..LIGHTNING_RANGE),
                )
            if (contains(tile)) tiles += tile
        }
        for (tile in tiles) {
            val heal = deps.random.of(LIGHTNING_HEAL_CHANCE) == 0
            deps.worldRepo.spotanimMap(
                SpotanimType((if (heal) SARADOMIN_HEAL_MARK else SARADOMIN_BOLT_MARK)
                    .asRSCM(RSCMType.SPOTANIM)),
                tile,
            )
            deps.worldQueues.add(LIGHTNING_DELAY) {
                deps.worldRepo.spotanimMap(
                    SpotanimType((if (heal) SARADOMIN_HEAL_BOLT else SARADOMIN_BOLT)
                        .asRSCM(RSCMType.SPOTANIM)),
                    tile,
                )
                for (player in fighters()) {
                    if (player.coords != tile) continue
                    if (heal) {
                        player.statAdd("stat.hitpoints", SARADOMIN_TILE_HEAL, 0)
                        player.mes("Saradomin's light restores you.")
                    } else {
                        hitPlayer(
                            boss,
                            player,
                            HitType.Magic,
                            deps.random.of(0..DagonhaiArena.SARADOMIN_BOLT_MAX),
                        )
                    }
                }
            }
        }
    }

    // ---- cauldron ---------------------------------------------------------------------------

    private fun tickCauldron() {
        val focus = cauldron ?: return
        if (!started || finished || finalPhase || restoreTimer >= 0) return
        cauldronTimer++
        if (cauldronTimer < DagonhaiArena.CAULDRON_EVERY) return
        cauldronTimer = 0
        var slot = deps.random.of(DagonhaiArena.CAULDRON_SLOTS)
        if (slot == cauldronSlot) slot = (slot + 1) % DagonhaiArena.CAULDRON_SLOTS
        cauldronSlot = slot
        focus.coords = DagonhaiArena.cauldronTile(origin, slot)
        focus.spotanim("spotanim.vfx_dagonhai_cauldron_sizzle", 0, 0, 0)
        shove(focus)
    }

    private fun shove(focus: Npc) {
        for (player in fighters()) {
            if (player.coords.chebyshevDistance(focus.coords) > 1) continue
            hitPlayer(
                focus,
                player,
                HitType.Typeless,
                deps.random.of(0..DagonhaiArena.PUSH_MAX),
            )
            player.coords = freeAdjacent(player) ?: continue
        }
    }

    private fun freeAdjacent(player: Player): CoordGrid? {
        for (dx in -1..1) {
            for (dz in -1..1) {
                if (dx == 0 && dz == 0) continue
                val dest = player.coords.translate(dx, dz)
                if (contains(dest) && members.none { it.isSlotAssigned && it.coords == dest }) {
                    return dest
                }
            }
        }
        return null
    }

    private fun refreshRitualBar() {
        val focus = cauldron ?: return
        val bar = ServerCacheManager.getHealthBar(RITUAL_BAR.asRSCM(RSCMType.HEADBAR)) ?: return
        val fill = bar.segments * ritual / FULL_PERCENT
        focus.showHeadbar(Headbar.fromNoSource(bar.id, bar.id, fill, fill, 0, RITUAL_BAR_CYCLES))
    }

    // ---- shield breaks ------------------------------------------------------------------------

    private fun shieldBreak() {
        breaks++
        if (breaks >= DagonhaiArena.MAX_BREAKS) {
            finalExplosion()
            return
        }
        startRestore()
    }

    private fun startRestore() {
        restoreTimer = DagonhaiArena.RESTORE_TICKS
        impTimer = 0
        for (member in trioAll()) {
            member.vars[ELEM] = -1
            runOn(member, "restore")
            member.transmog(unshielded(member), DagonhaiArena.RESTORE_TICKS + 2)
        }
        val portals = minOf(BASE_PORTALS + (breaks - 1), DagonhaiArena.RIFT_SLOTS)
        for (slot in 0 until portals) spawnRift(slot)
        for (player in fighters()) {
            player.mes("The Dagon'hai begin restoring the ritual's wards!")
        }
    }

    private fun unshielded(npc: Npc): NpcServerType =
        when (npc.vars[SLOT]) {
            DagonhaiArena.SLOT_NORTH -> UNSHIELDED_N
            DagonhaiArena.SLOT_SOUTH -> UNSHIELDED_S
            else -> UNSHIELDED_ELDER
        }.let { ServerCacheManager.getNpc(it.asRSCM(RSCMType.NPC))!! }

    private fun spawnRift(slot: Int) {
        if (finished || rifts.containsKey(slot)) return
        val rift = Npc(RIFT, DagonhaiArena.riftTile(origin, slot))
        rift.respawnDir = Direction.South
        rift.moveRestrict = MoveRestrict.NoMove
        rift.setHunt(0)
        rift.vars[SLOT] = slot
        deps.npcRepo.add(rift, Int.MAX_VALUE)
        rift.respawns = false
        registry.bind(rift, scope)
        rifts[slot] = rift
        members += rift
        val bar = ServerCacheManager.getHealthBar(RITUAL_BAR.asRSCM(RSCMType.HEADBAR))
        if (bar != null) {
            val cycles = DagonhaiArena.RESTORE_TICKS * HEADBAR_CYCLE
            rift.showHeadbar(Headbar.fromNoSource(bar.id, bar.id, 0, bar.segments, 0, cycles))
        }
    }

    private fun tickRestore() {
        if (restoreTimer < 0) return
        drainRifts()
        impTimer++
        if (impTimer >= DagonhaiArena.IMP_EVERY) {
            impTimer = 0
            spawnImps()
        }
        restoreTimer--
        if (restoreTimer <= 0) endRestore()
    }

    private fun spawnImps() {
        for ((slot, rift) in rifts) {
            if (!rift.isSlotAssigned) continue
            val spawned = impsSpawned.getOrDefault(slot, 0)
            if (spawned >= DagonhaiArena.IMPS_PER_RIFT) continue
            impsSpawned[slot] = spawned + 1
            val imp = Npc(IMP, rift.coords)
            imp.respawnDir = Direction.South
            deps.npcRepo.add(imp, Int.MAX_VALUE)
            imp.respawns = false
            registry.bind(imp, scope)
            imps += imp
            members += imp
            imp.spotanim("spotanim.tfa_dagonhai_demonic_strength_telegraph", 0, 0, 0)
            fighters().minByOrNull { it.coords.chebyshevDistance(imp.coords) }?.let {
                imp.apPlayer2(it, interactions)
            }
        }
    }

    private fun drainRifts() {
        for ((_, rift) in rifts) {
            if (!rift.isSlotAssigned) continue
            val drainers = fighters().filter { it.coords.chebyshevDistance(rift.coords) <= 1 }
            if (drainers.isEmpty()) continue
            for (player in drainers) {
                player.statSub("stat.prayer", DagonhaiArena.RIFT_PRAYER_COST, 0)
                rift.hitpoints -= DagonhaiArena.RIFT_DRAIN
            }
            if (rift.hitpoints <= 0) {
                rift.removeHeadbar(RITUAL_BAR_ID)
                rift.spotanim("spotanim.vfx_dagonhai_death_03", 0, 0, 0)
                deps.npcRepo.del(rift, Int.MAX_VALUE)
            }
        }
    }

    private fun endRestore() {
        restoreTimer = -1
        for ((_, rift) in rifts) {
            if (rift.isSlotAssigned) {
                rift.removeHeadbar(RITUAL_BAR_ID)
                deps.npcRepo.del(rift, Int.MAX_VALUE)
            }
        }
        rifts.clear()
        impsSpawned.clear()
        if (finalPhase) return
        trioHp = hpMax
        syncTrioHp()
        ritual = maxOf(0, ritual - DagonhaiArena.RITUAL_SHIELD_LOSS)
        refreshRitualBar()
        for (member in trioAll()) {
            runOn(member, if (member === boss) "settle" else "awaken")
        }
        updateBar()
        for (player in fighters()) {
            player.mes("The ritual's wards are restored!")
        }
    }

    private fun empower() {
        ritual = 0
        refreshRitualBar()
        empowerTimer = DagonhaiArena.EMPOWER_TICKS
        channel = null
        tome = null
        for (member in trioAll()) runOn(member, "empower")
        for (player in fighters()) {
            player.mes("The ritual reaches its peak — the Dagon'hai are empowered!")
        }
    }

    private fun tickEmpower() {
        if (empowerTimer < 0) return
        if (--empowerTimer >= 0) return
        for (member in trioAll()) {
            runOn(member, "settle")
        }
    }

    private fun finalExplosion() {
        finalPhase = true
        channel = null
        tome = null
        val focus = cauldron
        if (focus != null && focus.isSlotAssigned) {
            focus.anim("seq.npc_dagonhai_cauldron_attack_04_guthix_02", 0)
            focus.spotanim("spotanim.vfx_dagonhai_cauldron_dust_explosion", 0, 0, 0)
        }
        val alive = monks.values.filter { it.isSlotAssigned }
        if (alive.isNotEmpty() && boss.isSlotAssigned) {
            val victim = alive[deps.random.of(alive.size)]
            victim.vars[FINAL] = 1
            victim.vars[DONE] = 0
            victim.queueHit(boss, 1, HitType.Typeless, victim.hitpoints + 1, npcHitModifier)
        }
        for (member in trioAll()) {
            if (!member.isSlotAssigned) continue
            member.vars[FINAL] = 1
            member.hitpoints = DagonhaiArena.FINAL_HP
            stunned[member] = deps.mapClock.cycle + DagonhaiArena.STUN_TICKS
            runOn(member, "restore")
        }
        for (player in fighters()) {
            player.mes("The cauldron shatters — the ritual collapses!")
        }
        updateBar()
    }

    private fun checkDeaths() {
        if (!finalPhase || finished) return
        if (trioAll().all { it.hitpoints <= 0 || it.ignoreCombatInteractions }) complete()
    }

    // ---- bar + completion -------------------------------------------------------------------

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
        val hp = if (finalPhase) 0 else if (trioHp <= 0) 0 else trioHp
        hpBar.onUpdate(player, boss, hp, maxOf(1, hpMax))
    }

    fun closeBar() {
        if (barClosed) return
        barClosed = true
        fighters().forEach(hpBar::onDeathClose)
    }

    fun setHealthPercent(percent: Int): Boolean {
        if (!started || !boss.isSlotAssigned || finalPhase) return false
        trioHp = maxOf(1, hpMax * percent / FULL_PERCENT)
        syncTrioHp()
        updateBar()
        return true
    }

    fun setRitual(percent: Int): Boolean {
        if (!started || finalPhase) return false
        ritual = percent.coerceIn(0, FULL_PERCENT)
        refreshRitualBar()
        if (ritual >= FULL_PERCENT) empower()
        return true
    }

    fun forceChannel(elem: Int): Boolean {
        if (!started || channel != null || finalPhase) return false
        channelStart(boss, elem.coerceIn(0, ELEM_COUNT - 1))
        return true
    }

    fun forceBreak(): Boolean {
        if (!started || finalPhase) return false
        trioHp = 0
        syncTrioHp()
        shieldBreak()
        return true
    }

    fun debugState(): List<String> {
        val encounter = deps.encounterRegistry.of(boss)
        return listOf(
            "[debug] started=$started phase=${encounter.currentPhaseName} " +
                "hp=$trioHp/$hpMax breaks=$breaks ritual=$ritual% " +
                "channel=${channel?.npc?.vars?.get(SLOT)} tome=${tome?.god} " +
                "restore=$restoreTimer empower=$empowerTimer final=$finalPhase " +
                "rifts=${rifts.size} imps=${imps.size} stunned=${stunned.size}",
            "[debug] finished=$finished members=${trioAll().map { "hp=${it.hitpoints} retired=${it.ignoreCombatInteractions} final=${it.vars[FINAL]}" }}",
        )
    }

    private fun complete() {
        if (finished) return
        val now = deps.mapClock.cycle
        try {
            if (boss.isSlotAssigned) boss.vars[DONE] = 1
            for (fighter in fighters()) {
                val precise = fighter.vars["varbit.option_precise_timing"] != 0
                val duration = formatInstanceKillTime(now - startedAt, precise)
                fighter.mes(
                    "Challenge complete: The Dagon'hai. Duration: <col=ef1020>$duration</col>"
                )
            }
        } finally {
            endedAt = now
            for (npc in members.toList()) {
                if (npc.isSlotAssigned) deps.npcRepo.del(npc, Int.MAX_VALUE)
            }
            members.clear()
            rifts.clear()
            imps.clear()
            closeBar()
            playVictorySong()
            events.publish(TfaRoomCleared(session.id, "dagonhai"))
        }
    }

    private fun playVictorySong() {
        if (!boss.isSlotAssigned) return
        for (fighter in fighters()) fighter.midiSong(VICTORY_SONG, fadeOutSpeed = VICTORY_FADE_OUT)
        deps.worldQueues.add(1) { playVictorySong() }
    }

    private companion object {
        const val ELDER = "npc.tfa_dagonhai_leader"
        const val MONK_N = "npc.tfa_dagonhai_minion_1"
        const val MONK_S = "npc.tfa_dagonhai_minion_2"
        const val CAULDRON = "npc.tfa_dagonhai_focus"
        const val RIFT = "npc.tfa_dagonhai_rift"
        const val IMP = "npc.tfa_dagonhai_demon"
        const val UNSHIELDED_ELDER = "npc.tfa_dagonhai_leader_unshielded"
        const val UNSHIELDED_N = "npc.tfa_dagonhai_minion_1_unshielded"
        const val UNSHIELDED_S = "npc.tfa_dagonhai_minion_2_unshielded"

        const val RITUAL_BAR = "headbar.standard_charge_100"
        const val RITUAL_BAR_CYCLES = 20
        const val HEADBAR_CYCLE = 30

        const val SARADOMIN_HEAL_MARK = "spotanim.tfa_dagonhai_saradomin_heal_telegraph_150"
        const val SARADOMIN_BOLT_MARK = "spotanim.tfa_dagonhai_saradomin_damage_telegraph_150"
        const val SARADOMIN_HEAL_BOLT = "spotanim.tfa_dagonhai_saradomin_heal_lightning"
        const val SARADOMIN_BOLT = "spotanim.tfa_dagonhai_saradomin_damage_lightning"

        const val MIN_SCALING = 2
        const val MAX_SCALING = 8
        const val FULL_PERCENT = 100
        const val ELEM_COUNT = 4
        const val GOD_COUNT = 3
        const val BASE_PORTALS = 2
        const val SARADOMIN_BLOCK_HEAL = 15
        const val SARADOMIN_TILE_HEAL = 20
        const val SARADOMIN_SCATTER = 5
        const val SARADOMIN_SCATTER_OPEN = 8
        const val LIGHTNING_RANGE = 3
        const val LIGHTNING_HEAL_CHANCE = 3
        const val LIGHTNING_DELAY = 3
        const val ATTEMPTS = 40
        const val SHOCK_DELAY = 2
        const val SHOCK_RANGE = 8
        const val FIRE_DELAY = 2
        const val FIRE_RANGE = 3
        const val BURN_DELAY = 2
        const val BURN_DAMAGE = 3

        const val VICTORY_SONG = 147
        const val VICTORY_FADE_OUT = 50

        val ROTATION =
            intArrayOf(DagonhaiArena.SLOT_ELDER, DagonhaiArena.SLOT_NORTH, DagonhaiArena.SLOT_SOUTH)

        val RITUAL_BAR_ID: Int
            get() = RITUAL_BAR.asRSCM(RSCMType.HEADBAR)
    }
}
