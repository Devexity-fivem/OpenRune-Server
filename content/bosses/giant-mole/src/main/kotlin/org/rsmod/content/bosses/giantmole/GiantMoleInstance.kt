package org.rsmod.content.bosses.giantmole

import jakarta.inject.Inject
import org.rsmod.api.instances.BossInstanceRegistry
import org.rsmod.api.instances.InstanceArea
import org.rsmod.api.instances.InstanceNpc
import org.rsmod.api.instances.InstanceScript
import org.rsmod.api.player.cinematic.Cinematic
import org.rsmod.api.player.cinematic.MinimapState
import org.rsmod.api.player.ui.ifOpenFullOverlay
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

class GiantMoleInstance @Inject constructor(
    registry: BossInstanceRegistry,
    private val eventBus: EventBus,
) : InstanceScript(registry) {

    override fun settingsRow(): String = "dbrow.instance_giant_mole"

    override fun area(): InstanceArea = INSTANCE

    override fun ScriptContext.configure() {
        onExitObject {
            player.clearDarkness(eventBus)
            if (manager.sessionForPlayer(player) != null) {
                defaultLeaveFlow()
            } else {
                telejump(SURFACE_EXIT)
            }
        }

        onInstancePlayerJoin { applyDarkness(player) }
    }

    private fun applyDarkness(player: Player) {
        if (player.hasLightSource()) return
        player.darkness = 3
        Cinematic.setMinimapState(player, MinimapState.MinimapHidden)
        player.ifOpenFullOverlay("interface.darkness_dark", eventBus)
    }

    private companion object {
        private val SURFACE_EXIT = CoordGrid(2999, 3377, 0)

        private val INSTANCE =
            InstanceArea.copyRegions(
                regionIds = listOf(6992, 6993),
                npcSpawns =
                    listOf(
                        InstanceNpc("npc.mole_giant", CoordGrid(1759, 5189, 0)),
                        InstanceNpc("npc.mole_baby_01", CoordGrid(1739, 5177, 0)),
                        InstanceNpc("npc.mole_baby_01", CoordGrid(1756, 5164, 0)),
                        InstanceNpc("npc.mole_baby_01", CoordGrid(1761, 5161, 0)),
                        InstanceNpc("npc.mole_baby_01", CoordGrid(1765, 5178, 0)),
                        InstanceNpc("npc.mole_baby_01", CoordGrid(1737, 5156, 0)),
                        InstanceNpc("npc.mole_baby_01", CoordGrid(1741, 5188, 0)),
                        InstanceNpc("npc.mole_baby_01", CoordGrid(1772, 5200, 0)),
                        InstanceNpc("npc.mole_baby_01", CoordGrid(1776, 5241, 0)),
                        InstanceNpc("npc.mole_baby_01", CoordGrid(1736, 5227, 0)),
                        InstanceNpc("npc.mole_baby_02", CoordGrid(1756, 5181, 0)),
                        InstanceNpc("npc.mole_baby_02", CoordGrid(1760, 5152, 0)),
                        InstanceNpc("npc.mole_baby_02", CoordGrid(1779, 5165, 0)),
                        InstanceNpc("npc.mole_baby_02", CoordGrid(1780, 5180, 0)),
                        InstanceNpc("npc.mole_baby_02", CoordGrid(1781, 5152, 0)),
                        InstanceNpc("npc.mole_baby_02", CoordGrid(1746, 5221, 0)),
                        InstanceNpc("npc.mole_baby_02", CoordGrid(1755, 5199, 0)),
                        InstanceNpc("npc.mole_baby_02", CoordGrid(1770, 5226, 0)),
                        InstanceNpc("npc.mole_baby_02", CoordGrid(1783, 5191, 0)),
                        InstanceNpc("npc.mole_baby_02", CoordGrid(1734, 5202, 0)),
                        InstanceNpc("npc.mole_baby_03", CoordGrid(1742, 5163, 0)),
                        InstanceNpc("npc.mole_baby_03", CoordGrid(1746, 5152, 0)),
                        InstanceNpc("npc.mole_baby_03", CoordGrid(1746, 5171, 0)),
                        InstanceNpc("npc.mole_baby_03", CoordGrid(1762, 5182, 0)),
                        InstanceNpc("npc.mole_baby_03", CoordGrid(1764, 5164, 0)),
                        InstanceNpc("npc.mole_baby_03", CoordGrid(1770, 5174, 0)),
                        InstanceNpc("npc.mole_baby_03", CoordGrid(1760, 5215, 0)),
                        InstanceNpc("npc.mole_baby_03", CoordGrid(1763, 5196, 0)),
                        InstanceNpc("npc.mole_baby_03", CoordGrid(1778, 5232, 0)),
                        InstanceNpc("npc.mole_baby_03", CoordGrid(1779, 5210, 0)),
                        InstanceNpc("npc.mole_baby_03", CoordGrid(1737, 5209, 0)),
                    ),
            )
    }
}
