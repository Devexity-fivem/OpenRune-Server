package org.rsmod.content.areas.misc.lobbies.pestcontrol

import jakarta.inject.Inject
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.script.onEvent
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class PestControlScript @Inject constructor(private val game: PestControlGame) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<GameLifecycle.LateCycle> { game.tick() }
    }
}
