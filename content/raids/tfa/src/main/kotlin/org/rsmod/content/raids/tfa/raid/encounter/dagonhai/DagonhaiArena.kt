package org.rsmod.content.raids.tfa.raid.encounter.dagonhai

import org.rsmod.api.bosses.dsl.area
import org.rsmod.api.bosses.dsl.spawnTile
import org.rsmod.api.bosses.spec.Area
import org.rsmod.api.player.isValidTarget
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.map.CoordGrid

internal object DagonhaiVarns {
    const val HP_MAX = "varn.dagon_hp_max"
    const val DEAD = "varn.dagon_dead"
    const val DONE = "varn.dagon_done"
    const val FINAL = "varn.dagon_final"
    const val ELEM = "varn.dagon_elem"
    const val SLOT = "varn.dagon_slot"
    const val PHASE = "varn.dagon_phase"
}

/**
 * The Dagon'hai ritual chamber: a walled room whose north and south walls only cover the west
 * alcove, leaving open arms that connect to the monks' desk halls. Coordinates are relative to the
 * Elder's spawn (`origin`), so an instance copy works.
 */
internal object DagonhaiArena {
    const val SPAWN_X = 4069
    const val SPAWN_Z = 5599
    const val ENTRY_X = 4079
    const val ENTRY_Z = 5599
    const val REACH = 30

    const val BASE_HP = 1800

    const val RANGED_MAX = 28
    const val MAGIC_MAX = 32
    const val IMP_MAX = 12
    const val PUSH_MAX = 9
    const val BANDOS_BLOCK_MAX = 55
    const val BANDOS_SHOCK_MAX = 25
    const val BANDOS_OPEN_MAX = 35
    const val ZAMORAK_BLOCK_MAX = 25
    const val ZAMORAK_FIRE_MAX = 15
    const val ZAMORAK_OPEN_MAX = 30
    const val SARADOMIN_BOLT_MAX = 20
    const val SARADOMIN_OPEN_HEAL = 50
    const val BARRAGE_MAX = 25

    const val ATTACK_CYCLE = 6
    const val CHANNEL_TICKS = 9
    const val RITUAL_EVERY = 22
    const val RITUAL_STEP = 25
    const val RITUAL_SHIELD_LOSS = 20
    const val EMPOWER_TICKS = 12

    const val STUN_TICKS = 6
    const val STUN_BONUS = 15
    const val IMP_BONUS = 25
    const val CAULDRON_EVERY = 18
    const val RESTORE_TICKS = 25
    const val IMP_EVERY = 8
    const val IMPS_PER_RIFT = 3
    const val RIFT_HP = 40
    const val RIFT_DRAIN = 8
    const val RIFT_PRAYER_COST = 2
    const val MAX_BREAKS = 5
    const val FINAL_HP = 7

    const val ELEM_FIRE = 0
    const val ELEM_WATER = 1
    const val ELEM_AIR = 2
    const val ELEM_EARTH = 3

    const val GOD_ZAMORAK = 0
    const val GOD_SARADOMIN = 1
    const val GOD_BANDOS = 2

    const val SLOT_ELDER = 0
    const val SLOT_NORTH = 1
    const val SLOT_SOUTH = 2

    const val PHASE_COMBAT = 0
    const val PHASE_RITUAL = 1
    const val PHASE_RESTORE = 2
    const val PHASE_EMPOWERED = 3
    const val PHASE_FINAL = 4

    private const val MAP_SQUARE_MASK = 63

    val AREA: Area = area(spawnTile(-21, -18), spawnTile(11, 18))

    private const val CAULDRON_DX = -4
    private const val CAULDRON_DZ = 0

    private const val MONK_DX = -4
    private const val MONK_N_DZ = 8
    private const val MONK_S_DZ = -8

    private const val ENTRY_DX = 8
    private const val ENTRY_DZ = 0

    /** Tiles of the scorch patch the cauldron relocates between. */
    private val CAULDRON_OFFSETS =
        intArrayOf(
            -5, -1,
            -3, -1,
            -6, 0,
            -2, 0,
            -5, 1,
            -3, 1,
        )

    /** Rift spawn tiles: west alcove, south arm, north arm. */
    private val RIFT_OFFSETS =
        intArrayOf(
            -13, 0,
            -4, -5,
            -4, 5,
        )

    const val RIFT_SLOTS = 3

    fun cauldronHome(origin: CoordGrid): CoordGrid = origin.translate(CAULDRON_DX, CAULDRON_DZ)

    fun cauldronTile(origin: CoordGrid, slot: Int): CoordGrid =
        origin.translate(CAULDRON_OFFSETS[slot * 2], CAULDRON_OFFSETS[slot * 2 + 1])

    val CAULDRON_SLOTS: Int
        get() = CAULDRON_OFFSETS.size / 2

    fun monkTile(origin: CoordGrid, slot: Int): CoordGrid =
        origin.translate(MONK_DX, if (slot == SLOT_NORTH) MONK_N_DZ else MONK_S_DZ)

    fun riftTile(origin: CoordGrid, slot: Int): CoordGrid =
        origin.translate(RIFT_OFFSETS[slot * 2], RIFT_OFFSETS[slot * 2 + 1])

    fun arenaEntry(origin: CoordGrid): CoordGrid = origin.translate(ENTRY_DX, ENTRY_DZ)

    /** Straight lane the monk's tome travels along toward the cauldron (excludes both ends). */
    fun tomeLane(origin: CoordGrid, slot: Int): List<CoordGrid> {
        val monk = monkTile(origin, slot)
        val cauldron = cauldronHome(origin)
        val step = if (cauldron.z < monk.z) -1 else 1
        return buildList {
            var z = monk.z + step
            while (z != cauldron.z) {
                add(CoordGrid(monk.x, z, monk.level))
                z += step
            }
        }
    }

    fun inRoom(origin: CoordGrid, tile: CoordGrid): Boolean {
        val sw = origin.translate(-(SPAWN_X and MAP_SQUARE_MASK), -(SPAWN_Z and MAP_SQUARE_MASK))
        return tile.level == sw.level &&
            tile.x in sw.x..sw.x + MAP_SQUARE_MASK &&
            tile.z in sw.z..sw.z + MAP_SQUARE_MASK
    }

    fun inFightArea(origin: CoordGrid, tile: CoordGrid): Boolean {
        val sw = origin.translate(-21, -18)
        val ne = origin.translate(11, 18)
        return tile.level == sw.level && tile.x in sw.x..ne.x && tile.z in sw.z..ne.z
    }

    fun players(origin: CoordGrid, playerList: PlayerList): List<Player> =
        playerList.filter { it.isValidTarget() && inFightArea(origin, it.coords) }

    fun laneDirection(slot: Int): String = if (slot == SLOT_NORTH) "south" else "north"
}
