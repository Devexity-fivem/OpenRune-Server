package org.rsmod.content.raids.tfa.raid.encounter.ocellus

import kotlin.math.abs
import kotlin.math.sign
import org.rsmod.api.bosses.dsl.area
import org.rsmod.api.bosses.dsl.spawnTile
import org.rsmod.api.bosses.spec.Area
import org.rsmod.api.bosses.spec.TargetExpr
import org.rsmod.api.player.isValidTarget
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.map.CoordGrid

/** The Ocellus room, relative to the boss's spawn (the intro tile). */
internal object OcellusArena {
    val CORNERS: List<Pair<Int, Int>> = listOf(6 to 9, -6 to 9, -6 to -3, 6 to -3)

    private const val CENTRE_DX = 0
    private const val CENTRE_DZ = 3
    private const val MIDDLE_DX = 1
    private const val MIDDLE_DZ = 4
    private const val SW_DX = -10
    private const val SW_DZ = -7
    private const val NE_DX = 12
    private const val NE_DZ = 15
    private const val FLOOR_MIN = 2
    private const val FLOOR_MAX = 12
    private const val FLOOR_REACH = 16

    /** Where the boss stands in the middle of the room (its south-west tile). */
    val CENTRE: TargetExpr.Single = spawnTile(CENTRE_DX, CENTRE_DZ)

    /** The room's middle tile, which the boss faces from the corners. */
    val MIDDLE: TargetExpr.Single = spawnTile(MIDDLE_DX, MIDDLE_DZ)

    val AREA: Area = area(spawnTile(SW_DX, SW_DZ), spawnTile(NE_DX, NE_DZ))

    /** Every tile of phase 3 floor, out to the arena's edge. */
    val FLOOR: Area =
        area(
            spawnTile(MIDDLE_DX - FLOOR_MAX, MIDDLE_DZ - FLOOR_MAX),
            spawnTile(MIDDLE_DX + FLOOR_MAX, MIDDLE_DZ + FLOOR_MAX),
        )

    fun centre(npc: Npc): CoordGrid = npc.spawnCoords.translate(CENTRE_DX, CENTRE_DZ)

    /** Whether [tile] is phase 3 floor on the same side of the gap as the boss. */
    fun sameQuadrantFloor(npc: Npc, tile: CoordGrid): Boolean {
        val middle = npc.spawnCoords.translate(MIDDLE_DX, MIDDLE_DZ)
        val boss = npc.coords.translate(npc.size / 2, npc.size / 2)
        val dx = tile.x - middle.x
        val dz = tile.z - middle.z
        return abs(dx) in FLOOR_MIN..FLOOR_MAX &&
            abs(dz) in FLOOR_MIN..FLOOR_MAX &&
            abs(dx) + abs(dz) <= FLOOR_REACH &&
            !(abs(dx) == FLOOR_MIN && abs(dz) == FLOOR_MIN) &&
            dx.sign == (boss.x - middle.x).sign &&
            dz.sign == (boss.z - middle.z).sign
    }

    fun players(npc: Npc, playerList: PlayerList): List<Player> {
        val sw = npc.spawnCoords.translate(SW_DX, SW_DZ)
        val ne = npc.spawnCoords.translate(NE_DX, NE_DZ)
        return playerList.filter {
            it.isValidTarget() &&
                it.coords.level == sw.level &&
                it.coords.x in sw.x..ne.x &&
                it.coords.z in sw.z..ne.z
        }
    }
}

internal object OcellusVarns {
    const val ARRIVED_AT = "varn.ocellus_arrived_at"
    const val VOLLEY_STYLE = "varn.ocellus_volley_style"
    const val CORNER = "varn.ocellus_corner"
    const val CENTRE_SIDE = "varn.ocellus_centre_side"
    const val THRESHOLD = "varn.ocellus_threshold"

    /** Written to [THRESHOLD] during phase 2 by `::ocellus p3` to jump straight to phase 3. */
    const val SKIP_PHASE_TWO = 99
    const val ACTIVE_TENDRILS = "varn.ocellus_active_tendrils"
    const val METER = "varn.ocellus_meter"
    const val BARRAGE_WAVES = "varn.ocellus_barrage_waves"}
