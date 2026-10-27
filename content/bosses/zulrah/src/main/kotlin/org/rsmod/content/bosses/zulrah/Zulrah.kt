package org.rsmod.content.bosses.zulrah

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onModifyNpcHit
import org.rsmod.api.script.onNpcQueue
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class Zulrah
@Inject
internal constructor(
    private val deps: BossDeps,
    private val fights: ZulrahFights,
    private val npcDeath: NpcDeath,
) : PluginScript() {

    override fun ScriptContext.startup() {
        for (form in ZulrahRotations.Form.entries) {
            val type = ServerCacheManager.getNpc(form.npc.asRSCM(RSCMType.NPC)) ?: continue

            // Hits above the 50 damage cap land for a random 45-50 instead.
            onModifyNpcHit(type) {
                if (hit.damage > DAMAGE_CAP) hit.damage = deps.random.of(CAP_ROLL_MIN, DAMAGE_CAP)
            }

            onNpcQueue(type, "queue.death") {
                npcDeath.deathWithDrops(this, fights.onDeath(npc) ?: npc.coords)
            }
        }

        onEvent<NpcStateEvents.Delete> { fights.onDeleted(npc) }
    }

    private companion object {
        private const val DAMAGE_CAP = 50
        private const val CAP_ROLL_MIN = 45
    }
}
