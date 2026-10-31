package org.rsmod.content.skills.hunter

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcServerType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.roundToInt
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/**
 * Shared state for the Puro-Puro maze: the wandering crop circle that rotates between
 * overworld wheat fields, implings freed by imp defenders (which only their liberator
 * may re-catch), and the per-player catch totals shown by the impling tracker.
 */
@Singleton
class PuroPuroManager
@Inject
constructor(private val mapClock: MapClock, private val npcRepo: NpcRepository) {

    class Field(val name: String, val coords: CoordGrid)

    private val releasedOwners = HashMap<Npc, Player>()
    private var circle: Npc? = null
    private var rotateAt = 0

    var fieldIndex = -1
        private set

    val currentField: Field
        get() = FIELDS[fieldIndex.coerceAtLeast(0)]

    fun ownerOf(npc: Npc): Player? = releasedOwners[npc]

    fun markReleased(npc: Npc, owner: Player) {
        releasedOwners[npc] = owner
    }

    fun forget(npc: Npc) {
        releasedOwners.remove(npc)
    }

    fun forgetAll(owner: Player) {
        releasedOwners.entries.removeAll { it.value == owner }
    }

    fun startup() {
        if (circle == null) {
            fieldIndex = (FIELDS.indices).random()
            respawnCircle()
        }
    }

    fun tick() {
        if (mapClock.cycle >= rotateAt) {
            fieldIndex = (fieldIndex + 1).mod(FIELDS.size)
            respawnCircle()
        }
    }

    private fun respawnCircle() {
        circle?.let { npcRepo.del(it, Int.MAX_VALUE) }
        circle =
            Npc(CIRCLE_TYPE, currentField.coords).also {
                npcRepo.add(it, Int.MAX_VALUE)
            }
        rotateAt = mapClock.cycle + ROTATE_TICKS
    }

    fun circleNear(coords: CoordGrid): Boolean {
        val c = circle ?: return false
        return c.coords.level == coords.level && c.coords.chebyshevDistance(coords) <= 1
    }

    fun trackerCount(player: Player, index: Int, puro: Boolean): Int {
        val packed = player.vars["varp.ii_tracker_${index + 1}"]
        return if (puro) packed ushr 16 else packed and 0xFFFF
    }

    fun bumpTracker(player: Player, index: Int, puro: Boolean) {
        val symbol = "varp.ii_tracker_${index + 1}"
        val packed = player.vars[symbol]
        val next =
            if (puro) {
                (packed + (1 shl 16)).coerceAtMost(Int.MAX_VALUE)
            } else {
                ((packed and 0xFFFF0000.toInt()) or ((packed and 0xFFFF) + 1).coerceAtMost(0xFFFF))
            }
        VarPlayerIntMapSetter.set(player, symbol, next)
    }

    fun affinityLeft(player: Player): Int = player.vars[AFFINITY_VARBIT]

    fun grantAffinity(player: Player) {
        VarPlayerIntMapSetter.set(player, AFFINITY_VARBIT, AFFINITY_TICKS / AFFINITY_UNIT_TICKS)
    }

    fun tickAffinity(player: Player) {
        if (mapClock.cycle % AFFINITY_UNIT_TICKS != 0) {
            return
        }
        val left = player.vars[AFFINITY_VARBIT]
        if (left > 0) {
            VarPlayerIntMapSetter.set(player, AFFINITY_VARBIT, left - 1)
        }
    }

    fun wheatOutcome(strength: Int): Int {
        val efficient = ((100 * strength - 2) / 98.0).roundToInt().coerceIn(0, 256)
        val normal =
            ((11 * (99 - strength) + 257 * (strength - 1)) / 98.0)
                .roundToInt()
                .coerceIn(0, 256)
        val roll = (0 until 256).random()
        return when {
            roll < efficient -> OUTCOME_FAST
            roll - efficient < normal -> OUTCOME_NORMAL
            else -> OUTCOME_HARD
        }
    }

    companion object {
        const val OUTCOME_FAST = 0
        const val OUTCOME_NORMAL = 1
        const val OUTCOME_HARD = 2

        const val AFFINITY_VARBIT = "varbit.ii_affinity_time_left"
        const val AFFINITY_TICKS = 3000
        const val AFFINITY_UNIT_TICKS = 12
        const val AFFINITY_TICK_REDUCTION = 3
        const val ROTATE_TICKS = 1500

        val PURO_ENTRY = CoordGrid(2591, 4319)
        val ZANARIS_CIRCLE = CoordGrid(2428, 4446)
        val ZANARIS_EXIT = CoordGrid(2428, 4447)

        private val PURO_REGION_X = 2591 - 35
        private val PURO_REGION_Z = 4319 - 35

        fun inPuro(coords: CoordGrid): Boolean =
            coords.level == 0 &&
                coords.x in (PURO_REGION_X)..(PURO_REGION_X + 70) &&
                coords.z in (PURO_REGION_Z)..(PURO_REGION_Z + 70)

        val FIELDS =
            listOf(
                Field("Draynor Village", CoordGrid(3144, 3298)),
                Field("Rimmington", CoordGrid(2967, 3214)),
                Field("Lumbridge", CoordGrid(3166, 3275)),
                Field("Varrock", CoordGrid(3175, 3359)),
                Field("Yanille", CoordGrid(2573, 3104)),
                Field("East Ardougne", CoordGrid(2902, 3381)),
                Field("Catherby", CoordGrid(2806, 3463)),
                Field("Taverley", CoordGrid(2952, 3407)),
                Field("Brimhaven", CoordGrid(2783, 3211)),
                Field("Miscellania", CoordGrid(2555, 3851)),
                Field("Hosidius", CoordGrid(1738, 3553)),
                Field("Gnome Stronghold", CoordGrid(2434, 3424)),
            )

        val CIRCLE_TYPE: NpcServerType =
            checkNotNull(
                ServerCacheManager.getNpc("npc.ii_crop_circle_npc".asRSCM(RSCMType.NPC))
            )
    }
}
