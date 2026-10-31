package org.rsmod.content.quest.manager

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.config.refs.params
import org.rsmod.api.npc.aggression.AggressionGate
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

@Singleton
internal class QuestAggressionGate @Inject constructor() : AggressionGate {
    override fun permitsAggression(npc: Npc, player: Player): Boolean {
        val quest = npc.paramOrNull(params.aggro_unless_quest) ?: return true
        return !QuestRequirements.hasCompleted(player, quest)
    }
}
