package org.rsmod.api.player.events

import dev.openrune.types.ItemServerType
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player

public data class PlayerAteFoodEvent(
    public val player: Player,
    public val food: ItemServerType,
    public val healAmount: Int,
) : UnboundEvent
