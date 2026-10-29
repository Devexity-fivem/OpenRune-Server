package org.rsmod.content.bosses.maggotking

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.death.PlayerRespawnHook
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.module.PluginModule

class MaggotKingModule : PluginModule() {
    override fun bind() {
        addSetBinding<PlayerRespawnHook>(MaggotKingDeaths::class.java)
        addSetBinding<NpcDeathKillHook>(MaggotKingKillCount::class.java)
    }
}

/**
 * Counts deaths on the scoreboard, via the respawn hook so staff accounts are counted too.
 * Deaths stay unsafe: the body and grave land outside the darkwood trees.
 */
class MaggotKingDeaths
@Inject
constructor(
    private val instances: InstanceManager,
    private val worldStats: MaggotKingWorldStats,
) : PlayerRespawnHook {
    override fun respawnCoords(player: Player): CoordGrid? {
        val session = instances.sessionForPlayer(player) ?: return null
        if (session.key != MaggotKingInstance.INSTANCE_KEY) return null
        val deaths = player.vars[MaggotKingScoreboard.DEATHS] + 1
        VarPlayerIntMapSetter.set(player, MaggotKingScoreboard.DEATHS, deaths)
        worldStats.addDeath()
        return null
    }
}

/** Records world stats; personal kill count is handled by the generic killcount hook. */
class MaggotKingKillCount @Inject constructor(private val worldStats: MaggotKingWorldStats) :
    NpcDeathKillHook {
    override fun onKill(context: NpcDeathKillContext) {
        if (context.npc.type.id != BOSS_ID) return
        worldStats.addKill()
    }

    private companion object {
        private val BOSS_ID = "npc.maggot_king".asRSCM(RSCMType.NPC)
    }
}
