package dev.openrune.tools

import dev.openrune.toml.model.TomlValue
import dev.openrune.toml.rsconfig.RuneScapeTomlBlock
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PackServerConfigOverlayMergeTest {

    private fun int(value: Long) = TomlValue.Integer(value)

    private fun string(value: String) = TomlValue.String(value)

    private val tables = setOf("npc")

    @Test
    fun `overlay merge unions params sub-table and keeps base-only scalars`() {
        val base =
            TomlValue.Map(
                mapOf(
                    "id" to string("npc.lesser_demon"),
                    "params" to
                        TomlValue.Map(
                            mapOf(
                                "param.attack_anim" to string("seq.demon_attack"),
                                "param.demon" to int(1),
                            )
                        ),
                )
            )
        val overlay =
            TomlValue.Map(
                mapOf(
                    "id" to string("npc.lesser_demon"),
                    "wanderRange" to int(2),
                )
            )

        val merged = PackServerConfig.mergeOverlayBlocks(base, overlay)
        val params = merged.properties["params"] as TomlValue.Map

        assertEquals(2L, (merged.properties["wanderRange"] as TomlValue.Integer).value)
        assertEquals(
            "seq.demon_attack",
            (params.properties["param.attack_anim"] as TomlValue.String).value,
        )
        assertEquals(1L, (params.properties["param.demon"] as TomlValue.Integer).value)
    }

    @Test
    fun `overlay merge lets later scalars and params win on conflict`() {
        val base =
            TomlValue.Map(
                mapOf(
                    "id" to string("npc.x"),
                    "wanderRange" to int(4),
                    "params" to TomlValue.Map(mapOf("param.killcount_varp" to string("varp.a"))),
                )
            )
        val overlay =
            TomlValue.Map(
                mapOf(
                    "id" to string("npc.x"),
                    "wanderRange" to int(0),
                    "params" to TomlValue.Map(mapOf("param.killcount_varp" to string("varp.b"))),
                )
            )

        val merged = PackServerConfig.mergeOverlayBlocks(base, overlay)
        val params = merged.properties["params"] as TomlValue.Map

        assertEquals(0L, (merged.properties["wanderRange"] as TomlValue.Integer).value)
        assertEquals("varp.b", (params.properties["param.killcount_varp"] as TomlValue.String).value)
    }

    @Test
    fun `collect groups same-id blocks into one merged overlay`() {
        val file1 = File("server.toml")
        val file2 = File("pack.toml")
        val first =
            RuneScapeTomlBlock(
                "npc",
                TomlValue.Map(
                    mapOf(
                        "id" to string("npc.callisto"),
                        "huntRange" to int(30),
                        "params" to
                            TomlValue.Map(mapOf("param.death_anim" to string("seq.npc_callisto_death01"))),
                    )
                ),
            )
        val second =
            RuneScapeTomlBlock(
                "npc",
                TomlValue.Map(
                    mapOf(
                        "id" to string("npc.callisto"),
                        "params" to TomlValue.Map(mapOf("param.elemental_weakness_percent" to int(30))),
                    )
                ),
            )
        val unrelated =
            RuneScapeTomlBlock(
                "npc",
                TomlValue.Map(mapOf("id" to string("npc.other"), "wanderRange" to int(1))),
            )

        val filesByTable = mutableMapOf<String, MutableSet<File>>()
        val grouped =
            PackServerConfig.collectBlocksByTable(
                listOf(file1 to listOf(first), file2 to listOf(second, unrelated)),
                filesByTable,
                tables,
            )

        val npcs = grouped["npc"].orEmpty()
        assertEquals(setOf("npc.callisto", "npc.other"), npcs.keys)

        val merged = npcs.getValue("npc.callisto")
        assertEquals(30L, (merged.properties["huntRange"] as TomlValue.Integer).value)
        val params = merged.properties["params"] as TomlValue.Map
        assertEquals(
            "seq.npc_callisto_death01",
            (params.properties["param.death_anim"] as TomlValue.String).value,
        )
        assertEquals(
            30L,
            (params.properties["param.elemental_weakness_percent"] as TomlValue.Integer).value,
        )

        assertTrue(filesByTable.getValue("npc").containsAll(setOf(file1, file2)))
    }

    @Test
    fun `collect keeps id-less blocks separate`() {
        val file = File("pack.toml")
        val a = RuneScapeTomlBlock("npc", TomlValue.Map(mapOf("wanderRange" to int(1))))
        val b = RuneScapeTomlBlock("npc", TomlValue.Map(mapOf("wanderRange" to int(2))))

        val grouped =
            PackServerConfig.collectBlocksByTable(
                listOf(file to listOf(a, b)),
                mutableMapOf(),
                tables,
            )

        assertEquals(2, grouped["npc"].orEmpty().size)
    }
}
