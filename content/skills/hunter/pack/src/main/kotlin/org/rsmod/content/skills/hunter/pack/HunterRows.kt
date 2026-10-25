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
