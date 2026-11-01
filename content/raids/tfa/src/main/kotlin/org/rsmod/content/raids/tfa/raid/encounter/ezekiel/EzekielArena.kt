package org.rsmod.content.raids.tfa.raid.encounter.ezekiel

import kotlin.math.abs
import org.rsmod.api.bosses.dsl.area
import org.rsmod.api.bosses.dsl.spawnTile
import org.rsmod.api.bosses.spec.Area
import org.rsmod.api.player.isValidTarget
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.map.CoordGrid

internal object EzekielVarns {
    const val HP_MAX = "varn.ezekiel_hp_max"
    const val MELEE = "varn.ezekiel_melee"
    const val CHARGE = "varn.ezekiel_charge"
    const val STUNNED = "varn.ezekiel_stunned"
    const val PATTERN = "varn.ezekiel_pattern"
    const val DEAD = "varn.ezekiel_dead"
    const val DONE = "varn.ezekiel_done"
}

/** Everything here is relative to Ezekiel's spawn tile (`origin`), so an instance copy works. */
internal object EzekielArena {
    const val ENTRY_X = 4072
    const val ENTRY_Z = 5535
    const val SPAWN_X = 4062
    const val SPAWN_Z = 5534
    const val REACH = 30

    const val FOOTPRINT = 3
    const val ARENA_SW_DX = -7
    const val ARENA_SW_DZ = -8
    const val ARENA_NE_DX = 9
    const val ARENA_NE_DZ = 8

    const val MELEE_MAX = 30
    const val MAGIC_MAX = 24
    const val LIGHTNING_MIN = 15
    const val LIGHTNING_MAX = 30
    const val SWORD_MIN = 10
    const val SWORD_MAX = 30
    const val CLEAVE_REACH = 2

    const val SWORD_SLOTS = 8
    const val SWORD_HIT_DAMAGE = 25
    const val SWORD_HEAL = 100
    const val SWORD_CHARGE_TICKS = 14
    const val SWORD_STUN_TICKS = 8
    const val SWORD_RANGED_RESIST = 60
    const val ENRAGE_CYCLE = 11

    const val ENRAGE_PERCENT = 35

    private const val MAP_SQUARE_MASK = 63

    val AREA: Area = area(spawnTile(ARENA_SW_DX, ARENA_SW_DZ), spawnTile(ARENA_NE_DX, ARENA_NE_DZ))

    /** The eight fence slots the animated swords launch from, clockwise from the south-west. */
    private val SWORD_OFFSETS =
        arrayOf(
            -7 to -8, // sw corner
            -7 to 0, // west
            -7 to 8, // nw corner
            0 to 8, // north
            9 to 8, // ne corner
            9 to 0, // east
            9 to -8, // se corner
            0 to -8, // south
        )

    fun inRoom(origin: CoordGrid, tile: CoordGrid): Boolean {
        val swX = origin.x - (SPAWN_X and MAP_SQUARE_MASK)
        val swZ = origin.z - (SPAWN_Z and MAP_SQUARE_MASK)
        return tile.level == origin.level &&
            tile.x in swX..swX + MAP_SQUARE_MASK &&
            tile.z in swZ..swZ + MAP_SQUARE_MASK
    }

    fun inArena(origin: CoordGrid, tile: CoordGrid): Boolean {
        val dx = tile.x - origin.x
        val dz = tile.z - origin.z
        return tile.level == origin.level &&
            dx in ARENA_SW_DX..ARENA_NE_DX &&
            dz in ARENA_SW_DZ..ARENA_NE_DZ
    }

    fun players(origin: CoordGrid, playerList: PlayerList): List<Player> =
        playerList.filter { it.isValidTarget() && inArena(origin, it.coords) }

    fun inFootprint(origin: CoordGrid, tile: CoordGrid): Boolean =
        tile.level == origin.level &&
            tile.x in origin.x until origin.x + FOOTPRINT &&
            tile.z in origin.z until origin.z + FOOTPRINT

    fun footprintTiles(origin: CoordGrid): List<CoordGrid> =
        buildList {
            for (dx in 0 until FOOTPRINT) for (dz in 0 until FOOTPRINT) add(origin.translate(dx, dz))
        }

    fun footprintDistance(origin: CoordGrid, tile: CoordGrid): Int {
        val dx = maxOf(origin.x - tile.x, 0, tile.x - (origin.x + FOOTPRINT - 1))
        val dz = maxOf(origin.z - tile.z, 0, tile.z - (origin.z + FOOTPRINT - 1))
        return maxOf(dx, dz)
    }

    fun arenaTiles(origin: CoordGrid): List<CoordGrid> =
        buildList {
            for (dx in ARENA_SW_DX..ARENA_NE_DX) {
                for (dz in ARENA_SW_DZ..ARENA_NE_DZ) add(origin.translate(dx, dz))
            }
        }

    /**
     * The cleave spares the tiles directly opposite [tank] on the far side of the footprint. Every
     * other tile within [CLEAVE_REACH] is hit.
     */
    fun cleaveTiles(origin: CoordGrid, tank: CoordGrid): List<CoordGrid> {
        val cx = origin.x + FOOTPRINT / 2
        val cz = origin.z + FOOTPRINT / 2
        val sideX = (tank.x - cx).coerceIn(-1, 1)
        val sideZ = (tank.z - cz).coerceIn(-1, 1)
        return arenaTiles(origin).filter { tile ->
            val dist = footprintDistance(origin, tile)
            if (dist == 0 || dist > CLEAVE_REACH) return@filter false
            val behind =
                sideX != 0 && (tile.x - cx).coerceIn(-1, 1) == -sideX ||
                    sideZ != 0 && (tile.z - cz).coerceIn(-1, 1) == -sideZ
            !behind
        }
    }

    /** The plus-shaped blast: full rows and columns through the footprint, footprint excluded. */
    fun crossTiles(origin: CoordGrid): List<CoordGrid> =
        arenaTiles(origin).filter { tile ->
            !inFootprint(origin, tile) &&
                (tile.z in origin.z until origin.z + FOOTPRINT ||
                    tile.x in origin.x until origin.x + FOOTPRINT)
        }

    fun swordTile(origin: CoordGrid, slot: Int): CoordGrid {
        val (dx, dz) = SWORD_OFFSETS[slot]
        return origin.translate(dx, dz)
    }

    /**
     * The straight lane a sword at [slot] carves through the arena: the line from its tile through
     * the footprint's middle, extended to the far wall. The footprint itself is part of the lane so
     * the launch resolution can test whether Ezekiel took the blade.
     */
    fun laneTiles(origin: CoordGrid, slot: Int): List<CoordGrid> {
        val start = swordTile(origin, slot)
        val cx = origin.x + FOOTPRINT / 2
        val cz = origin.z + FOOTPRINT / 2
        val dx = cx - start.x
        val dz = cz - start.z
        val lane = ArrayList<CoordGrid>()
        var tile = start
        var guard = 0
        while (inRoom(origin, tile) && guard++ < 32) {
            lane += tile
            tile =
                if (dx == 0 && dz == 0) {
                    break
                } else if (abs(dx) >= abs(dz)) {
                    tile.translate(dx.compareTo(0), if (abs(dz) * 2 > abs(dx)) dz.compareTo(0) else 0)
                } else {
                    tile.translate(if (abs(dx) * 2 > abs(dz)) dx.compareTo(0) else 0, dz.compareTo(0))
                }
        }
        return lane.filter { inArena(origin, it) || inFootprint(origin, it) }
    }

    /** Enrage safe-side pockets: left, front and right of the boss as he faces the centre. */
    val ENRAGE_LANES = intArrayOf(1, 3, 5)

    fun enrageLane(origin: CoordGrid, index: Int): List<CoordGrid> =
        laneTiles(origin, ENRAGE_LANES[index])

    fun centreTile(origin: CoordGrid): CoordGrid =
        origin.translate(FOOTPRINT / 2, FOOTPRINT / 2)
}
