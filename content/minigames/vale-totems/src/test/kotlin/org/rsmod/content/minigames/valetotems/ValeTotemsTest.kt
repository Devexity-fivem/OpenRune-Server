package org.rsmod.content.minigames.valetotems

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.loc.MapLocDefinition
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.table.ValeTotemsSitesRow
import org.rsmod.api.table.ValeTotemsWoodsRow
import org.rsmod.api.table.fletching.FletchingRecipesRow
import org.rsmod.map.square.MapSquareKey

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ValeTotemsTest {

    private data class Placed(val id: Int, val x: Int, val z: Int)

    private lateinit var locs: List<Placed>

    @BeforeAll
    fun load() {
        val cache = ServerCacheManager.init(240)
        try {
            val all = mutableListOf<Placed>()
            for (mx in 20..24) {
                for (mz in 49..55) {
                    val key = MapSquareKey(mx, mz)
                    val data = cache.data(MAPS, key.id, 1) ?: continue
                    val map = MapLocListDecoder.decode(InlineByteBuf(data))
                    for (spawn in map.spawns) {
                        val def = MapLocDefinition(spawn)
                        all += Placed(def.id, mx * 64 + def.localX, mz * 64 + def.localZ)
                    }
                }
            }
            locs = all
        } finally {
            cache.close()
        }
    }

    @Test
    fun `all eight sites have the full totem loc set`() {
        val rows = ValeTotemsSitesRow.all()
        assertEquals(8, rows.size)
        for (row in rows) {
            val base = RSCM.getRSCM("loc.ent_totems_site_${row.key}_base")
            val low = RSCM.getRSCM("loc.ent_totems_site_${row.key}_low")
            val mid = RSCM.getRSCM("loc.ent_totems_site_${row.key}_mid")
            val top = RSCM.getRSCM("loc.ent_totems_site_${row.key}_top")
            val deco = RSCM.getRSCM("loc.ent_totems_site_${row.key}_decoration")
            val pile = RSCM.getRSCM("loc.ent_totems_site_${row.key}_offerings")
            val totemX = row.totem.x
            val totemZ = row.totem.z
            val pileX = row.offerings.x
            val pileZ = row.offerings.z
            assertTrue(
                locs.any { it.id == base && it.x == totemX && it.z == totemZ },
                "site ${row.key} base missing at $totemX,$totemZ",
            )
            assertTrue(locs.any { it.id == low && it.x == totemX && it.z == totemZ })
            assertTrue(locs.any { it.id == mid && it.x == totemX && it.z == totemZ })
            assertTrue(locs.any { it.id == top && it.x == totemX && it.z == totemZ })
            assertTrue(locs.any { it.id == deco && it.x == totemX && it.z == totemZ })
            assertTrue(
                locs.any { it.id == pile && it.x == pileX && it.z == pileZ },
                "site ${row.key} offerings missing at $pileX,$pileZ",
            )
        }
    }

    @Test
    fun `site base multiloc resolves base plus carved state`() {
        val cache = ServerCacheManager.init(240)
        try {
            val multi =
                ServerCacheManager.getObjectOrDefault(
                    RSCM.getRSCM("loc.ent_totems_site_1_base"),
                )
            val transforms = checkNotNull(multi.transforms)
            assertEquals(57064, transforms[0], "empty site resolves to base_none")
            for (w in 1..6) {
                val baseLoc = ServerCacheManager.getObjectOrDefault(transforms[w])
                assertTrue(
                    baseLoc.internalName.contains("base_") &&
                        !baseLoc.internalName.contains("carved"),
                    "wood $w index should resolve an uncarved base, got ${baseLoc.internalName}",
                )
                val carvedLoc = ServerCacheManager.getObjectOrDefault(transforms[w + 8])
                assertTrue(
                    carvedLoc.internalName.contains("carved_base_"),
                    "carved wood $w index should resolve a carved base, got ${carvedLoc.internalName}",
                )
            }
        } finally {
            cache.close()
        }
    }

    @Test
    fun `wood rows carry build carve and offering data`() {
        val rows = ValeTotemsWoodsRow.all()
        assertEquals(6, rows.size)
        val names = listOf("Oak", "Willow", "Maple", "Yew", "Magic", "Redwood")
        val levels = listOf(20, 35, 50, 65, 80, 90)
        // Per-visit base offerings x10, matching the wiki: 2/2/2/2.5/3/3.
        val visitUnits = listOf(20, 20, 20, 25, 30, 30)
        rows.forEachIndexed { i, row ->
            assertEquals(i + 1, row.key)
            assertTrue(row.name.startsWith(names[i]), "row $i name=${row.name}")
            assertEquals(levels[i], row.level)
            assertTrue(row.actionXp > 0 && row.decorXp > 0)
            assertTrue(row.durability in 2..7)
            assertTrue(row.offerings > 0)
            assertEquals(visitUnits[i], row.visitUnit, "row $i visit unit")
            assertNotNull(row.log.internalName)
        }
    }

    @Test
    fun `per visit offerings match the wiki table`() {
        // ceil(unit x (1 + decorations)) for 0..4 decorations, all correct carves.
        val expected =
            listOf(
                intArrayOf(2, 4, 6, 8, 10),
                intArrayOf(2, 4, 6, 8, 10),
                intArrayOf(2, 4, 6, 8, 10),
                intArrayOf(3, 5, 8, 10, 13),
                intArrayOf(3, 6, 9, 12, 15),
                intArrayOf(3, 6, 9, 12, 15),
            )
        val rows = ValeTotemsWoodsRow.all()
        rows.forEachIndexed { i, row ->
            for (decorations in 0..4) {
                val perVisit = kotlin.math.ceil(row.visitUnit * (1 + decorations) / 10.0).toInt()
                assertEquals(
                    expected[i][decorations],
                    perVisit,
                    "row $i with $decorations decorations",
                )
            }
        }
    }

    @Test
    fun `decoration fletching levels match the wiki creation table`() {
        val levelsByOutput =
            FletchingRecipesRow.all().associate { it.output.internalName to it.statReq.first().t1 }
        val expected =
            mapOf(
                "obj.oak_shortbow" to 20,
                "obj.unstrung_oak_shortbow" to 20,
                "obj.oak_longbow" to 25,
                "obj.unstrung_oak_longbow" to 25,
                "obj.oak_shield" to 27,
                "obj.xbows_crossbow_stock_oak" to 24,
                "obj.willow_shortbow" to 35,
                "obj.unstrung_willow_shortbow" to 35,
                "obj.willow_longbow" to 40,
                "obj.unstrung_willow_longbow" to 40,
                "obj.willow_shield" to 42,
                "obj.xbows_crossbow_stock_willow" to 39,
                "obj.maple_shortbow" to 50,
                "obj.unstrung_maple_shortbow" to 50,
                "obj.maple_longbow" to 55,
                "obj.unstrung_maple_longbow" to 55,
                "obj.maple_shield" to 57,
                "obj.xbows_crossbow_stock_maple" to 54,
                "obj.yew_shortbow" to 65,
                "obj.unstrung_yew_shortbow" to 65,
                "obj.yew_longbow" to 70,
                "obj.unstrung_yew_longbow" to 70,
                "obj.yew_shield" to 72,
                "obj.xbows_crossbow_stock_yew" to 69,
                "obj.magic_shortbow" to 80,
                "obj.unstrung_magic_shortbow" to 80,
                "obj.magic_longbow" to 85,
                "obj.unstrung_magic_longbow" to 85,
                "obj.magic_shield" to 87,
                "obj.xbows_crossbow_stock_magic" to 78,
                "obj.redwood_hiking_staff" to 90,
                "obj.redwood_shield" to 92,
            )
        val enum =
            checkNotNull(
                ServerCacheManager.getEnum(RSCM.getRSCM("enum.vale_totems_decoration_wood")),
            )
        for ((key, _) in enum.values) {
            val internal = RSCM.getReverseMapping(RSCMType.OBJ, key as Int)
            val expectedLevel = expected[internal] ?: continue
            assertEquals(
                expectedLevel,
                levelsByOutput[internal],
                "fletching level for $internal",
            )
        }
    }

    @Test
    fun `decoration enum maps valid fletched items per wood`() {
        val cache = ServerCacheManager.init(240)
        try {
            val enum =
                checkNotNull(
                    ServerCacheManager.getEnum(
                        RSCM.getRSCM("enum.vale_totems_decoration_wood"),
                    ),
                )
            assertEquals(32, enum.values.size)
            val byWood = enum.values.entries.groupBy({ it.value }, { it.key })
            for (wood in 1..5) {
                assertEquals(6, byWood.getValue(wood).size, "wood $wood has 6 decorations")
            }
            assertEquals(2, byWood.getValue(6).size, "redwood has 2 decorations")
        } finally {
            cache.close()
        }
    }

    @Test
    fun `research exchange inventory seeds its shop stock`() {
        val cache = ServerCacheManager.init(240)
        try {
            val inv =
                checkNotNull(
                    ServerCacheManager.getInventory(
                        RSCM.getRSCM("inv.deadmanlose_dummy"),
                    ),
                )
            assertEquals(4, inv.stock.size)
            assertTrue(inv.stock.all { it.count == 100 })
            val names = inv.stock.map { ServerCacheManager.getItem(it.obj)?.internalName }
            assertTrue(names.contains("obj.bowstring_spool"), "stock=$names")
            assertTrue(names.contains("obj.fletching_knife"), "stock=$names")
            assertTrue(names.contains("obj.greenman_mask"), "stock=$names")
            assertTrue(names.contains("obj.ent_branch"), "stock=$names")
        } finally {
            cache.close()
        }
    }

    @Test
    fun `vale offerings item exposes rummage ops`() {
        val cache = ServerCacheManager.init(240)
        try {
            val loot =
                checkNotNull(
                    ServerCacheManager.getItem(
                        RSCM.getRSCM("obj.ent_totems_loot"),
                    ),
                )
            val ops = loot.interfaceOptions
            assertEquals("Rummage", ops[0])
            assertEquals("Hasty-rummage", ops[1])
            assertEquals("Inspect", ops[2])
        } finally {
            cache.close()
        }
    }
}
