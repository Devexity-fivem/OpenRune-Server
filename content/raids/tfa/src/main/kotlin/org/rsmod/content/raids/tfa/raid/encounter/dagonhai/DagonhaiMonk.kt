package org.rsmod.content.raids.tfa.raid.encounter.dagonhai

import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.bosses.spec.ProjectileConfig
import org.rsmod.content.raids.tfa.raid.encounter.dagonhai.DagonhaiAbilities.COMBAT
import org.rsmod.content.raids.tfa.raid.encounter.dagonhai.DagonhaiAbilities.EMPOWERED
import org.rsmod.content.raids.tfa.raid.encounter.dagonhai.DagonhaiAbilities.RITUAL
import org.rsmod.content.raids.tfa.raid.encounter.dagonhai.DagonhaiVarns.SLOT
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

class DagonhaiMonk @Inject constructor(deps: BossDeps, private val fights: DagonhaiEncounter) :
    BossPluginScript(deps) {

    override fun ScriptContext.startup() {
        BossCombat.register(
            this,
            spec,
            deps,
            onModifyHit = { fights.of(npc)?.modifyTrioHit(this) },
            onHit = { fights.of(npc)?.let { it.impactTrio(this); it.updateBar() } },
        )
    }

    private fun laneTiles(npc: Npc, slot: Int): List<CoordGrid> =
        fights.of(npc)?.laneTiles(npc.vars[SLOT]) ?: emptyList()

    private fun cauldronTile(npc: Npc): CoordGrid =
        fights.of(npc)?.cauldronCoords() ?: npc.coords

    override val spec: BossSpec =
        boss(MONK_N, MONK_N_UNSHIELDED, MONK_S, MONK_S_UNSHIELDED) {
            stats(attackRate = 1)

            val arena = DagonhaiArena.AREA
            val books = intArrayOf(1, 2)

            fun laneTelegraph(god: String, damage: Int): Effect =
                switch(
                    SLOT,
                    listOf(
                        Effect.NoOp,
                        tileAoE(
                            tiles = { npc, _ -> laneTiles(npc, DagonhaiArena.SLOT_NORTH) },
                            telegraph =
                                telegraph(
                                    "spotanim.vfx_dagonhai_throw_book_floor_${god}_south_01",
                                    windup = TOME_LANE_TICKS,
                                ),
                            damage = (0..damage).roll(),
                            type = Magic,
                        ),
                        tileAoE(
                            tiles = { npc, _ -> laneTiles(npc, DagonhaiArena.SLOT_SOUTH) },
                            telegraph =
                                telegraph(
                                    "spotanim.vfx_dagonhai_throw_book_floor_${god}_north_01",
                                    windup = TOME_LANE_TICKS,
                                ),
                            damage = (0..damage).roll(),
                            type = Magic,
                        ),
                    ),
                )

            fun tome(god: Int, blockMax: Int): Effect {
                val name = DagonhaiAbilities.godName(god)
                return sequence(
                    transitionTo(RITUAL),
                    say("What have we here?"),
                    switch(
                        SLOT,
                        listOf(Effect.NoOp) + books.map { anim("seq.npc_dagonhai_search_books_idle_0$it") },
                    ),
                    delay(SEARCH_TICKS),
                    say(DagonhaiAbilities.GOD_QUOTES[god]),
                    faceTile(customTile { npc, _ -> cauldronTile(npc) }),
                    switch(
                        SLOT,
                        listOf(Effect.NoOp) + books.map { anim("seq.npc_dagonhai_throw_book_${name}_0$it") },
                    ),
                    laneTelegraph(name, blockMax),
                    projectile(
                        spotanim = "spotanim.vfx_dagonhai_throw_book_projectile_${name}_01",
                        config =
                            ProjectileConfig.fixed(
                                PROJ_START_HEIGHT,
                                PROJ_END_HEIGHT,
                                TOME_DELAY,
                                TOME_FLIGHT,
                                PROJ_ANGLE,
                                PROJ_PROGRESS,
                            ),
                        target = customTile { npc, _ -> cauldronTile(npc) },
                    ),
                    after(TOME_LANE_TICKS, external("dagon.tome_$name")),
                )
            }

            ability("tome_zamorak", tome(DagonhaiArena.GOD_ZAMORAK, DagonhaiArena.ZAMORAK_BLOCK_MAX))
            ability(
                "tome_saradomin",
                tome(DagonhaiArena.GOD_SARADOMIN, DagonhaiArena.SARADOMIN_BOLT_MAX),
            )
            ability("tome_bandos", tome(DagonhaiArena.GOD_BANDOS, DagonhaiArena.BANDOS_BLOCK_MAX))

            ability("channel", sequence(*DagonhaiAbilities.channelEffects().toTypedArray()))
            ability("channel_end", sequence(*DagonhaiAbilities.channelEndEffects().toTypedArray()))
            ability(
                "channel_stop",
                sequence(*DagonhaiAbilities.channelStopEffects().toTypedArray()),
            )
            ability("restore", sequence(*DagonhaiAbilities.restoreEffects().toTypedArray()))
            ability("empower", sequence(*DagonhaiAbilities.empowermentEffects().toTypedArray()))
            ability("barrage", DagonhaiAbilities.barrage(arena))
            ability("settle", transitionTo(DESK))
            ability("awaken", transitionTo(COMBAT))

            phase(DESK, scriptedMovement = true, lockMovement = true) {}
            phase(COMBAT, scriptedMovement = true, lockMovement = true) {}
            phase(RITUAL, scriptedMovement = true, lockMovement = true) {}
            phase(EMPOWERED, scriptedMovement = true, lockMovement = true) {
                weightedSelectorRandom { +random("barrage", weight = 1) }
            }
        }

    private companion object {
        const val MONK_N = "npc.tfa_dagonhai_minion_1"
        const val MONK_N_UNSHIELDED = "npc.tfa_dagonhai_minion_1_unshielded"
        const val MONK_S = "npc.tfa_dagonhai_minion_2"
        const val MONK_S_UNSHIELDED = "npc.tfa_dagonhai_minion_2_unshielded"
        const val DESK = "desk"

        const val SEARCH_TICKS = 2
        const val TOME_LANE_TICKS = 4

        const val PROJ_START_HEIGHT = 470
        const val PROJ_END_HEIGHT = 120
        const val PROJ_ANGLE = 0
        const val PROJ_PROGRESS = 160
        const val TOME_DELAY = 20
        const val TOME_FLIGHT = 40
    }
}
