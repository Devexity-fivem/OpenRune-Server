package org.rsmod.content.raids.tfa.raid.encounter.dagonhai

import jakarta.inject.Inject
import org.rsmod.api.instances.events.InstanceEndedEvent
import org.rsmod.api.instances.events.instanceEventId
import org.rsmod.api.script.onEvent
import org.rsmod.content.raids.tfa.party.TfaLobby
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DagonhaiInstanceScript
@Inject
constructor(private val encounter: DagonhaiEncounter, private val lobby: TfaLobby) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<InstanceEndedEvent>(instanceEventId(DagonhaiEncounter.KEY)) {
            encounter.close(instanceId)
            lobby.raidEnded(instanceId)
        }
    }
}
