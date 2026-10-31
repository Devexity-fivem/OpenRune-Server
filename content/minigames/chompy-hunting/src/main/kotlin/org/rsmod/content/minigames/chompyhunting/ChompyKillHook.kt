package org.rsmod.content.minigames.chompyhunting

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.inv.isType

internal class ChompyKillHook @Inject constructor(private val npcRepo: NpcRepository) :
    NpcDeathKillHook {

    override fun onKill(context: NpcDeathKillContext) {
        if (!context.npc.type.isType(CHOMPY)) {
            return
        }
        val player = context.hero
        val kills = player.vars[ChompyHunting.KILLS_VARP] + 1
        VarPlayerIntMapSetter.set(player, ChompyHunting.KILLS_VARP, kills)
        player.mes(
            "You scratch a notch on your bow for the chompy bird kill.",
            ChatType.GameMessage,
        )
        val unlocked = ChompyHunting.hatUnlockedAt(kills)
        if (unlocked != null) {
            player.mes("You can now claim $unlocked from Rantz!", ChatType.GameMessage)
        }
        npcRepo.add(Npc(DEAD_CHOMPY, context.dropCoords), CARCASS_TICKS)
    }

    private companion object {
        const val CHOMPY = "npc.chompybird"
        const val CARCASS_TICKS = 200
        val DEAD_CHOMPY =
            checkNotNull(ServerCacheManager.getNpc("npc.chompybird_dead".asRSCM(RSCMType.NPC)))
    }
}
