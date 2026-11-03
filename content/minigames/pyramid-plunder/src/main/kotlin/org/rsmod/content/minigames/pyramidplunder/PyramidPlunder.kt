package org.rsmod.content.minigames.pyramidplunder

import jakarta.inject.Inject
import kotlin.math.abs
import org.rsmod.api.mechanics.toxins.impl.PlayerPoison
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.thievingLvl
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerTimer
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.timerAt
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class PyramidPlunder
@Inject
constructor(
    private val eventBus: EventBus,
    private val npcRepo: NpcRepository,
    private val random: GameRandom,
    private val worldClock: MapClock,
    private val protectedAccess: ProtectedAccessLauncher,
) : PluginScript() {

    override fun ScriptContext.startup() {
        ENTRY_DOORS.forEachIndexed { idx, loc ->
            onOpLoc1(loc) { enterPyramid(idx) }
        }
        onOpNpc3(MUMMY_GUARDIAN) { startRun() }
        onOpLoc1(TOMB_DOOR_CLOSED) { pickDoor(it) }
        onOpLoc1(TOMB_DOOR_OPEN) { enterDoor(it) }
        URN_CLOSED.forEach { loc -> onOpLoc1(loc) { searchUrn(it.loc.id) } }
        URN_CLOSED.forEach { loc -> onOpLoc2(loc) { checkSnakes(it.loc.id) } }
        URN_SNAKE.forEach { loc -> onOpLoc1(loc) { searchSnake() } }
        URN_SNAKE.forEach { loc -> onOpLoc3(loc) { charmSnake(it.loc.id) } }
        URN_CHARMED.forEach { loc -> onOpLoc1(loc) { searchCharmed(it.loc.id) } }
        URN_OPEN.forEach { loc -> onOpLoc1(loc) { mes("You've already searched this urn.") } }
        onOpLoc1(CHEST_CLOSED) { searchChest() }
        onOpLoc1(SARCOPHAGUS_CLOSED) { openSarcophagus() }
        onOpLoc1(SARCOPHAGUS_OPEN) { searchSarcophagus() }
        onOpLoc1(SPEARTRAP) { passTrap() }
        onOpLoc1(TOMB_EXIT) { leaveTomb() }
        onOpLoc2(TOMB_EXIT) { leaveTomb() }
        onOpLoc1(ANTECHAMBER_EXIT) { leaveTomb() }
        onPlayerTimer(PP_TIMER) { sessionTick() }
        onPlayerLogin { ejectStaleSession(player) }
    }

    private fun ejectStaleSession(player: Player) {
        if (player.vars[VB_ROOM] == 0) {
            return
        }
        protectedAccess.launch(player) {
            if (!inComplex(player.coords)) {
                clearSession()
            }
        }
    }

    private suspend fun ProtectedAccess.enterPyramid(door: Int) {
        if (player.thievingLvl < REQUIRED_LEVEL) {
            mes("You need a Thieving level of $REQUIRED_LEVEL to enter the pyramid.")
            return
        }
        vars[OUTSIDE_DOOR_VARS[door]] = DOOR_ANIM
        anim(SEQ_PYRAMID_DOOR)
        delay(2)
        vars[OUTSIDE_DOOR_VARS[door]] = DOOR_CLOSED
        if (door == correctEntryDoor()) {
            teleport(LOBBY)
        } else {
            teleport(ANTECHAMBER)
        }
    }

    private fun ProtectedAccess.correctEntryDoor(): Int {
        var v = vars[VP_DOORS]
        val rot = (v shr ENTRY_ROTATE_SHIFT) and ROTATE_MASK
        val cycle = worldClock.cycle and ROTATE_MASK
        if (rot == 0 || (cycle - rot) and ROTATE_MASK >= ROTATE_TICKS) {
            v = (v and ENTRY_KEEP.inv()) or
                (random.of(0, 3) shl ENTRY_CORRECT_SHIFT) or
                (cycle shl ENTRY_ROTATE_SHIFT)
            vars[VP_DOORS] = v
        }
        return (v shr ENTRY_CORRECT_SHIFT) and 3
    }

    private suspend fun ProtectedAccess.startRun() {
        if (player.thievingLvl < REQUIRED_LEVEL) {
            mes("The Guardian Mummy will not let you in. You need a Thieving level of $REQUIRED_LEVEL.")
            return
        }
        if (vars[VB_ROOM] != 0) {
            return
        }
        vars[VB_PLAYED_BEFORE] = 1
        anim(SEQ_TELE)
        spotanim(SPOT_TELE)
        delay(1)
        vars[VB_TIMER] = SESSION_TICKS
        ifOpenOverlay(OVERLAY, OVERLAY_TARGET)
        enterRoom(1)
        player.timerAt(PP_TIMER, worldClock.cycle, 1)
    }

    private fun ProtectedAccess.enterRoom(room: Int) {
        val def = ROOMS[room - 1]
        vars[VB_ROOM] = room
        vars[VB_REQUIRED] = def.req
        vars[VB_ROOM_LEVEL] = room
        vars[VB_RIGHT_DOOR] = 0
        vars[VB_TRAP] = 0
        vars[VB_CHEST] = 0
        vars[VB_SARC] = 0
        vars[VB_SARC_PUSH] = 0
        for (i in 1..4) vars[DOOR_VARS[i - 1]] = 0
        for (i in 1..15) vars["varbit.ntk_urn${i}_state"] = 0
        vars[VP_DOORS] =
            (vars[VP_DOORS] and ROOM_CLEAR_MASK.inv()) or
                (random.of(0, 3) shl ROOM_CORRECT_SHIFT)
        teleport(def.corridor)
    }

    private suspend fun ProtectedAccess.sessionTick() {
        val left = vars[VB_TIMER] - 1
        if (left <= 0 || vars[VB_ROOM] == 0 || !inComplex(player.coords)) {
            expire()
            return
        }
        vars[VB_TIMER] = left
        player.timerAt(PP_TIMER, worldClock.cycle, 1)
    }

    private suspend fun ProtectedAccess.expire() {
        anim(SEQ_TELE)
        spotanim(SPOT_TELE)
        delay(1)
        clearSession()
        teleport(LOBBY)
        mes("Your time in the pyramid is up, and you are thrown back to the Guardian Mummy.")
    }

    private suspend fun ProtectedAccess.pickDoor(event: org.rsmod.api.player.events.interact.LocEvents.Op1) {
        val slot = DOOR_SLOTS[event.loc.id] ?: return
        val room = vars[VB_ROOM]
        if (room == 0) {
            mes("You fail to pick the lock.")
            return
        }
        val doorVar = vars[DOOR_VARS[slot]]
        if (doorVar != 0) {
            return
        }
        anim(SEQ_DOOR_PICK)
        delay(3)
        val lockpick = inv.count(LOCKPICK) > 0
        val chance = DOOR_BASE + player.thievingLvl - room * 4 + if (lockpick) DOOR_LOCKPICK else 0
        if (random.of(0, 199) >= chance) {
            mes("You fail to pick the lock.")
            return
        }
        anim(SEQ_TOMB_OPEN)
        vars[DOOR_VARS[slot]] = DOOR_ANIM
        val correct = (vars[VP_DOORS] shr ROOM_CORRECT_SHIFT) and 3
        if (slot == correct) {
            vars[VB_RIGHT_DOOR] = 1
        } else {
            vars[VP_DOORS] = vars[VP_DOORS] or (1 shl (TRIED_SHIFT + slot))
        }
        val xp = if (lockpick) ROOMS[room - 1].doorXp / 2 else ROOMS[room - 1].doorXp
        statAdvance("stat.thieving", xp)
        mes("You manage to pick the lock.")
    }

    private suspend fun ProtectedAccess.enterDoor(event: org.rsmod.api.player.events.interact.LocEvents.Op1) {
        val slot = DOOR_SLOTS[event.loc.id] ?: return
        val room = vars[VB_ROOM]
        if (room == 0 || vars[DOOR_VARS[slot]] == 0) {
            return
        }
        val correct = (vars[VP_DOORS] shr ROOM_CORRECT_SHIFT) and 3
        val tried = Integer.bitCount((vars[VP_DOORS] shr TRIED_SHIFT) and 0xF)
        if (slot != correct && tried < 3) {
            mes("The door leads nowhere.")
            return
        }
        if (room >= 8) {
            finishRoom()
            return
        }
        enterRoom(room + 1)
    }

    private suspend fun ProtectedAccess.finishRoom() {
        clearSession()
        teleport(LOBBY)
        mes("You reach the deepest chamber and make your way back to the Guardian Mummy.")
    }

    private suspend fun ProtectedAccess.searchUrn(baseId: Int) {
        val slot = URN_SLOTS[baseId] ?: return
        anim(SEQ_URN)
        delay(1)
        if (!urnSuccess()) {
            snakeBite()
            return
        }
        lootUrn(slot, charmed = false)
    }

    private suspend fun ProtectedAccess.checkSnakes(baseId: Int) {
        val slot = URN_SLOTS[baseId] ?: return
        anim(SEQ_URN)
        delay(1)
        if (urnSuccess()) {
            vars[URN_VARS[slot - 1]] = URN_SNAKE_STATE
            mes("You find a snake. It stares at you menacingly.")
        } else {
            mes("There don't seem to be any snakes in this urn.")
        }
    }

    private fun ProtectedAccess.urnSuccess(): Boolean {
        val room = vars[VB_ROOM].coerceAtLeast(1)
        val chance = URN_BASE + player.thievingLvl - (room - 1) * URN_ROOM_STEP
        return random.of(0, 255) < chance
    }

    private suspend fun ProtectedAccess.searchSnake() {
        anim(SEQ_URN)
        delay(1)
        snakeBite()
    }

    private suspend fun ProtectedAccess.snakeBite() {
        val bite = random.of(1, 4)
        takeInstantHit(HitType.Typeless, bite)
        PlayerPoison.tryPoison(player, bite)
        mes("A snake bites you!")
    }

    private suspend fun ProtectedAccess.charmSnake(baseId: Int) {
        val slot = URN_SLOTS[baseId] ?: return
        if (inv.count(SNAKE_FLUTE) == 0) {
            mes("You need a snake charm to charm this snake.")
            return
        }
        anim(SEQ_CHARM)
        delay(1)
        vars[URN_VARS[slot - 1]] = URN_CHARMED_STATE
        mes("You charm the snake into a deep doze.")
    }

    private suspend fun ProtectedAccess.searchCharmed(baseId: Int) {
        val slot = URN_SLOTS[baseId] ?: return
        anim(SEQ_URN)
        delay(1)
        lootUrn(slot, charmed = true)
    }

    private suspend fun ProtectedAccess.lootUrn(slot: Int, charmed: Boolean) {
        val room = vars[VB_ROOM]
        vars[URN_VARS[slot - 1]] = URN_OPEN_STATE
        if (room == 0) {
            return
        }
        val xp = ROOMS[room - 1].urnXp * if (charmed) 2 / 3.0 else 1.0
        statAdvance("stat.thieving", xp)
        rollLoot(room)
    }

    private suspend fun ProtectedAccess.searchChest() {
        val room = vars[VB_ROOM]
        if (room == 0) {
            return
        }
        anim(SEQ_TOMB_OPEN)
        delay(1)
        vars[VB_CHEST] = 1
        rollSceptre()
        rollLoot(room)
        if (random.of(0, 255) < (CHEST_TRAP_MAX - player.thievingLvl / 3).coerceAtLeast(4)) {
            takeInstantHit(HitType.Typeless, random.of(1, 4))
            spawnSwarm()
            mes("You set off a trap!")
        } else {
            statAdvance("stat.thieving", ROOMS[room - 1].chestXp)
        }
    }

    private suspend fun ProtectedAccess.openSarcophagus() {
        anim(SEQ_SARC_READY)
        delay(1)
        anim(SEQ_SARC_OPEN)
        vars[VB_SARC_PUSH] = 1
        vars[VB_SARC] = 2
        delay(3)
        vars[VB_SARC_PUSH] = 0
        vars[VB_SARC] = 1
        statAdvance("stat.strength", ROOMS[(vars[VB_ROOM] - 1).coerceAtLeast(0)].strXp)
        if (random.of(0, 15) < vars[VB_ROOM]) {
            spawnMummy()
            mes("A mummy bursts out of the sarcophagus!")
        }
    }

    private suspend fun ProtectedAccess.searchSarcophagus() {
        val room = vars[VB_ROOM]
        if (room == 0) {
            return
        }
        if (vars[VP_DOORS] and SARC_LOOTED_BIT != 0) {
            mes("You've already searched this sarcophagus.")
            return
        }
        anim(SEQ_SARC_SEARCH)
        delay(2)
        vars[VP_DOORS] = vars[VP_DOORS] or SARC_LOOTED_BIT
        rollSceptre()
        rollLoot(room)
    }

    private suspend fun ProtectedAccess.passTrap() {
        val room = roomByCorridor(player.coords) ?: return
        vars[VB_TRAP] = 1
        delay(2)
        val nearCorridor = dist(player.coords, room.corridor) < dist(player.coords, room.inside)
        if (random.of(0, 4) == 0) {
            takeInstantHit(HitType.Typeless, random.of(1, 4))
            mes("You fail to pass the traps and take damage!")
            vars[VB_TRAP] = 0
            return
        }
        teleport(if (nearCorridor) room.inside else room.corridor)
        vars[VB_TRAP] = 0
        statAdvance("stat.thieving", TRAP_XP)
    }

    private suspend fun ProtectedAccess.leaveTomb() {
        clearSession()
        anim(SEQ_TELE)
        spotanim(SPOT_TELE)
        delay(1)
        teleport(EXIT_NORTH)
        mes("You find your way back out of the pyramid.")
    }

    private fun ProtectedAccess.clearSession() {
        vars[VB_ROOM] = 0
        vars[VB_TIMER] = 0
        vars[VB_REQUIRED] = 0
        vars[VB_ROOM_LEVEL] = 0
        vars[VB_RIGHT_DOOR] = 0
        vars[VB_TRAP] = 0
        vars[VB_CHEST] = 0
        vars[VB_SARC] = 0
        vars[VB_SARC_PUSH] = 0
        for (i in 1..4) vars[DOOR_VARS[i - 1]] = 0
        for (i in 1..15) vars["varbit.ntk_urn${i}_state"] = 0
        for (i in 0..3) vars[OUTSIDE_DOOR_VARS[i]] = 0
        vars[VP_DOORS] = vars[VP_DOORS] and ENTRY_KEEP
        player.clearTimer(PP_TIMER)
        player.ifCloseOverlay(OVERLAY, eventBus)
    }

    private suspend fun ProtectedAccess.rollSceptre() {
        if (random.of(0, 999) == 0) {
            invAdd(inv, "obj.ntk_jewelled_sceptre_3")
            mes("<col=ff0000>You have found a Pharaoh's Sceptre!</col>")
            clearSession()
            teleport(EXIT_NORTH)
            mes("You are ejected from the pyramid.")
        }
    }

    private suspend fun ProtectedAccess.rollLoot(room: Int) {
        val idx = (room - 1 + random.of(-2, 2)).coerceIn(0, LOOT.lastIndex)
        invAdd(inv, LOOT[idx])
    }

    private fun ProtectedAccess.spawnSwarm() {
        val npc = Npc(SCARAB_SWARM, player.tileNear())
        npc.respawns = false
        npcRepo.add(npc, SPAWN_TICKS)
        npc.assignSpawnOwner(player, worldClock.cycle)
    }

    private fun ProtectedAccess.spawnMummy() {
        val type = MUMMY_TYPES[random.of(0, MUMMY_TYPES.lastIndex)]
        val npc = Npc(type, player.tileNear())
        npc.respawns = false
        npcRepo.add(npc, SPAWN_TICKS)
        npc.assignSpawnOwner(player, worldClock.cycle)
    }

    private fun Player.tileNear(): CoordGrid {
        repeat(8) {
            val dx = random.of(-2, 2)
            val dz = random.of(-2, 2)
            if (dx != 0 || dz != 0) {
                return coords.translate(dx, dz)
            }
        }
        return coords
    }

    private fun roomByCorridor(coords: CoordGrid): Room? =
        ROOMS.firstOrNull {
            coords.level == 0 && dist(coords, it.corridor) <= 4
        }

    private fun dist(a: CoordGrid, b: CoordGrid): Int =
        abs(a.x - b.x) + abs(a.z - b.z)

    private fun inComplex(coords: CoordGrid): Boolean =
        coords.level == 0 &&
            coords.x in COMPLEX_X0..COMPLEX_X1 &&
            coords.z in COMPLEX_Z0..COMPLEX_Z1

    private data class Room(
        val req: Int,
        val corridor: CoordGrid,
        val inside: CoordGrid,
        val urnXp: Double,
        val chestXp: Double,
        val doorXp: Double,
        val strXp: Double,
    )

    private companion object {
        const val REQUIRED_LEVEL = 21
        const val SESSION_TICKS = 500
        const val SPAWN_TICKS = 3000
        const val TRAP_XP = 10.0
        const val ROTATE_TICKS = 2500
        const val URN_BASE = 128
        const val URN_ROOM_STEP = 6
        const val DOOR_BASE = 40
        const val DOOR_LOCKPICK = 30
        const val CHEST_TRAP_MAX = 48

        const val DOOR_CLOSED = 0
        const val DOOR_ANIM = 1
        const val URN_OPEN_STATE = 1
        const val URN_SNAKE_STATE = 2
        const val URN_CHARMED_STATE = 3

        const val OVERLAY = "interface.ntk_overlay"
        const val OVERLAY_TARGET = "component.toplevel_osrs_stretch:overlay_hud"
        const val PP_TIMER = "timer.pp_timer"
        const val MUMMY_GUARDIAN = "npc.ntk_mummy_guardian"
        const val SCARAB_SWARM = "npc.ntk_scarab_swarm"
        const val SNAKE_FLUTE = "obj.snake_flute"
        const val LOCKPICK = "obj.lockpick"

        const val SEQ_TELE = "seq.ntk_tele_spotanim"
        const val SEQ_DOOR_PICK = "seq.ntk_tomb_door"
        const val SEQ_TOMB_OPEN = "seq.ntk_tomb_open"
        const val SEQ_PYRAMID_DOOR = "seq.ntk_pyramid_door"
        const val SEQ_URN = "seq.ntk_urn_anim"
        const val SEQ_CHARM = "seq.charming_loop"
        const val SEQ_SARC_OPEN = "seq.ntk_sarcophagi_anim"
        const val SEQ_SARC_READY = "seq.ntk_sarcophagi_anim_ready"
        const val SEQ_SARC_SEARCH = "seq.ntk_tomb_search"
        const val SPOT_TELE = "spotanim.ntk_tele"

        const val TOMB_DOOR_CLOSED = "loc.ntk_tomb_door_noanim"
        const val TOMB_DOOR_OPEN = "loc.ntk_tomb_door_anim"
        const val TOMB_EXIT = "loc.ntk_tomb_door_exit"
        const val ANTECHAMBER_EXIT = "loc.ntk_antechamber_exit"
        const val SPEARTRAP = "loc.ntk_speartrap_inmotion"
        const val CHEST_CLOSED = "loc.ntk_golden_chest_closed"
        const val SARCOPHAGUS_CLOSED = "loc.ntk_sarcophagus"
        const val SARCOPHAGUS_OPEN = "loc.ntk_sarcophagus_open"

        const val VB_ROOM = "varbit.ntk_room_number"
        const val VB_TIMER = "varbit.ntk_player_timer_count"
        const val VB_REQUIRED = "varbit.ntk_thieving_required"
        const val VB_ROOM_LEVEL = "varbit.ntk_current_room_level"
        const val VB_RIGHT_DOOR = "varbit.ntk_right_door_opened"
        const val VB_PLAYED_BEFORE = "varbit.ntk_played_before"
        const val VB_TRAP = "varbit.ntk_trap_active"
        const val VB_CHEST = "varbit.ntk_golden_chest_state"
        const val VB_SARC = "varbit.ntk_sarcophagus_state"
        const val VB_SARC_PUSH = "varbit.ntk_sarcophagus_push"
        const val VP_DOORS = "varp.pp_doors"

        const val ENTRY_CORRECT_SHIFT = 0
        const val ROOM_CORRECT_SHIFT = 2
        const val TRIED_SHIFT = 4
        const val SARC_LOOTED_BIT = 0x100
        const val ENTRY_ROTATE_SHIFT = 11
        const val ROTATE_MASK = 0xFFFFF
        const val ROOM_DOOR_MASK = 0xC
        const val TRIED_MASK = 0xF0
        const val ROOM_CLEAR_MASK = ROOM_DOOR_MASK or TRIED_MASK or SARC_LOOTED_BIT
        const val ENTRY_KEEP = 0x3 or (ROTATE_MASK shl ENTRY_ROTATE_SHIFT)

        val URN_VARS = Array(15) { "varbit.ntk_urn${it + 1}_state" }
        val DOOR_VARS = Array(4) { "varbit.ntk_door${it + 1}_state" }
        val OUTSIDE_DOOR_VARS = Array(4) { "varbit.ntk_outside_door${it + 1}_state" }

        val URN_SLOTS =
            mapOf(
                26580 to 1, 26600 to 2, 26601 to 3, 26602 to 4, 26603 to 5,
                26604 to 6, 26605 to 7, 26606 to 8, 26607 to 9, 26608 to 10,
                26609 to 11, 26610 to 12, 26611 to 13, 26612 to 14, 26613 to 15,
            )

        val DOOR_SLOTS = mapOf(26617 to 0, 26618 to 0, 26619 to 1, 26620 to 2, 26621 to 3)

        val URN_CLOSED =
            listOf(
                "loc.ntk_urn1_closed",
                "loc.ntk_urn2_closed",
                "loc.ntk_urn3_closed",
                "loc.ntk_urn_rough_closed",
            )
        val URN_OPEN =
            listOf(
                "loc.ntk_urn1_open",
                "loc.ntk_urn2_open",
                "loc.ntk_urn3_open",
                "loc.ntk_urn_rough_open",
            )
        val URN_SNAKE =
            listOf(
                "loc.ntk_urn1_snake",
                "loc.ntk_urn2_snake",
                "loc.ntk_urn3_snake",
                "loc.ntk_urn_rough_snake",
            )
        val URN_CHARMED =
            listOf(
                "loc.ntk_urn1_snake_charmed",
                "loc.ntk_urn2_snake_charmed",
                "loc.ntk_urn3_snake_charmed",
                "loc.ntk_urn_rough_snake_charmed",
            )

        val MUMMY_TYPES =
            listOf(
                "npc.ntk_mummy_1",
                "npc.ntk_mummy_2",
                "npc.ntk_mummy_3",
                "npc.ntk_mummy_4",
                "npc.ntk_mummy_5",
            )

        val LOOT =
            listOf(
                "obj.ntk_scarab_pottery",
                "obj.ntk_statuette_pottery",
                "obj.ntk_seal_stone",
                "obj.ntk_scarab_stone",
                "obj.ntk_statuette_stone",
                "obj.ntk_ivory_comb",
                "obj.ntk_seal_gold",
                "obj.ntk_scarab_gold",
                "obj.ntk_statuette_gold",
            )

        val ENTRY_DOORS =
            listOf(
                "loc.ntk_pyramid_door_north_noanim",
                "loc.ntk_pyramid_door_east_noanim",
                "loc.ntk_pyramid_door_south_noanim",
                "loc.ntk_pyramid_door_west_noanim",
            )

        val LOBBY = CoordGrid(1931, 4427, 3)
        val ANTECHAMBER = CoordGrid(1930, 4428, 2)
        val EXIT_NORTH = CoordGrid(3279, 2807, 0)

        const val COMPLEX_X0 = 1918
        const val COMPLEX_X1 = 1982
        const val COMPLEX_Z0 = 4418
        const val COMPLEX_Z1 = 4482

        val ROOMS =
            listOf(
                Room(21, CoordGrid(1926, 4425, 0), CoordGrid(1926, 4430, 0), 60.0, 40.0, 40.0, 20.0),
                Room(31, CoordGrid(1944, 4422, 0), CoordGrid(1944, 4427, 0), 90.0, 60.0, 60.0, 30.0),
                Room(41, CoordGrid(1974, 4421, 0), CoordGrid(1974, 4426, 0), 150.0, 100.0, 100.0, 50.0),
                Room(51, CoordGrid(1977, 4466, 0), CoordGrid(1973, 4466, 0), 215.0, 140.0, 140.0, 70.0),
                Room(61, CoordGrid(1954, 4475, 0), CoordGrid(1954, 4471, 0), 300.0, 200.0, 200.0, 100.0),
                Room(71, CoordGrid(1927, 4475, 0), CoordGrid(1927, 4470, 0), 450.0, 300.0, 300.0, 150.0),
                Room(81, CoordGrid(1929, 4452, 0), CoordGrid(1933, 4452, 0), 675.0, 450.0, 450.0, 225.0),
                Room(91, CoordGrid(1963, 4444, 0), CoordGrid(1959, 4444, 0), 825.0, 550.0, 0.0, 275.0),
            )
    }
}
