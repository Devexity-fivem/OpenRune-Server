package org.rsmod.content.skills.hunter

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcServerType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.random.Random
import org.rsmod.api.enums.NamedEnums
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.npc.NpcUid

/**
 * Drives the impling spawn lifecycle: static `ii_*_impling_precursor` npcs are
 * invisible placeholder spawns that manifest as a real impling after roaming
 * invisibly for ~1-3 minutes, stay visible for ~30 minutes, then revert. Caught
 * implings re-enter the cycle through the spawn/respawn events bound in
 * [HunterEvents]. Weights mirror the tiers published on the OSRS wiki.
 */
@Singleton
class ImplingSpawnManager @Inject constructor(private val mapClock: MapClock) {

    private val manifests = ArrayDeque<ImplingManifest>()

    private val pools: Map<String, List<ImplingSpawn>> = buildPools()

    fun isPrecursor(npc: Npc): Boolean = npc.type.internalName in pools

    fun schedule(npc: Npc) {
        manifests.removeAll { it.npc === npc }
        if (npc.transmog != null) {
            npc.resetTransmog()
        }
        manifests +=
            ImplingManifest(
                npc,
                dueCycle = mapClock.cycle + Random.nextInt(INVISIBLE_MIN, INVISIBLE_MAX),
                visible = false,
            )
    }

    fun tick() {
        val cycle = mapClock.cycle
        val requeue = mutableListOf<ImplingManifest>()
        val iter = manifests.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            if (entry.dueCycle > cycle) {
                continue
            }
            iter.remove()
            process(entry)?.let(requeue::add)
        }
        manifests.addAll(requeue)
    }

    private fun process(entry: ImplingManifest): ImplingManifest? {
        val npc = entry.npc
        if (npc.uid == NpcUid.NULL) {
            return null
        }
        if (entry.visible) {
            // The visible window just expired; rest invisibly before the next spawn.
            return ImplingManifest(
                npc,
                dueCycle = mapClock.cycle + Random.nextInt(INVISIBLE_MIN, INVISIBLE_MAX),
                visible = false,
            )
        }
        val target = rollSpawn(npc) ?: return null
        npc.transmog(target, VISIBLE_TICKS)
        return ImplingManifest(npc, dueCycle = mapClock.cycle + VISIBLE_TICKS, visible = true)
    }

    private fun rollSpawn(npc: Npc): NpcServerType? {
        val pool = pools[npc.type.internalName] ?: return null
        val total = pool.sumOf { it.weight }
        var roll = Random.nextInt(total)
        for (spawn in pool) {
            roll -= spawn.weight
            if (roll < 0) {
                return spawn.type
            }
        }
        return pool.last().type
    }

    private class ImplingSpawn(val type: NpcServerType, val weight: Int)

    internal data class ImplingManifest(val npc: Npc, val dueCycle: Int, val visible: Boolean)

    private companion object {
        const val INVISIBLE_MIN = 100
        const val INVISIBLE_MAX = 300
        const val VISIBLE_TICKS = 3000

        fun spawn(symbol: String, weight: Int): ImplingSpawn =
            ImplingSpawn(
                checkNotNull(ServerCacheManager.getNpc(symbol.asRSCM(RSCMType.NPC))) {
                    "Missing npc type: $symbol"
                },
                weight,
            )

        fun buildPools(): Map<String, List<ImplingSpawn>> =
            mapOf(
                "npc.ii_common_impling_precursor" to
                    listOf(
                        spawn("npc.ii_impling_type_1", 20),
                        spawn("npc.ii_impling_type_2", 20),
                        spawn("npc.ii_impling_type_3", 20),
                        spawn("npc.ii_impling_type_4", 20),
                        spawn("npc.ii_impling_type_5", 10),
                        spawn("npc.ii_impling_type_6", 10),
                    ),
                "npc.ii_uncommon_impling_precursor" to
                    listOf(
                        spawn("npc.ii_impling_type_3", 10),
                        spawn("npc.ii_impling_type_4", 10),
                        spawn("npc.ii_impling_type_5", 20),
                        spawn("npc.ii_impling_type_6", 37),
                        spawn("npc.ii_impling_type_7", 20),
                        spawn("npc.ii_impling_type_8", 2),
                        spawn("npc.ii_impling_type_9", 1),
                    ),
                "npc.ii_rare_impling_precursor" to
                    listOf(
                        spawn("npc.ii_impling_type_7", 10),
                        spawn("npc.ii_impling_type_8", 50),
                        spawn("npc.ii_impling_type_9", 30),
                        spawn("npc.ii_impling_type_10", 10),
                        spawn("npc.ii_impling_type_11", 1),
                    ),
                "npc.ii_rare_impling_precursor_maze" to
                    listOf(
                        spawn("npc.ii_impling_type_7_maze", 150),
                        spawn("npc.ii_impling_type_8_maze", 114),
                        spawn("npc.ii_impling_type_9_maze", 27),
                        spawn("npc.ii_impling_type_10_maze", 9),
                        spawn("npc.ii_impling_type_11_maze", 1),
                    ),
                "npc.ii_impling_type_12_precursor" to
                    NamedEnums.hunter_crystal_implings.values.filterNotNull().map {
                        ImplingSpawn(it, 1)
                    },
            )
    }
}
