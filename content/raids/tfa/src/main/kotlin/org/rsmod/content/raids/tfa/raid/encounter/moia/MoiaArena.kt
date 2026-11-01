package org.rsmod.content.raids.tfa.raid.encounter.moia

import kotlin.math.abs
import org.rsmod.api.bosses.dsl.area
import org.rsmod.api.bosses.dsl.spawnTile
import org.rsmod.api.bosses.spec.Area
import org.rsmod.api.player.isValidTarget
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.map.CoordGrid

internal object MoiaVarns {
    const val HP_MAX = "varn.moia_hp_max"
    const val SIDE = "varn.moia_side"
    const val TP_CD = "varn.moia_tp_cd"
    const val COUGH_CD = "varn.moia_cough_cd"
    const val ENRAGED = "varn.moia_enraged"
    const val ENRAGE_ROUND = "varn.moia_enrage_round"
    const val DEAD = "varn.moia_dead"
    const val DONE = "varn.moia_done"
    const val SLOT = "varn.moia_slot"
    const val INFECTED = "varn.moia_infected"
}

/** Everything here is relative to Moia's initial hover tile (`origin`), so an instance copy works. */
internal object MoiaArena {
    const val SPAWN_X = 4062
    const val SPAWN_Z = 5656
    const val ENTRY_X = 4073
    const val ENTRY_Z = 5663
    const val REACH = 30

    const val FOOTPRINT = 3
    const val BASE_HP = 1900

    const val RANGED_MAX = 30
    const val MAGIC_MAX = 35
    const val BINDER_MAX = 12
    const val BINDER_REACH = 2
    const val ATTACK_RATE = 5
    const val TELEPORT_EVERY = 4
    const val COUGH_EVERY = 7
    const val SHUFFLE_TICKS = 14
    const val RESPAWN_TICKS = 20
    const val PUSH_DAMAGE = 8

    const val ENRAGE_PERCENT = 33
    const val COUNTDOWN_FIRST = 40
    const val COUNTDOWN_STEP = 5
    const val COUNTDOWN_MIN = 15
    const val ENRAGE_BLOW_MIN = 20
    const val ENRAGE_BLOW_MAX = 30

    const val INFECT_BINDER_DAMAGE = 20
    const val INFECT_PRAYER_DRAIN = 2
    const val INFECT_CYCLES = 12
    const val INFECT_TICKS = 4

    private val INFECT_DAMAGE = intArrayOf(2, 5, 9, 13, 16, 20)

    private const val MAP_SQUARE_MASK = 63

    const val SIDE_SOUTH = 0
    const val SIDE_NORTH = 1
    const val SIDE_WEST = 2
    const val SIDE_EAST = 3

    val AREA: Area = area(spawnTile(-10, -4), spawnTile(13, 16))

    /** 3x3 spawn tiles Moia hovers on, one per side, outside the fence ring. */
    private val HOVER_OFFSETS =
        intArrayOf(
            0,
            0, // south gate
            0,
            14, // north gate
            -6,
            6, // west gate
            7,
            5, // east gate
        )

    /**
     * Pink-tile ring around the arena centre: eight perimeter slots of the 3x3 lattice plus the
     * centre itself. The west perimeter slot starts empty so players have a gap to enter.
     */
    private val BINDER_OFFSETS =
        intArrayOf(
            1,
            9, // N
            3,
            9, // NE
            3,
            7, // E
            3,
            5, // SE
            1,
            5, // S
            -1,
            5, // SW
            -1,
            7, // W  (initial gap)
            -1,
            9, // NW
            1,
            7, // centre
        )

    const val BINDER_SLOTS = 9
    const val WEST_SLOT = 6
    const val CENTRE_SLOT = 8

    private const val ENTRY_DX = 3
    private const val ENTRY_DZ = 7
    private const val CENTRE_DX = 0
    private const val CENTRE_DZ = 6

    fun hoverSpawn(origin: CoordGrid, side: Int): CoordGrid =
        origin.translate(HOVER_OFFSETS[side * 2], HOVER_OFFSETS[side * 2 + 1])

    fun binderTile(origin: CoordGrid, slot: Int): CoordGrid =
        origin.translate(BINDER_OFFSETS[slot * 2], BINDER_OFFSETS[slot * 2 + 1])

    /** Perimeter slots in clockwise order — the shuffle cycle. Centre slot is excluded. */
    val PERIMETER = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7)

    fun centreTile(origin: CoordGrid): CoordGrid = binderTile(origin, CENTRE_SLOT)

    fun enrageSpawn(origin: CoordGrid): CoordGrid = origin.translate(CENTRE_DX, CENTRE_DZ)

    fun arenaEntry(origin: CoordGrid): CoordGrid = origin.translate(ENTRY_DX, ENTRY_DZ)

    fun contains(origin: CoordGrid, tile: CoordGrid): Boolean {
        val sw = origin.translate(-4, 2)
        val ne = origin.translate(7, 11)
        return tile.level == sw.level && tile.x in sw.x..ne.x && tile.z in sw.z..ne.z
    }

    /** The outer courtyard where Moia hovers — wider than the ring but inside the map square. */
    fun inCourtyard(origin: CoordGrid, tile: CoordGrid): Boolean {
        val sw = origin.translate(-10, -4)
        val ne = origin.translate(13, 16)
        return tile.level == sw.level && tile.x in sw.x..ne.x && tile.z in sw.z..ne.z
    }

    fun inRoom(origin: CoordGrid, tile: CoordGrid): Boolean {
        val sw = origin.translate(-(SPAWN_X and MAP_SQUARE_MASK), -(SPAWN_Z and MAP_SQUARE_MASK))
        return tile.level == sw.level &&
            tile.x in sw.x..sw.x + MAP_SQUARE_MASK &&
            tile.z in sw.z..sw.z + MAP_SQUARE_MASK
    }

    fun players(origin: CoordGrid, playerList: PlayerList): List<Player> =
        playerList.filter { it.isValidTarget() && inCourtyard(origin, it.coords) }

    fun footprintDistance(origin: CoordGrid, tile: CoordGrid): Int {
        val dx = tile.x - (origin.x + 1)
        val dz = tile.z - (origin.z + 1)
        return maxOf(abs(dx), abs(dz))
    }

    fun infectDamage(stage: Int): Int = INFECT_DAMAGE[stage.coerceIn(0, INFECT_DAMAGE.lastIndex)]
}
