package dev.openrune.tables.skills.runecrafting

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

object Tiara {

    const val ITEM = 0
    const val ALTER = 1
    const val XP = 2

    fun tiara() = dbTable("dbtable.runecrafting_tiara", serverOnly = true) {
        column("item", ITEM, VarType.OBJ)
        column("alter", ALTER, VarType.LOC)
        column("xp", XP, VarType.INT)

        row("dbrow.runecrafting_tiara_air") {
            columnRSCM(ITEM, "obj.tiara_air")
            columnRSCM(ALTER, "loc.air_altar")
            column(XP, 250)
        }

        row("dbrow.runecrafting_tiara_mind") {
            columnRSCM(ITEM, "obj.tiara_mind")
            columnRSCM(ALTER, "loc.mind_altar")
            column(XP, 275)
        }

        row("dbrow.runecrafting_tiara_water") {
            columnRSCM(ITEM, "obj.tiara_water")
            columnRSCM(ALTER, "loc.water_altar")
            column(XP, 300)
        }

        row("dbrow.runecrafting_tiara_earth") {
            columnRSCM(ITEM, "obj.tiara_earth")
            columnRSCM(ALTER, "loc.earth_altar")
            column(XP, 325)
        }

        row("dbrow.runecrafting_tiara_fire") {
            columnRSCM(ITEM, "obj.tiara_fire")
            columnRSCM(ALTER, "loc.fire_altar")
            column(XP, 350)
        }

        row("dbrow.runecrafting_tiara_body") {
            columnRSCM(ITEM, "obj.tiara_body")
            columnRSCM(ALTER, "loc.body_altar")
            column(XP, 375)
        }

        row("dbrow.runecrafting_tiara_cosmic") {
            columnRSCM(ITEM, "obj.tiara_cosmic")
            columnRSCM(ALTER, "loc.cosmic_altar")
            column(XP, 400)
        }

        row("dbrow.runecrafting_tiara_chaos") {
            columnRSCM(ITEM, "obj.tiara_chaos")
            columnRSCM(ALTER, "loc.chaos_altar")
            column(XP, 425)
        }

        row("dbrow.runecrafting_tiara_nature") {
            columnRSCM(ITEM, "obj.tiara_nature")
            columnRSCM(ALTER, "loc.nature_altar")
            column(XP, 450)
        }

        row("dbrow.runecrafting_tiara_law") {
            columnRSCM(ITEM, "obj.tiara_law")
            columnRSCM(ALTER, "loc.law_altar")
            column(XP, 475)
        }

        row("dbrow.runecrafting_tiara_death") {
            columnRSCM(ITEM, "obj.tiara_death")
            columnRSCM(ALTER, "loc.death_altar")
            column(XP, 500)
        }

        row("dbrow.runecrafting_tiara_wrath") {
            columnRSCM(ITEM, "obj.tiara_wrath")
            columnRSCM(ALTER, "loc.wrath_altar")
            column(XP, 525)
        }

    }

}
