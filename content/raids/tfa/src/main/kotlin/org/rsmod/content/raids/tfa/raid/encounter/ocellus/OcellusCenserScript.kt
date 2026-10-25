package org.rsmod.content.raids.tfa.raid.encounter.ocellus

import jakarta.inject.Inject
import org.rsmod.api.script.onOpLoc1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Lighting one of the censers round Ocellus's room; see [OcellusCensers]. */
class OcellusCenserScript @Inject constructor(private val encounter: OcellusEncounter) :
    PluginScript() {
    override fun ScriptContext.startup() {
        for (censer in CENSERS) {
            onOpLoc1(censer) { encounter.lightCenser(it.loc)?.let(::mes) }
        }
    }

    private companion object {
        val CENSERS =
            listOf(
                "loc.tfa_ocellus_artifact_heal",
                "loc.tfa_ocellus_artifact_spec",
                "loc.tfa_ocellus_artifact_damage_melee",
                "loc.tfa_ocellus_artifact_damage_ranged",
            )
    }
}
