package org.rsmod.content.skills.agility

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class CanifisRooftopCourse @Inject constructor(private val marks: MarkOfGrace) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1("loc.rooftops_canifis_start_tree") {
            traverse("seq.human_reachforladder", TREE_TOP, TREE_XP, OBSTACLE_TREE)
        }
        onOpLoc1("loc.rooftops_canifis_jump") { leap(GAP_1_LANDING, GAP_1_XP, OBSTACLE_GAP_1) }
        onOpLoc1("loc.rooftops_canifis_jump_2") { leap(GAP_2_LANDING, GAP_2_XP, OBSTACLE_GAP_2) }
        onOpLoc1("loc.rooftops_canifis_jump_5") { leap(GAP_3_LANDING, GAP_3_XP, OBSTACLE_GAP_3) }
        onOpLoc1("loc.rooftops_canifis_jump_3") { leap(GAP_4_LANDING, GAP_4_XP, OBSTACLE_GAP_4) }
        onOpLoc1("loc.rooftops_canifis_polevault") {
            traverse("seq.agility_shortcut_wall_jump2", POLE_VAULT_LANDING, POLE_VAULT_XP, OBSTACLE_POLE_VAULT)
        }
        onOpLoc1("loc.rooftops_canifis_jump_4") { leap(GAP_5_LANDING, GAP_5_XP, OBSTACLE_GAP_5) }
        onOpLoc1("loc.rooftops_canifis_leapdown") { jumpDown() }
    }

    private suspend fun ProtectedAccess.leap(dest: CoordGrid, xp: Double, index: Int) =
        traverse("seq.agility_shortcut_wall_jumpdown", dest, xp, index)

    private suspend fun ProtectedAccess.traverse(seq: String, dest: CoordGrid, xp: Double, index: Int) {
        if (!canAttempt()) return
        anim(seq)
        delay(MOVE_TICKS)
        telejump(dest)
        statAdvance(AGILITY, xp)
        player.advanceCourse(index)
    }

    private suspend fun ProtectedAccess.jumpDown() {
        if (!canAttempt()) return
        anim("seq.agility_shortcut_wall_jumpdown")
        delay(MOVE_TICKS)
        telejump(FINISH_GROUND)
        statAdvance(AGILITY, GAP_6_XP)
        if (player.completeLap(OBSTACLE_GAP_6)) {
            marks.onLapCompleted(player, MARK_SPAWN, REQUIRED_LVL)
        }
    }

    private fun ProtectedAccess.canAttempt(): Boolean {
        if (!QuestRequirements.hasCompleted(player, PRIEST_IN_PERIL)) {
            mes("You need to complete Priest in Peril to use this course.")
            return false
        }
        if (player.agilityLvl >= REQUIRED_LVL) return true
        mes("You need an Agility level of $REQUIRED_LVL to attempt this.")
        return false
    }

    private companion object {
        const val AGILITY = "stat.agility"
        const val PRIEST_IN_PERIL = "quest_priestinperil"
        const val REQUIRED_LVL = 40
        const val MOVE_TICKS = 2

        const val TREE_XP = 10.0
        const val GAP_1_XP = 8.0
        const val GAP_2_XP = 8.0
        const val GAP_3_XP = 10.0
        const val GAP_4_XP = 8.0
        const val POLE_VAULT_XP = 10.0
        const val GAP_5_XP = 11.0
        const val GAP_6_XP = 175.0

        const val OBSTACLE_TREE = 1
        const val OBSTACLE_GAP_1 = 2
        const val OBSTACLE_GAP_2 = 3
        const val OBSTACLE_GAP_3 = 4
        const val OBSTACLE_GAP_4 = 5
        const val OBSTACLE_POLE_VAULT = 6
        const val OBSTACLE_GAP_5 = 7
        const val OBSTACLE_GAP_6 = 8

        val TREE_TOP = CoordGrid(3506, 3492, 2)
        val GAP_1_LANDING = CoordGrid(3502, 3504, 2)
        val GAP_2_LANDING = CoordGrid(3492, 3504, 2)
        val GAP_3_LANDING = CoordGrid(3478, 3493, 3)
        val GAP_4_LANDING = CoordGrid(3478, 3486, 2)
        val POLE_VAULT_LANDING = CoordGrid(3489, 3476, 3)
        val GAP_5_LANDING = CoordGrid(3510, 3476, 2)
        val FINISH_GROUND = CoordGrid(3510, 3485, 0)
        val MARK_SPAWN = CoordGrid(3500, 3505, 2)
    }
}
