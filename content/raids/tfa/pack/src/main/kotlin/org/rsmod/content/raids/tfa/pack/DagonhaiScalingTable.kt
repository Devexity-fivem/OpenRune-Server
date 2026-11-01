package org.rsmod.content.raids.tfa.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

object DagonhaiScalingTable {

    const val DAGONHAI = 0

    private val ROWS =
        mapOf(2 to 1800, 3 to 2600, 4 to 3400, 5 to 4200, 6 to 5000, 7 to 5800, 8 to 6600)

    fun scaling() =
        dbTable("dbtable.tfa_dagonhai_scaling", serverOnly = true) {
            column("dagonhai", DAGONHAI, VarType.INT)
            for ((players, hp) in ROWS) {
                row("dbrow.tfa_dagonhai_scaling_$players") { column(DAGONHAI, hp) }
            }
        }
}
