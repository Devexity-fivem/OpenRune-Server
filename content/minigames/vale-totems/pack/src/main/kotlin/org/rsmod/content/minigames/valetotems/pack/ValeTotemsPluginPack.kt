package org.rsmod.content.minigames.valetotems.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType
import dev.openrune.pack.PluginPack
import dev.openrune.pack.columnCoord
import org.rsmod.map.CoordGrid

class ValeTotemsPluginPack : PluginPack() {
    override fun dbTables() = listOf(ValeTotemsTables.woods(), ValeTotemsTables.sites())
}

object ValeTotemsTables {

    const val WOOD_ID = 0
    const val WOOD_NAME = 1
    const val WOOD_LEVEL = 2
    const val WOOD_ACTION_XP = 3
    const val WOOD_DECOR_XP = 4
    const val WOOD_DURABILITY = 5
    const val WOOD_OFFERINGS = 6
    const val WOOD_LOG = 7
    const val WOOD_VISIT_UNIT = 8

    fun woods() =
        dbTable("dbtable.vale_totems_woods", serverOnly = true) {
            column("key", WOOD_ID, VarType.INT)
            column("name", WOOD_NAME, VarType.STRING)
            column("level", WOOD_LEVEL, VarType.INT)
            column("action_xp", WOOD_ACTION_XP, VarType.INT)
            column("decor_xp", WOOD_DECOR_XP, VarType.INT)
            column("durability", WOOD_DURABILITY, VarType.INT)
            column("offerings", WOOD_OFFERINGS, VarType.INT)
            column("log", WOOD_LOG, VarType.OBJ)
            column("visit_unit", WOOD_VISIT_UNIT, VarType.INT)

            row("dbrow.vale_totems_wood_oak") {
                column(WOOD_ID, 1)
                column(WOOD_NAME, "Oak totem")
                column(WOOD_LEVEL, 20)
                column(WOOD_ACTION_XP, 125)
                column(WOOD_DECOR_XP, 512)
                column(WOOD_DURABILITY, 2)
                column(WOOD_OFFERINGS, 20)
                columnRSCM(WOOD_LOG, "obj.oak_logs")
                column(WOOD_VISIT_UNIT, 20)
            }

            row("dbrow.vale_totems_wood_willow") {
                column(WOOD_ID, 2)
                column(WOOD_NAME, "Willow totem")
                column(WOOD_LEVEL, 35)
                column(WOOD_ACTION_XP, 317)
                column(WOOD_DECOR_XP, 1269)
                column(WOOD_DURABILITY, 3)
                column(WOOD_OFFERINGS, 30)
                columnRSCM(WOOD_LOG, "obj.willow_logs")
                column(WOOD_VISIT_UNIT, 20)
            }

            row("dbrow.vale_totems_wood_maple") {
                column(WOOD_ID, 3)
                column(WOOD_NAME, "Maple totem")
                column(WOOD_LEVEL, 50)
                column(WOOD_ACTION_XP, 508)
                column(WOOD_DECOR_XP, 2010)
                column(WOOD_DURABILITY, 4)
                column(WOOD_OFFERINGS, 40)
                columnRSCM(WOOD_LOG, "obj.maple_logs")
                column(WOOD_VISIT_UNIT, 20)
            }

            row("dbrow.vale_totems_wood_yew") {
                column(WOOD_ID, 4)
                column(WOOD_NAME, "Yew totem")
                column(WOOD_LEVEL, 65)
                column(WOOD_ACTION_XP, 826)
                column(WOOD_DECOR_XP, 3262)
                column(WOOD_DURABILITY, 5)
                column(WOOD_OFFERINGS, 65)
                columnRSCM(WOOD_LOG, "obj.yew_logs")
                column(WOOD_VISIT_UNIT, 25)
            }

            row("dbrow.vale_totems_wood_magic") {
                column(WOOD_ID, 5)
                column(WOOD_NAME, "Magic totem")
                column(WOOD_LEVEL, 80)
                column(WOOD_ACTION_XP, 1561)
                column(WOOD_DECOR_XP, 6198)
                column(WOOD_DURABILITY, 6)
                column(WOOD_OFFERINGS, 90)
                columnRSCM(WOOD_LOG, "obj.magic_logs")
                column(WOOD_VISIT_UNIT, 30)
            }

            row("dbrow.vale_totems_wood_redwood") {
                column(WOOD_ID, 6)
                column(WOOD_NAME, "Redwood totem")
                column(WOOD_LEVEL, 90)
                column(WOOD_ACTION_XP, 2213)
                column(WOOD_DECOR_XP, 7255)
                column(WOOD_DURABILITY, 7)
                column(WOOD_OFFERINGS, 105)
                columnRSCM(WOOD_LOG, "obj.redwood_logs")
                column(WOOD_VISIT_UNIT, 30)
            }
        }

    const val SITE_ID = 0
    const val SITE_TOTEM = 1
    const val SITE_OFFERINGS = 2
    const val SITE_SPIRIT = 3

    fun sites() =
        dbTable("dbtable.vale_totems_sites", serverOnly = true) {
            column("key", SITE_ID, VarType.INT)
            column("totem", SITE_TOTEM, VarType.COORDGRID)
            column("offerings", SITE_OFFERINGS, VarType.COORDGRID)
            column("spirit", SITE_SPIRIT, VarType.COORDGRID)

            site(1, 1370, 3375, 1370, 3374, 1369, 3372)
            site(2, 1346, 3319, 1347, 3319, 1348, 3322)
            site(3, 1385, 3274, 1385, 3275, 1382, 3276)
            site(4, 1413, 3286, 1412, 3286, 1416, 3289)
            site(5, 1438, 3305, 1438, 3306, 1435, 3302)
            site(6, 1477, 3332, 1478, 3332, 1474, 3335)
            site(7, 1453, 3341, 1452, 3341, 1456, 3338)
            site(8, 1398, 3329, 1398, 3330, 1401, 3326)
        }

    private fun dev.openrune.definition.dbtables.DBTableBuilder.site(
        id: Int,
        tx: Int,
        tz: Int,
        ox: Int,
        oz: Int,
        sx: Int,
        sz: Int,
    ) =
        row("dbrow.vale_totems_site_$id") {
            column(SITE_ID, id)
            columnCoord(SITE_TOTEM, CoordGrid(tx, tz, 0))
            columnCoord(SITE_OFFERINGS, CoordGrid(ox, oz, 0))
            columnCoord(SITE_SPIRIT, CoordGrid(sx, sz, 0))
        }
}
