package org.rsmod.content.other.dizanasquiver

import org.rsmod.api.player.hook.PlayerInvUpdateHook
import org.rsmod.plugin.module.PluginModule

public class DizanaQuiverModule : PluginModule() {
    override fun bind() {
        addSetBinding<PlayerInvUpdateHook>(DizanaQuiverInvSyncHook::class.java)
    }
}
