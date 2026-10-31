package org.rsmod.content.areas.city.alkharid.npcs

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import org.rsmod.annotations.InternalApi
import org.rsmod.api.hunt.Hunt
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.script.onNpcHit
import org.rsmod.game.entity.PlayerList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class AlKharidWarriors
@Inject
constructor(
    private val hunt: Hunt,
    private val playerList: PlayerList,
    private val interactions: AiPlayerInteractions,
) : PluginScript() {
    @OptIn(InternalApi::class)
    override fun ScriptContext.startup() {
        val warrior = ServerCacheManager.getNpc(WARRIOR_TYPE.asRSCM(RSCMType.NPC)) ?: return
        onNpcHit(warrior) {
            val attacker =
                hit.takeIf { it.isFromPlayer }?.resolvePlayerSource(playerList) ?: return@onNpcHit
            for (other in hunt.findNpcs(npc.coords, CALL_RANGE, HuntVis.LineOfSight)) {
                if (other === npc || !other.type.isType(WARRIOR_TYPE)) {
                    continue
                }
                if (other.hasInteraction()) {
                    continue
                }
                other.opPlayer2(attacker, interactions)
            }
        }
    }

    private companion object {
        private const val CALL_RANGE = 10
        private const val WARRIOR_TYPE = "npc.al_kharid_warrior"
    }
}
