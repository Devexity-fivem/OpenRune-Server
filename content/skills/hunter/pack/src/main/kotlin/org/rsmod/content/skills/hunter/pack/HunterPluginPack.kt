package org.rsmod.content.skills.hunter.pack

import dev.openrune.definition.dbtables.DBTable
import dev.openrune.definition.dbtables.DBTableBuilder
import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType
import dev.openrune.pack.PluginPack

class HunterPluginPack : PluginPack() {
    override fun dbTables(): List<DBTable> = listOf(birdSnareTable())
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
