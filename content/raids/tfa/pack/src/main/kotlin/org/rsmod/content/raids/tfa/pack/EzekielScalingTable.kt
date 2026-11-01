package org.rsmod.content.raids.tfa.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

object EzekielScalingTable {

    const val EZEKIEL = 0

    private val ROWS =
        mapOf(2 to 3000, 3 to 4350, 4 to 5600, 5 to 6850, 6 to 8100, 7 to 9350, 8 to 10600)

    fun scaling() =
        dbTable("dbtable.tfa_ezekiel_scaling", serverOnly = true) {
            column("ezekiel", EZEKIEL, VarType.INT)
            for ((players, hp) in ROWS) {
                row("dbrow.tfa_ezekiel_scaling_$players") { column(EZEKIEL, hp) }
            }
        }
}
