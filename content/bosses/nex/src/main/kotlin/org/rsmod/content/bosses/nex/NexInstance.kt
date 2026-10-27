package org.rsmod.content.bosses.nex

import jakarta.inject.Inject
import org.rsmod.api.instances.BossInstanceRegistry
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceNpc
import org.rsmod.api.instances.InstanceScript
import org.rsmod.api.instances.InstanceSession
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.ScriptContext

class NexInstance @Inject constructor(
    registry: BossInstanceRegistry,
    private val npcRepo: NpcRepository,
) : InstanceScript(registry) {

    override fun settingsRow(): String = "dbrow.instance_nex"

    override fun area(): InstanceArea = INSTANCE

    override fun ScriptContext.configure() {
        onEnterObject { enterNexPublic() }
        onOpLoc2(BARRIER) { defaultInstanceEntry() }
        onOpLoc3(BARRIER) { peekNexPublic() }
        onExitObject { defaultLeaveFlow() }
    }

    private suspend fun ProtectedAccess.enterNexPublic() {
        if (manager.sessionForPlayer(player) != null) {
            defaultLeaveFlow()
            return
        }
        val public = manager.sessionsForKey(key).firstOrNull { it.isServerOwned }
        if (public != null && nexAlive(public)) {
            mes("The barrier has sealed shut. Nex is already engaged in battle.")
            return
        }
        enterPublicRoom(INSTANCE)
    }

    private fun nexAlive(session: InstanceSession): Boolean =
        session.regionIds.any { regionId ->
            // Scan the region's central zone with enough radius to cover it.
            val zone =
                ZoneKey(
                    (regionId shr 8) * 8 + 4,
                    (regionId and 0xFF) * 8 + 4,
                    session.placement.instanceLevel,
                )
            npcRepo.findAll(zone, REGION_ZONE_RADIUS).any {
                it.hitpoints > 0 && NEX_FORMS.any(it::isVisType)
            }
        }

    private fun ProtectedAccess.peekNexPublic() {
        val session = manager.sessionsForKey(key).firstOrNull { it.isServerOwned }
        val count = session?.occupants?.size ?: 0
        if (count == 0) {
            mes("Nex's chamber is currently empty.")
        } else {
            mes(
                "There ${if (count == 1) "is" else "are"} $count player${if (count == 1) "" else "s"} " +
                    "in Nex's chamber."
            )
        }
    }

    private companion object {
        private const val BARRIER = "loc.nex_fight_barrier_outer"
        private const val REGION_ZONE_RADIUS = 8

        private val NEX_FORMS =
            listOf("npc.nex", "npc.nex_soulsplit", "npc.nex_deflect", "npc.nex_dying")

        // Regions 45_81 and 46_81 — the chamber straddles the region line at x=2944.
        private val INSTANCE =
            InstanceArea.copyRegions(
                regionIds = listOf(11601, 11857),
                level = 0,
                npcSpawns =
                    listOf(
                        InstanceNpc("npc.nex", CoordGrid(2924, 5203, 0)),
                        InstanceNpc("npc.nex_smokemage", CoordGrid(2914, 5213, 0)),
                        InstanceNpc("npc.nex_shadowmage", CoordGrid(2934, 5213, 0)),
                        InstanceNpc("npc.nex_bloodmage", CoordGrid(2934, 5193, 0)),
                        InstanceNpc("npc.nex_icemage", CoordGrid(2914, 5193, 0)),
                    ),
            )
    }
}
