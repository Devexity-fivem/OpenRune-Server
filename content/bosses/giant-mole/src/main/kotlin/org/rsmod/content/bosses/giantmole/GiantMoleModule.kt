package org.rsmod.content.bosses.giantmole

import org.rsmod.api.player.hook.SpadeDigHook
import org.rsmod.plugin.module.PluginModule

class GiantMoleModule : PluginModule() {
    override fun bind() {
        addSetBinding<SpadeDigHook>(GiantMoleDigHook::class.java)
    }
}
