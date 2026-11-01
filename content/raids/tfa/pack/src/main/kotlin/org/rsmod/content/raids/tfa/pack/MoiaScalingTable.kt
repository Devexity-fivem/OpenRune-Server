package org.rsmod.content.raids.tfa.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

object MoiaScalingTable {

    const val MOIA = 0

    private val ROWS =
        mapOf(2 to 1900, 3 to 2700, 4 to 3500, 5 to 4300, 6 to 5100, 7 to 5900, 8 to 6700)

    fun scaling() =
        dbTable("dbtable.tfa_moia_scaling", serverOnly = true) {
            column("moia", MOIA, VarType.INT)
            for ((players, hp) in ROWS) {
                row("dbrow.tfa_moia_scaling_$players") { column(MOIA, hp) }
            }
        }
}
