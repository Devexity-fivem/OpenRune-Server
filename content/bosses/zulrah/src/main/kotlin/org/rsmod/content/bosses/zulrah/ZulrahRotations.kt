package org.rsmod.content.bosses.zulrah

import org.rsmod.map.CoordGrid

object ZulrahRotations {

    enum class Pos(val tile: CoordGrid) {
        MIDDLE(CoordGrid(2266, 3073, 0)),
        SOUTH(CoordGrid(2266, 3062, 0)),
        WEST(CoordGrid(2256, 3071, 0)),
        EAST(CoordGrid(2276, 3071, 0)),
    }

    enum class Form(val npc: String) {
        GREEN("npc.snakeboss_boss_ranged"),
        CRIMSON("npc.snakeboss_boss_melee"),
        TANZANITE("npc.snakeboss_boss_magic"),
    }

    enum class Style {
        RANGED,
        MAGIC,
        TANZANITE,
    }

    sealed interface Act {
        data class Attack(val style: Style, val count: Int) : Act

        data class Jad(val start: Style, val count: Int) : Act

        data class TailSlam(val count: Int) : Act

        data class Orbs(val count: Int) : Act

        data class Clouds(val count: Int) : Act
    }

    data class Phase(val pos: Pos, val form: Form, val acts: List<Act>)

    private fun alternating(first: Act, second: Act, pairs: Int): List<Act> =
        (0 until pairs).flatMap { listOf(first, second) }

    private val CRIMSON_A =
        listOf(
            Phase(Pos.MIDDLE, Form.GREEN, listOf(Act.Clouds(4))),
            Phase(Pos.MIDDLE, Form.CRIMSON, listOf(Act.TailSlam(2))),
            Phase(Pos.MIDDLE, Form.TANZANITE, listOf(Act.Attack(Style.TANZANITE, 4))),
            Phase(
                Pos.SOUTH,
                Form.GREEN,
                listOf(Act.Attack(Style.RANGED, 5), Act.Orbs(2), Act.Clouds(2), Act.Orbs(2)),
            ),
            Phase(Pos.MIDDLE, Form.CRIMSON, listOf(Act.TailSlam(2))),
            Phase(Pos.WEST, Form.TANZANITE, listOf(Act.Attack(Style.TANZANITE, 5))),
            Phase(Pos.SOUTH, Form.GREEN, listOf(Act.Clouds(3), Act.Orbs(4))),
            Phase(
                Pos.SOUTH,
                Form.TANZANITE,
                listOf(Act.Attack(Style.TANZANITE, 5)) +
                    alternating(Act.Orbs(1), Act.Clouds(1), 5),
            ),
            Phase(
                Pos.WEST,
                Form.GREEN,
                listOf(Act.Jad(Style.RANGED, 10), Act.Clouds(4)),
            ),
            Phase(Pos.MIDDLE, Form.CRIMSON, listOf(Act.TailSlam(2))),
            Phase(Pos.MIDDLE, Form.GREEN, listOf(Act.Attack(Style.RANGED, 5), Act.Clouds(4))),
        )

    private val CRIMSON_B =
        listOf(
            Phase(Pos.MIDDLE, Form.GREEN, listOf(Act.Clouds(4))),
            Phase(Pos.MIDDLE, Form.CRIMSON, listOf(Act.TailSlam(2))),
            Phase(Pos.MIDDLE, Form.TANZANITE, listOf(Act.Attack(Style.TANZANITE, 4))),
            Phase(Pos.WEST, Form.GREEN, listOf(Act.Clouds(3), Act.Orbs(4))),
            Phase(
                Pos.SOUTH,
                Form.TANZANITE,
                listOf(Act.Attack(Style.TANZANITE, 5), Act.Orbs(2), Act.Clouds(2), Act.Orbs(2)),
            ),
            Phase(Pos.MIDDLE, Form.CRIMSON, listOf(Act.TailSlam(2))),
            Phase(Pos.EAST, Form.GREEN, listOf(Act.Attack(Style.RANGED, 5))),
            Phase(
                Pos.SOUTH,
                Form.TANZANITE,
                listOf(Act.Attack(Style.TANZANITE, 5)) +
                    alternating(Act.Orbs(1), Act.Clouds(1), 5),
            ),
            Phase(
                Pos.WEST,
                Form.GREEN,
                listOf(Act.Jad(Style.RANGED, 10), Act.Clouds(4)),
            ),
            Phase(Pos.MIDDLE, Form.CRIMSON, listOf(Act.TailSlam(2))),
            Phase(Pos.MIDDLE, Form.GREEN, listOf(Act.Attack(Style.RANGED, 5), Act.Clouds(4))),
        )

    private val SERP =
        listOf(
            Phase(Pos.MIDDLE, Form.GREEN, listOf(Act.Clouds(4))),
            Phase(Pos.EAST, Form.GREEN, listOf(Act.Attack(Style.RANGED, 5), Act.Orbs(3))),
            Phase(
                Pos.MIDDLE,
                Form.CRIMSON,
                alternating(Act.Clouds(1), Act.Orbs(1), 6) + Act.TailSlam(2),
            ),
            Phase(Pos.WEST, Form.TANZANITE, listOf(Act.Attack(Style.TANZANITE, 5))),
            Phase(Pos.SOUTH, Form.GREEN, listOf(Act.Attack(Style.RANGED, 5))),
            Phase(Pos.EAST, Form.TANZANITE, listOf(Act.Attack(Style.TANZANITE, 5))),
            Phase(Pos.MIDDLE, Form.GREEN, listOf(Act.Clouds(3), Act.Orbs(3))),
            Phase(Pos.WEST, Form.GREEN, listOf(Act.Attack(Style.RANGED, 5))),
            Phase(
                Pos.MIDDLE,
                Form.TANZANITE,
                listOf(Act.Attack(Style.TANZANITE, 5), Act.Clouds(2), Act.Orbs(3)),
            ),
            Phase(Pos.EAST, Form.GREEN, listOf(Act.Jad(Style.MAGIC, 10))),
            Phase(Pos.MIDDLE, Form.TANZANITE, listOf(Act.Orbs(4))),
            Phase(Pos.MIDDLE, Form.GREEN, listOf(Act.Attack(Style.RANGED, 5), Act.Clouds(4))),
        )

    private val TANZ =
        listOf(
            Phase(Pos.MIDDLE, Form.GREEN, listOf(Act.Clouds(4))),
            Phase(
                Pos.EAST,
                Form.TANZANITE,
                listOf(Act.Orbs(4), Act.Attack(Style.TANZANITE, 6)),
            ),
            Phase(Pos.SOUTH, Form.GREEN, listOf(Act.Attack(Style.RANGED, 4), Act.Clouds(2))),
            Phase(
                Pos.WEST,
                Form.TANZANITE,
                listOf(Act.Orbs(4), Act.Attack(Style.TANZANITE, 4)),
            ),
            Phase(Pos.MIDDLE, Form.CRIMSON, listOf(Act.TailSlam(2), Act.Clouds(2))),
            Phase(Pos.EAST, Form.GREEN, listOf(Act.Attack(Style.RANGED, 4))),
            Phase(Pos.SOUTH, Form.GREEN, listOf(Act.Orbs(6), Act.Clouds(3))),
            Phase(
                Pos.WEST,
                Form.TANZANITE,
                listOf(Act.Attack(Style.TANZANITE, 5), Act.Orbs(4)),
            ),
            Phase(Pos.MIDDLE, Form.GREEN, listOf(Act.Attack(Style.RANGED, 4))),
            Phase(
                Pos.MIDDLE,
                Form.TANZANITE,
                listOf(Act.Attack(Style.TANZANITE, 4), Act.Clouds(3)),
            ),
            Phase(Pos.EAST, Form.GREEN, listOf(Act.Jad(Style.MAGIC, 8))),
            Phase(Pos.MIDDLE, Form.TANZANITE, listOf(Act.Orbs(4))),
            Phase(Pos.MIDDLE, Form.GREEN, listOf(Act.Attack(Style.RANGED, 5), Act.Clouds(4))),
        )

    // Venom clouds land on fixed tiles ringing the bay's inner walkway, leaving
    // the island's east and west tips clear. Captured from real gas-projectile
    // target points relative to the opening spawn.
    val CLOUD_RING: List<CoordGrid> =
        listOf(
            CoordGrid(2265, 3068, 0),
            CoordGrid(2262, 3069, 0),
            CoordGrid(2262, 3072, 0),
            CoordGrid(2262, 3075, 0),
            CoordGrid(2268, 3068, 0),
            CoordGrid(2271, 3068, 0),
            CoordGrid(2272, 3071, 0),
            CoordGrid(2272, 3074, 0),
            CoordGrid(2272, 3077, 0),
        )

    val ROTATIONS: List<List<Phase>> = listOf(CRIMSON_A, CRIMSON_B, SERP, TANZ)
}
