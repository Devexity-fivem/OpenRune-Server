package org.rsmod.content.bosses.bryophyta

import dev.openrune.ServerCacheManager
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.game.entity.Player

// OSRS wiki: growthlings only take damage from an axe or (magic) secateurs.
class Growthling @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {

    override val spec: BossSpec = boss("npc.gb_growthling") {

        stats(attackRate = 4)

        val melee =
            ability("melee") {
                hit {
                    damage(0..4).roll()
                    type(Melee)
                }
            }

        val needTool =
            ability("need_tool") { message("You need an axe or secateurs to harm this growthling.") }

        incoming {
            rule(Condition.Custom { _, target -> target?.hasCuttingTool() != true }) {
                cap(0)
                run(needTool)
            }
        }

        phase("combat") {
            weightedSelectorRandom { +random(melee, weight = 1, requires = WithinMeleeRange) }
        }
    }

    private fun Player.hasCuttingTool(): Boolean {
        val weapon = worn[Wearpos.RightHand.slot] ?: return false
        val type = ServerCacheManager.getItem(weapon.id) ?: return false
        return type.isContentType("content.woodcutting_axe") ||
            type.internalName in CUTTING_OBJS
    }

    private companion object {
        val CUTTING_OBJS = setOf("obj.secateurs", "obj.fairy_enchanted_secateurs")
    }
}
