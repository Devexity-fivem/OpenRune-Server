package org.rsmod.api.npc

import org.rsmod.api.npc.aggression.AggressionGate
import org.rsmod.api.npc.aggression.ConditionalAggression
import org.rsmod.plugin.module.PluginModule

public class NpcModule : PluginModule() {
    override fun bind() {
        newSetBinding<AggressionGate>()
        addSetBinding<AggressionGate>(ConditionalAggression::class.java)
    }
}
