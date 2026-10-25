package org.rsmod.content.raids.tfa.raid.encounter.ocellus

import jakarta.inject.Inject
import org.rsmod.api.instances.events.InstanceEndedEvent
import org.rsmod.api.instances.events.instanceEventId
import org.rsmod.api.script.onEvent
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class OcellusInstanceScript @Inject constructor(private val encounter: OcellusEncounter) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<InstanceEndedEvent>(instanceEventId(OcellusEncounter.KEY)) {
            encounter.close(instanceId)
        }
    }
}
