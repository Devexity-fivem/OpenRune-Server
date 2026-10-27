package org.rsmod.content.bosses.zulrah

import jakarta.inject.Inject
import org.rsmod.api.instances.BossInstanceRegistry
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceScript
import org.rsmod.plugin.scripts.ScriptContext

class ZulrahInstance
@Inject
internal constructor(
    registry: BossInstanceRegistry,
    private val fights: ZulrahFights,
) : InstanceScript(registry) {

    override fun settingsRow(): String = "dbrow.instance_zulrah"

    override fun area(): InstanceArea = AREA

    override fun ScriptContext.configure() {
        onInstancePlayerJoin { fights.start(player) }
    }

    private companion object {
        private val AREA = InstanceArea.copyRegions(regionIds = listOf(9007, 9008))
    }
}
