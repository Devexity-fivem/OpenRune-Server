package org.rsmod.content.skills.hunter

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.output.mes
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/**
 * Tracks gyr falcons standing on caught kebbits until their owner retrieves
 * them or they return to Matthias after [STAY_TICKS]. The falconry pen is the
 * fenced area around Matthias in Piscatoris; the box is an approximation of
 * its bounds since the cache ships no dedicated area def.
 */
@Singleton
class FalconryManager
@Inject
constructor(private val mapClock: MapClock, private val npcRepo: NpcRepository) {
    class Landed(val npc: Npc, val owner: Player, val leavesCycle: Int)

    private val landed = LinkedHashMap<Npc, Landed>()

    var stayTicks = STAY_TICKS

    fun land(falcon: Npc, owner: Player) {
        npcRepo.add(falcon, Int.MAX_VALUE)
        landed[falcon] = Landed(falcon, owner, mapClock.cycle + stayTicks)
    }

    fun ownerOf(npc: Npc): Player? = landed[npc]?.owner

    fun remove(npc: Npc) {
        if (landed.remove(npc) != null) {
            npcRepo.del(npc, Int.MAX_VALUE)
        }
    }

    /** Removes [owner]'s landed falcon, if any. Returns true when one was recalled. */
    fun recallOwned(owner: Player): Boolean {
        val entry = landed.values.firstOrNull { it.owner == owner } ?: return false
        remove(entry.npc)
        return true
    }

    fun tick() {
        for (entry in landed.values.toList()) {
            if (mapClock.cycle >= entry.leavesCycle) {
                remove(entry.npc)
                entry.owner.mes(
                    "Your falcon has left its prey. You see it heading back toward the falconer."
                )
            }
        }
    }

    companion object {
        const val STAY_TICKS = 100

        fun insideArea(coords: CoordGrid): Boolean =
            coords.level == 0 && coords.x in 2355..2400 && coords.z in 3570..3650
    }
}
