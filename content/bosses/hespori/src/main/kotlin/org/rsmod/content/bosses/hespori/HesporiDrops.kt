package org.rsmod.content.bosses.hespori

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.WeakHashMap
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.death.NpcDropReceiveHook
import org.rsmod.api.invtx.invAddOrDrop
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.game.entity.Player
import org.rsmod.plugin.module.PluginModule

class HesporiDropsModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcDeathKillHook>(HesporiLootHooks::class.java)
        addSetBinding<NpcDropReceiveHook>(HesporiLootHooks::class.java)
    }
}

// In OSRS the kill awards nothing to the ground: the dead plant is harvested
// for its seeds. The patch-harvest step is not modelled, so the rolled drops
// are placed straight into the killer's inventory instead (overflow drops to
// the floor under them as usual).
@Singleton
class HesporiLootHooks @Inject constructor(private val objRepo: ObjRepository) :
    NpcDeathKillHook, NpcDropReceiveHook {

    // Kill hooks run before NpcDropTableKillHook (priority 0), so the flag
    // always reflects the kill whose drops are being rolled.
    override val priority: Int
        get() = 1

    private val inventoryKill = WeakHashMap<Player, Boolean>()

    override fun onKill(context: NpcDeathKillContext) {
        inventoryKill[context.hero] = context.npc.isType(BOSS)
    }

    override fun tryReceive(receiver: Player, obj: String, count: Int): Boolean {
        if (inventoryKill[receiver] != true) return false
        receiver.invAddOrDrop(objRepo, obj, count, coords = receiver.coords)
        return true
    }

    private companion object {
        private const val BOSS = "npc.hespori"
    }
}
