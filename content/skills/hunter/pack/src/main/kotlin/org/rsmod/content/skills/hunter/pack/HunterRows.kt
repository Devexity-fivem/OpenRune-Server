package org.rsmod.content.skills.hunter.pack

import dev.openrune.definition.dbtables.DBTableBuilder

// xp values are in tenths (34 xp = 340).
internal fun DBTableBuilder.birdSnareRows() {
    birdSnareRow("crimson_swift", "npc.hunting_bird_jungle", 1, 340, "jungle", "obj.hunting_jungle_feather")
    birdSnareRow("golden_warbler", "npc.hunting_bird_desert", 5, 470, "desert", "obj.hunting_desert_feather")
    birdSnareRow("copper_longtail", "npc.hunting_bird_woodland", 9, 613, "woodland", "obj.hunting_woodland_feather")
    birdSnareRow("cerulean_twitch", "npc.hunting_bird_polar", 11, 646, "polar", "obj.hunting_polar_feather")
    birdSnareRow("tropical_wagtail", "npc.multicoloured_bird", 19, 950, "coloured", "obj.hunting_stripy_bird_feather")
}

private fun DBTableBuilder.birdSnareRow(
    name: String,
    bird: String,
    level: Int,
    xp: Int,
    locSuffix: String,
    feather: String,
    featherMin: Int = 5,
    featherMax: Int = 10,
) {
    row("dbrow.hunter_bird_$name") {
        columnRSCM(Hunter.COL_BIRD, bird)
        column(Hunter.COL_LEVEL, level)
        column(Hunter.COL_XP, xp)
        columnRSCM(Hunter.COL_FULL_LOC, "loc.hunting_ojibway_trap_full_$locSuffix")
        columnRSCM(Hunter.COL_TRAPPING_LOC, "loc.hunting_ojibway_trap_trapping_$locSuffix")
        columnRSCM(Hunter.COL_FEATHER, feather)
        column(Hunter.COL_FEATHER_MIN, featherMin)
        column(Hunter.COL_FEATHER_MAX, featherMax)
    }
}

// xp values are in tenths (115 xp = 1150). Trapping locs are directional: the
// suffix picks the side the prey entered the box from.
internal fun DBTableBuilder.boxTrapRows() {
    boxTrapRow("ferret", "npc.hunting_ferret", 27, 1150, "ferret", "obj.hunting_ferret")
    boxTrapRow(
        "jerboa",
        "npc.varlamore_hunterjerboa01",
        39,
        1370,
        "jerboa",
        "obj.hunting_jerboa_tail",
    )
    boxTrapRow("chinchompa", "npc.hunting_chinchompa", 53, 1984, "chinchompa", "obj.chinchompa_captured")
    boxTrapRow(
        "chinchompa_big",
        "npc.hunting_chinchompa_big",
        63,
        2650,
        "chinchompa_big",
        "obj.chinchompa_big_captured",
    )
    boxTrapRow(
        "chinchompa_black",
        "npc.hunting_chinchompa_black",
        73,
        3150,
        "chinchompa_black",
        "obj.chinchompa_black",
    )
}

private fun DBTableBuilder.boxTrapRow(
    name: String,
    prey: String,
    level: Int,
    xp: Int,
    locStem: String,
    caught: String,
) {
    row("dbrow.hunter_box_$name") {
        columnRSCM(Hunter.BOX_COL_PREY, prey)
        column(Hunter.BOX_COL_LEVEL, level)
        column(Hunter.BOX_COL_XP, xp)
        columnRSCM(Hunter.BOX_COL_FULL_LOC, "loc.hunting_boxtrap_full_$locStem")
        columnRSCM(Hunter.BOX_COL_TRAPPING_N, "loc.hunting_boxtrap_trapping_${locStem}_n")
        columnRSCM(Hunter.BOX_COL_TRAPPING_S, "loc.hunting_boxtrap_trapping_${locStem}_s")
        columnRSCM(Hunter.BOX_COL_TRAPPING_E, "loc.hunting_boxtrap_trapping_${locStem}_e")
        columnRSCM(Hunter.BOX_COL_TRAPPING_W, "loc.hunting_boxtrap_trapping_${locStem}_w")
        columnRSCM(Hunter.BOX_COL_CAUGHT, caught)
    }
}

// xp values are in tenths (102.4 xp = 1024). Every catch also drops bones; `meat`
// is omitted for prey that does not drop a raw carcass. `trapping_m` is the
// mirrored loc used when the prey enters from the boulder's back side.
internal fun DBTableBuilder.deadfallRows() {
    deadfallRow(
        "wild",
        "npc.huntingbeast_claws",
        23,
        1024,
        "claw",
        "obj.huntingbeast_claws",
        "obj.huntingbeast_wild_meat",
    )
    deadfallRow(
        "barbed",
        "npc.huntingbeast_barbedtail",
        33,
        1344,
        "barbed",
        "obj.hunting_barbed_harpoon",
        "obj.huntingbeast_barbed_meat",
    )
    deadfallRow("spike", "npc.huntingbeast_spiky", 37, 1472, "spike", "obj.huntingbeast_spike", null)
    deadfallRow(
        "sabre",
        "npc.huntingbeast_sabreteeth",
        51,
        1600,
        "sabre",
        "obj.huntingbeast_sabreteeth",
        null,
    )
    deadfallRow(
        "fennec",
        "npc.varlamore_hunterfox01",
        57,
        1776,
        "fennec",
        "obj.hunting_fennecfox_fur",
        "obj.hunting_fennecfox_meat",
    )
}

private fun DBTableBuilder.deadfallRow(
    name: String,
    prey: String,
    level: Int,
    xp: Int,
    locStem: String,
    part: String,
    meat: String?,
) {
    row("dbrow.hunter_deadfall_$name") {
        columnRSCM(Hunter.DEAD_COL_PREY, prey)
        column(Hunter.DEAD_COL_LEVEL, level)
        column(Hunter.DEAD_COL_XP, xp)
        columnRSCM(Hunter.DEAD_COL_FULL_LOC, "loc.hunting_deadfall_full_$locStem")
        columnRSCM(Hunter.DEAD_COL_TRAPPING, "loc.hunting_deadfall_trapping_$locStem")
        columnRSCM(Hunter.DEAD_COL_TRAPPING_M, "loc.hunting_deadfall_trapping_${locStem}_m")
        columnRSCM(Hunter.DEAD_COL_PART, part)
        meat?.let { columnRSCM(Hunter.DEAD_COL_MEAT, it) }
    }
}

// Ordered lowest-tier first: the cheapest valid log in the inventory is used.
// Redwood and arctic pine logs are not valid deadfall props.
internal fun DBTableBuilder.deadfallLogRows() {
    for (log in DEADFALL_LOGS) {
        row("dbrow.hunter_deadfall_log_$log") { columnRSCM(Hunter.DEAD_LOG_COL, "obj.$log") }
    }
}

// xp values are in tenths (152 xp = 1520). The tree and trap locs are per-biome:
// `tree` is the static young tree, `armed` the strung net, `catching`/`failing` the
// transition states and `failed` the collapsed trap. Swamp mixes stems: its
// catching/full locs use `_green` while the rest use `_swamp`.
internal fun DBTableBuilder.netTrapRows() {
    netTrapRow(
        "swamp_lizard",
        "npc.salamander_green",
        29,
        1520,
        "swamp",
        "green",
        "obj.salamander_tar_green",
        "obj.green_salamander",
    )
    netTrapRow(
        "orange_salamander",
        "npc.salamander_orange",
        47,
        2240,
        "orange",
        "orange",
        "obj.salamander_tar_orange",
        "obj.orange_salamander",
    )
    netTrapRow(
        "red_salamander",
        "npc.salamander_red",
        59,
        2720,
        "red",
        "red",
        "obj.salamander_tar_red",
        "obj.red_salamander",
    )
    netTrapRow(
        "black_salamander",
        "npc.salamander_black",
        67,
        3192,
        "black",
        "black",
        "obj.salamander_tar_black",
        "obj.black_salamander",
    )
    netTrapRow(
        "tecu_salamander",
        "npc.salamander_mountain",
        79,
        3440,
        "mountain",
        "mountain",
        "obj.salamander_tar_mountain",
        "obj.mountain_salamander",
    )
}

private fun DBTableBuilder.netTrapRow(
    name: String,
    prey: String,
    level: Int,
    xp: Int,
    suffix: String,
    catchSuffix: String,
    bait: String,
    caught: String,
) {
    row("dbrow.hunter_net_$name") {
        columnRSCM(Hunter.NET_COL_PREY, prey)
        column(Hunter.NET_COL_LEVEL, level)
        column(Hunter.NET_COL_XP, xp)
        columnRSCM(Hunter.NET_COL_TREE, "loc.hunting_sapling_up_$suffix")
        columnRSCM(Hunter.NET_COL_ARMED, "loc.hunting_sapling_net_set_$suffix")
        columnRSCM(Hunter.NET_COL_CATCHING, "loc.hunting_sapling_catching_$catchSuffix")
        columnRSCM(Hunter.NET_COL_FULL, "loc.hunting_sapling_full_$catchSuffix")
        columnRSCM(Hunter.NET_COL_FAILING, "loc.hunting_sapling_failing_$suffix")
        columnRSCM(Hunter.NET_COL_FAILED, "loc.hunting_sapling_failed_$suffix")
        columnRSCM(Hunter.NET_COL_BAIT, bait)
        columnRSCM(Hunter.NET_COL_CAUGHT, caught)
    }
}

private val DEADFALL_LOGS =
    listOf(
        "logs",
        "oak_logs",
        "achey_tree_logs",
        "willow_logs",
        "teak_logs",
        "juniper_logs",
        "maple_logs",
        "mahogany_logs",
        "camphor_logs",
        "ironwood_logs",
        "yew_logs",
        "magic_logs",
        "rosewood_logs",
    )
