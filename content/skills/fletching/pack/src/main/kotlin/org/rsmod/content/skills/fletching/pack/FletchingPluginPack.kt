package org.rsmod.content.skills.fletching.pack

import dev.openrune.definition.dbtables.DBTable
import dev.openrune.definition.dbtables.DBTableBuilder
import dev.openrune.definition.util.VarType
import dev.openrune.pack.PluginPack
import dev.openrune.tables.production.productionTable

class FletchingPluginPack : PluginPack() {
    override fun dbTables(): List<DBTable> = listOf(fletchingTable())
}

object Fletching {
    const val COL_TOOL = 7
    const val COL_ANIM = 8
    const val COL_TICKS = 9
    const val COL_FIRST_TICKS = 10
    const val COL_KIND = 11
    const val COL_FEATHER_INPUT = 12

    const val KIND_CARVE = "carve"
    const val KIND_STRING = "string"
    const val KIND_ASSEMBLE = "assemble"
    const val KIND_ATTACH = "attach"
    const val KIND_CHISEL = "chisel"
}

private fun DBTableBuilder.fletchingColumns() {
    column("tool", Fletching.COL_TOOL, VarType.OBJ)
    column("anim", Fletching.COL_ANIM, VarType.STRING)
    column("ticks", Fletching.COL_TICKS, VarType.INT)
    column("first_ticks", Fletching.COL_FIRST_TICKS, VarType.INT)
    column("kind", Fletching.COL_KIND, VarType.STRING)
    column("feather_input", Fletching.COL_FEATHER_INPUT, VarType.INT)
}

private fun fletchingTable(): DBTable =
    productionTable(
        "dbtable.fletching_recipes",
        serverOnly = true,
        extraColumns = { fletchingColumns() },
    ) {
        fletchingRows()
    }
