package org.rsmod.content.bosses.questbosses

import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.runtime.encounter
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// Troll Stronghold — Dad: heavy melee up to ~28.
class Dad @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.fd_troll_dad") {
        stats(attackRate = 8)
        val melee = ability("melee") {
            anim("seq.giant_attack")
            hit { damage(0..27).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// Troll Romance — Arrg: melee up to ~15.
class Arrg @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.trollromance_arrg_attackable") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.troll_attack")
            hit { damage(0..38).roll(); type(Melee) }
        }
        val ranged = ability("ranged") {
            anim("seq.troll_attack")
            projectile(
                spotanim = "spotanim.myarm_rock_roc_travel",
                hit = Effect.Hit(damage = Roll(0..30), type = Ranged),
            )
        }
        phase("combat") {
            weightedSelectorRandom {
                +random(melee, weight = 2, requires = WithinMeleeRange)
                +random(ranged, weight = 1)
            }
        }
    }
}

// My Arm's Big Adventure — Giant Roc: melee up to ~16.
class GiantRoc @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.myarm_giant_roc") {
        stats(attackRate = 6)
        val melee = ability("melee") {
            anim("seq.giant_attack")
            hit { damage(0..14).roll(); type(Melee) }
        }
        val ranged = ability("ranged") {
            anim("seq.giant_attack")
            projectile(
                spotanim = "spotanim.myarm_rock_roc_travel",
                hit = Effect.Hit(damage = Roll(0..20), type = Ranged),
            )
        }
        phase("combat") {
            weightedSelectorRandom {
                +random(melee, weight = 2, requires = WithinMeleeRange)
                +random(ranged, weight = 1)
            }
        }
    }
}

// One Small Favour — Slagilith: melee up to ~11.
class Slagilith @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.slagilith") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.golem_attack")
            hit { damage(0..11).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// Grim Tales — Glod: melee up to ~20.
class Glod @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.grim_glod") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.giant_attack")
            hit { damage(0..25).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// The Fremennik Isles — Ice Troll King: melee up to ~15.
class IceTrollKing @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.fris_troll_king_true") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.troll_attack")
            hit { damage(0..21).roll(); type(Melee) }
        }
        val magic = ability("magic") {
            anim("seq.troll_attack")
            projectile(
                spotanim = "spotanim.ice_barrage_travel",
                hit = Effect.Hit(damage = Roll(0..11), type = Magic),
            )
        }
        val ranged = ability("ranged") {
            anim("seq.troll_attack")
            projectile(
                spotanim = "spotanim.myarm_rock_roc_travel",
                hit = Effect.Hit(damage = Roll(0..7), type = Ranged),
            )
        }
        phase("combat") {
            weightedSelectorRandom {
                +random(melee, weight = 3, requires = WithinMeleeRange)
                +random(magic, weight = 1)
                +random(ranged, weight = 1)
            }
        }
    }
}

// Fight Arena — General Khazard: melee up to ~9.
class GeneralKhazard @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.general_khazard_arena") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.human_sword_slash")
            hit { damage(0..9).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// Monkey Madness — Jungle Demon: melee up to ~32 plus magic when out of reach.
class JungleDemon @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.mm_demon") {
        stats(attackRate = 6)
        val melee = ability("melee") {
            anim("seq.demon_attack")
            hit { damage(0..32).roll(); type(Melee) }
        }
        val magic = ability("magic") {
            anim("seq.demon_attack")
            projectile(spotanim = "spotanim.firebolt_travel", hit = Effect.Hit(damage = Roll(0..26), type = Magic))
        }
        phase("combat") {
            weightedSelectorRandom {
                +random(melee, weight = 3, requires = WithinMeleeRange)
                +random(magic, weight = 2)
            }
        }
    }
}

// Fremennik Trials — Koschei the deathless: four forms; each "kill" revives
// him at full health in a stronger form, the last attacking every tick.
class Koschei @Inject constructor(private val deps: BossDeps) : PluginScript() {

    val spec: BossSpec =
        boss("npc.vikingexile_koschei_battle") {
            stats(attackRate = 4)
            val melee = ability("melee") {
                anim("seq.human_unarmedpunch")
                hit { damage(0..14).roll(); type(Melee) }
            }
            for (form in 1..4) {
                phase("form$form", attackRate = if (form == 4) 1 else 4) {
                    weightedSelectorRandom { +random(melee, weight = 1) }
                }
            }
        }

    override fun ScriptContext.startup() {
        BossCombat.register(this, spec, deps, onModifyHit = { onReviveHit(this) })
    }

    private fun onReviveHit(event: NpcHitEvents.Modify) {
        if (!event.hit.isFromPlayer) return
        val npc = event.npc
        if (event.hit.damage < npc.hitpoints) return
        val form = npc.vars[FORM_VARN]
        if (form >= 3) return
        event.hit.damage = npc.hitpoints - 1
        npc.vars[FORM_VARN] = form + 1
        deps.worldQueues.add(1) { revive(npc, "form${form + 2}") }
    }

    private fun revive(npc: Npc, phase: String) {
        npc.hitpoints = npc.baseHitpointsLvl
        deps.encounter(npc).transitionTo(phase, deps.mapClock.cycle)
        npc.say("You cannot defeat Koschei the Deathless!")
    }

    private companion object {
        const val FORM_VARN = "varn.generic_state_2"
    }
}

// Fremennik Trials — The Draugen: melee up to ~8.
class Draugen @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.viking_draugen") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.ghost_attack")
            hit { damage(0..7).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}

// Olaf's Quest — Ulfric: melee up to ~11.
class Ulfric @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec = boss("npc.olaf2_ulfric") {
        stats(attackRate = 4)
        val melee = ability("melee") {
            anim("seq.skeleton_attack")
            hit { damage(0..11).roll(); type(Melee) }
        }
        phase("combat") { weightedSelectorRandom { +random(melee, weight = 1) } }
    }
}
