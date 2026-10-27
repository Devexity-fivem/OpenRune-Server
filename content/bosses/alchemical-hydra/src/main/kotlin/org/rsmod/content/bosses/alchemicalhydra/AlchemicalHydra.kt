package org.rsmod.content.bosses.alchemicalhydra

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.script.onEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Alchemical Hydra (npc.hydraboss) in the Karuulm Slayer Dungeon. Fights through
 * four phases as its heads are lost at 75%, 50% and 25% health. Phases one to
 * three start with 75% damage reduction, removed once the hydra is lured over
 * the chemical vent matching the current phase — green for serpentine, blue
 * for electric, red for fire. Signature attacks: a double-hitting ranged spit
 * and a single magic blast; each phase adds a special — poison pools (green,
 * and again in the final phase), lightning strikes (blue) and a flame wall
 * (red). The final phase alternates ranged and magic every attack at a faster
 * rate with empowered max hits.
 * Deviations: the slayer-task requirement, instanced lair and transition
 * animations are not modelled; vents are located by scanning the room's vent
 * locs at spawn and fall back to offsets from the boss spawn tile.
 */
class AlchemicalHydra @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {

    private val bossId by lazy { BOSS.asRSCM(RSCMType.NPC) }

    // Phase name -> vent tile, resolved once per boss from the room's vent locs.
    private val ventTiles = mutableMapOf<Npc, Map<String, CoordGrid>>()

    // Phase names whose vent has already neutralised this boss's reduction.
    private val ventedPhases = mutableMapOf<Npc, MutableSet<String>>()

    override val spec =
        boss(BOSS, BOSS_ELECTRIC, BOSS_FIRE, BOSS_EXTINGUISHED) {
            stats(attackRate = ATTACK_RATE)

            val ranged = mutableMapOf<Int, AbilityRef>()
            val magic = mutableMapOf<Int, AbilityRef>()
            for (stage in 1..4) {
                val enraged = stage == 4
                val rMax = if (enraged) ENRAGED_RANGED_MAX else RANGED_MAX
                val mMax = if (enraged) ENRAGED_MAGIC_MAX else MAGIC_MAX
                ranged[stage] =
                    ability("ranged_$stage") {
                        anim("seq.hydra_stage_${stage}_attack_ranged")
                        projectile(
                            spotanim = "spotanim.hydraboss_ranged_proj",
                            hit = Effect.Hit(damage = (0..rMax).roll(), type = Ranged),
                        )
                        hit {
                            damage(0..rMax).roll()
                            type(Ranged)
                            delay = 1
                        }
                    }
                magic[stage] =
                    ability("magic_$stage") {
                        anim("seq.hydra_stage_${stage}_attack_magic")
                        projectile(
                            spotanim = "spotanim.hydraboss_magic_proj",
                            hit = Effect.Hit(damage = (0..mMax).roll(), type = Magic),
                        )
                    }
            }

            val pools =
                ability("poison_pools") {
                    anim("seq.hydra_stage_1_attack_special")
                    projectile(spotanim = "spotanim.hydraboss_pools_proj")
                    debris(
                        telegraph = "spotanim.hydraboss_pools_proj",
                        damage = (0..POOL_MAX).roll(),
                        type = Typeless,
                        targetRadius = 10,
                        scatterRadius = 3,
                        count = POOL_COUNT,
                    )
                    poison(POISON_DAMAGE, 1, 4)
                }

            val lightning =
                ability("lightning_strike") {
                    anim("seq.hydra_stage_2_attack_special")
                    spotanim("spotanim.hydraboss_lightning_caster")
                    projectile(spotanim = "spotanim.hydraboss_lightning_travel")
                    debris(
                        telegraph = "spotanim.hydraboss_lightning_travel",
                        damage = (0..LIGHTNING_MAX).roll(),
                        type = Typeless,
                        targetRadius = 8,
                        scatterRadius = 1,
                        count = LIGHTNING_COUNT,
                    )
                }

            val flame =
                ability("flame_wall") {
                    anim("seq.hydra_stage_3_attack_special")
                    projectile(spotanim = "spotanim.hydraboss_fire_travel")
                    debris(
                        telegraph = "spotanim.hydraboss_fire",
                        impact = "spotanim.hydraboss_fire_30",
                        damage = (0..FLAME_MAX).roll(),
                        type = Typeless,
                        targetRadius = 10,
                        scatterRadius = 2,
                        count = FLAME_COUNT,
                    )
                }

            val checkVent = ability("check_vent") { include(external(HYDRA_VENT_CHECK)) }

            incoming {
                for (phase in PHASE_VENTS.keys) {
                    rule(Condition.InPhase(phase) and Condition.Not(vented(phase))) {
                        scalePercent(VENTED_DAMAGE_PERCENT)
                    }
                }
            }

            every(VENT_TRY_TICKS, Effect.Run(checkVent.name))

            phase("serpentine") {
                weightedSelectorRandom {
                    +random(ranged.getValue(1), weight = 4)
                    +random(magic.getValue(1), weight = 3)
                }
                forceEveryAttacks(SPECIAL_ATTACK_MIN, SPECIAL_ATTACK_MAX, pools)
            }
            phase("electric", entryHp = PHASE_TWO_HP, transmog = BOSS_ELECTRIC) {
                weightedSelectorRandom {
                    +random(ranged.getValue(2), weight = 4)
                    +random(magic.getValue(2), weight = 3)
                }
                forceEveryAttacks(SPECIAL_ATTACK_MIN, SPECIAL_ATTACK_MAX, lightning)
            }
            phase("fire", entryHp = PHASE_THREE_HP, transmog = BOSS_FIRE) {
                weightedSelectorRandom {
                    +random(ranged.getValue(3), weight = 4)
                    +random(magic.getValue(3), weight = 3)
                }
                forceEveryAttacks(SPECIAL_ATTACK_MIN, SPECIAL_ATTACK_MAX, flame)
            }
            phase(
                "extinguished",
                entryHp = PHASE_FOUR_HP,
                transmog = BOSS_EXTINGUISHED,
                attackRate = ENRAGED_ATTACK_RATE,
            ) {
                rotationSelector {
                    +then(ranged.getValue(4))
                    +then(magic.getValue(4))
                }
                forceEveryAttacks(SPECIAL_ATTACK_MIN, SPECIAL_ATTACK_MAX, pools)
            }
        }

    override fun ScriptContext.startup() {
        BossCombat.register(this, spec, deps)
        deps.extensionRegistry.register(HYDRA_VENT_CHECK) { ctx -> checkVent(ctx.npc) }
        onEvent<NpcStateEvents.Delete> {
            ventTiles.remove(npc)
            ventedPhases.remove(npc)
        }
    }

    private fun vented(phase: String): Condition =
        Condition.Custom { npc, _ -> phase in (ventedPhases[npc] ?: emptySet()) }

    private fun checkVent(boss: Npc) {
        val phase = deps.encounterRegistry.of(boss).currentPhaseName
        val tile = vents(boss)[phase] ?: return
        // Reach rather than strict footprint: the vents are far enough apart that
        // size 6's ambiguity between sw-corner and centre coords never overlaps two.
        if (boss.coords.chebyshevDistance(tile) <= boss.type.size) {
            ventedPhases.getOrPut(boss) { mutableSetOf() } += phase
        }
    }

    private fun vents(boss: Npc): Map<String, CoordGrid> =
        ventTiles.getOrPut(boss) { scanVents(boss) }

    /** Loc names colour a vent only while it spews; uncoloured vents are assigned by position. */
    private fun scanVents(boss: Npc): Map<String, CoordGrid> {
        val centre = ZoneKey.from(boss.spawnCoords)
        val byPhase = mutableMapOf<String, CoordGrid>()
        val uncoloured = mutableListOf<CoordGrid>()
        for (dx in -VENT_ZONE_RADIUS..VENT_ZONE_RADIUS) {
            for (dz in -VENT_ZONE_RADIUS..VENT_ZONE_RADIUS) {
                for (loc in deps.locRepo.findAll(centre.translate(dx, dz))) {
                    val name = RSCM.getReverseMapping(RSCMType.LOC, loc.id)
                    if (!name.contains(VENT_NAME)) continue
                    val phase = PHASE_VENTS.entries.firstOrNull { name.endsWith("_${it.value}") }?.key
                    if (phase != null) byPhase[phase] = loc.coords else uncoloured += loc.coords
                }
            }
        }
        val remaining = PHASE_VENTS.keys - byPhase.keys
        for ((phase, tile) in remaining.zip(uncoloured.sortedWith(compareBy({ it.x }, { it.z })))) {
            byPhase[phase] = tile
        }
        for (phase in remaining - byPhase.keys) {
            val (dx, dz) = FALLBACK_VENTS.getValue(phase)
            byPhase[phase] = boss.spawnCoords.translate(dx, dz)
        }
        return byPhase
    }

    private companion object {
        private const val BOSS = "npc.hydraboss"
        private const val BOSS_ELECTRIC = "npc.hydraboss_2"
        private const val BOSS_FIRE = "npc.hydraboss_3"
        private const val BOSS_EXTINGUISHED = "npc.hydraboss_4"
        private const val HYDRA_VENT_CHECK = "hydra.vent_check"

        private const val ATTACK_RATE = 6
        private const val ENRAGED_ATTACK_RATE = 4
        private const val RANGED_MAX = 17
        private const val ENRAGED_RANGED_MAX = 26
        private const val MAGIC_MAX = 35
        private const val ENRAGED_MAGIC_MAX = 52
        private const val POOL_MAX = 12
        private const val LIGHTNING_MAX = 20
        private const val FLAME_MAX = 30
        private const val POISON_DAMAGE = 6

        private const val PHASE_TWO_HP = 0.75
        private const val PHASE_THREE_HP = 0.50
        private const val PHASE_FOUR_HP = 0.25
        private const val VENTED_DAMAGE_PERCENT = 25

        private const val SPECIAL_ATTACK_MIN = 7
        private const val SPECIAL_ATTACK_MAX = 10
        private const val VENT_TRY_TICKS = 5
        private const val VENT_ZONE_RADIUS = 4
        private const val VENT_NAME = "hydra_room_floor_vent"

        private val POOL_COUNT = 3..5
        private val LIGHTNING_COUNT = 2..3
        private val FLAME_COUNT = 4..6

        // Phase -> vent colour suffix matching the room's geyser vent locs.
        private val PHASE_VENTS =
            linkedMapOf(
                "serpentine" to "green",
                "electric" to "blue",
                "fire" to "red",
            )

        // Approximate vent offsets from the boss spawn tile when the room's vent
        // locs are absent from the map data.
        private val FALLBACK_VENTS =
            mapOf(
                "serpentine" to (-6 to 4),
                "electric" to (6 to 4),
                "fire" to (0 to -6),
            )
    }
}
