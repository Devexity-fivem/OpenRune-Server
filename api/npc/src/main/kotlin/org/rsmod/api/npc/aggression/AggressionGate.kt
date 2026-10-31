package org.rsmod.api.npc.aggression

import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

public fun interface AggressionGate {
    /** Returns `false` to stop [npc] hunting [player] this tick. */
    public fun permitsAggression(npc: Npc, player: Player): Boolean
}
