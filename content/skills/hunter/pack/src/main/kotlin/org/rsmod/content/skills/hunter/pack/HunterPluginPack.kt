package org.rsmod.content.skills.hunter.pack

import dev.openrune.definition.dbtables.DBTable
import dev.openrune.definition.dbtables.DBTableBuilder
import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType
import dev.openrune.pack.PluginPack

class HunterPluginPack : PluginPack() {
    override fun dbTables(): List<DBTable> =
        listOf(birdSnareTable(), boxTrapTable(), deadfallTable(), deadfallLogsTable())
}

object Hunter {
    const val COL_BIRD = 0
    const val COL_LEVEL = 1
    const val COL_XP = 2
    const val COL_FULL_LOC = 3
    const val COL_TRAPPING_LOC = 4
    const val COL_FEATHER = 5
    const val COL_FEATHER_MIN = 6
    const val COL_FEATHER_MAX = 7

    const val BOX_COL_PREY = 0
    const val BOX_COL_LEVEL = 1
    const val BOX_COL_XP = 2
    const val BOX_COL_FULL_LOC = 3
    const val BOX_COL_TRAPPING_N = 4
    const val BOX_COL_TRAPPING_S = 5
    const val BOX_COL_TRAPPING_E = 6
    const val BOX_COL_TRAPPING_W = 7
    const val BOX_COL_CAUGHT = 8

    const val DEAD_COL_PREY = 0
    const val DEAD_COL_LEVEL = 1
    const val DEAD_COL_XP = 2
    const val DEAD_COL_FULL_LOC = 3
    const val DEAD_COL_TRAPPING = 4
    const val DEAD_COL_TRAPPING_M = 5
    const val DEAD_COL_PART = 6
    const val DEAD_COL_MEAT = 7

    const val DEAD_LOG_COL = 0
}

private fun DBTableBuilder.birdSnareColumns() {
    column("bird", Hunter.COL_BIRD, VarType.NPC)
    column("level", Hunter.COL_LEVEL, VarType.INT)
    column("xp", Hunter.COL_XP, VarType.INT)
    column("full_loc", Hunter.COL_FULL_LOC, VarType.LOC)
    column("trapping_loc", Hunter.COL_TRAPPING_LOC, VarType.LOC)
    column("feather", Hunter.COL_FEATHER, VarType.OBJ)
    column("feather_min", Hunter.COL_FEATHER_MIN, VarType.INT)
    column("feather_max", Hunter.COL_FEATHER_MAX, VarType.INT)
}

private fun birdSnareTable(): DBTable =
    dbTable("dbtable.hunter_bird_snares", serverOnly = true) {
        birdSnareColumns()
        birdSnareRows()
    }

private fun DBTableBuilder.boxTrapColumns() {
    column("prey", Hunter.BOX_COL_PREY, VarType.NPC)
    column("level", Hunter.BOX_COL_LEVEL, VarType.INT)
    column("xp", Hunter.BOX_COL_XP, VarType.INT)
    column("full_loc", Hunter.BOX_COL_FULL_LOC, VarType.LOC)
    column("trapping_n", Hunter.BOX_COL_TRAPPING_N, VarType.LOC)
    column("trapping_s", Hunter.BOX_COL_TRAPPING_S, VarType.LOC)
    column("trapping_e", Hunter.BOX_COL_TRAPPING_E, VarType.LOC)
    column("trapping_w", Hunter.BOX_COL_TRAPPING_W, VarType.LOC)
    column("caught", Hunter.BOX_COL_CAUGHT, VarType.OBJ)
}

private fun boxTrapTable(): DBTable =
    dbTable("dbtable.hunter_box_traps", serverOnly = true) {
        boxTrapColumns()
        boxTrapRows()
    }

private fun DBTableBuilder.deadfallColumns() {
    column("prey", Hunter.DEAD_COL_PREY, VarType.NPC)
    column("level", Hunter.DEAD_COL_LEVEL, VarType.INT)
    column("xp", Hunter.DEAD_COL_XP, VarType.INT)
    column("full_loc", Hunter.DEAD_COL_FULL_LOC, VarType.LOC)
    column("trapping", Hunter.DEAD_COL_TRAPPING, VarType.LOC)
    column("trapping_m", Hunter.DEAD_COL_TRAPPING_M, VarType.LOC)
    column("part", Hunter.DEAD_COL_PART, VarType.OBJ)
    column("meat", Hunter.DEAD_COL_MEAT, VarType.OBJ)
}

private fun deadfallTable(): DBTable =
    dbTable("dbtable.hunter_deadfall", serverOnly = true) {
        deadfallColumns()
        deadfallRows()
    }

private fun deadfallLogsTable(): DBTable =
    dbTable("dbtable.hunter_deadfall_logs", serverOnly = true) {
        column("log", Hunter.DEAD_LOG_COL, VarType.OBJ)
        deadfallLogRows()
    }
