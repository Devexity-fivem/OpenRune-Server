package org.rsmod.api.player.output

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import net.rsprot.protocol.game.outgoing.specific.PlayerSpotAnimSpecific
import org.rsmod.game.entity.Player

/** Plays a spotanim on [this] player that only their own client sees. @see [PlayerSpotAnimSpecific] */
public fun Player.spotanimSpecific(spot: String, delay: Int = 0, height: Int = 0, slot: Int = 0) {
    client.write(PlayerSpotAnimSpecific(slotId, spot.asRSCM(RSCMType.SPOTANIM), slot, height, delay))
}
