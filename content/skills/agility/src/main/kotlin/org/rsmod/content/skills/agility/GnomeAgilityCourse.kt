package org.rsmod.content.skills.agility

import jakarta.inject.Inject
import org.rsmod.api.config.Constants.em_face_east
import org.rsmod.api.config.Constants.em_face_north
import org.rsmod.api.config.Constants.em_face_south
import org.rsmod.api.config.Constants.em_face_west
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onApLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class GnomeAgilityCourse @Inject constructor(
    private val marks: MarkOfGrace,
    private val npcRepo: NpcRepository,
    private val random: GameRandom,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onApLoc1("loc.gnome_log_balance1") { apCrossLog(it.loc) }
        onApLoc1("loc.log_balance2") { apCrossLog(it.loc) }
        onApLoc1("loc.log_balance3") { apCrossLog(it.loc) }
        onApLoc1("loc.obstical_net2") { apClimbNetUp(it.loc) }
        onApLoc1("loc.climbing_branch") { apClimbBranchUp(it.loc) }
        onApLoc1("loc.balancing_rope") { apCrossRope(it.loc) }
        onApLoc1("loc.balancing_rope_mid") { apCrossRope(it.loc) }
        onApLoc1("loc.climbing_tree") { apClimbBranchDown(it.loc) }
        onApLoc1("loc.climbing_tree2") { apClimbBranchDown(it.loc) }
        onApLoc1("loc.obstical_net3") { apClimbNetOver(it.loc) }
        onApLoc1("loc.obstical_pipe3_1") { apSqueezePipe(it.loc) }
        onApLoc1("loc.obstical_pipe3_2") { apSqueezePipe(it.loc) }
        onOpNpc1(TRAINER_NPC) { talkToTrainer(it.npc) }
    }

    private suspend fun ProtectedAccess.talkToTrainer(npc: Npc) =
        startDialogue(npc) {
            chatNpc(
                verymad,
                "I'm amazed by how much humans chat. The sign over there says " +
                    "training area, not pointless conversation area.",
            )
        }

    private suspend fun ProtectedAccess.apCrossLog(log: BoundLocInfo) {
        if (isWithinApRange(log, AP_RANGE)) crossLog()
    }

    private suspend fun ProtectedAccess.crossLog() {
        if (!checkLevel()) return
        val southbound = coords.z > LOG.z
        val dest = if (southbound) LOG_SOUTH_END else LOG_NORTH_END
        val facing = if (southbound) em_face_south else em_face_north

        spam("You walk carefully across the slippery log...")
        exactMove(coords, dest, delay1 = LOG_START_CYCLES, delay2 = LOG_END_CYCLES, dir = facing)
        anim("seq.human_walk_logbalance")
        delay(LOG_TICKS)
        statAdvance(AGILITY, LOG_XP)
        completeObstacle(OBSTACLE_LOG)
        spam("... and make it safely to the other side.")
    }

    private suspend fun ProtectedAccess.apClimbNetUp(net: BoundLocInfo) {
        if (isWithinApRange(net, AP_RANGE)) climbNetUp()
    }

    private suspend fun ProtectedAccess.climbNetUp() {
        if (!checkLevel()) return
        spam("You climb the netting.")
        anim("seq.human_climbing")
        delay(CLIMB_TICKS)
        telejump(CoordGrid(coords.x, NET_UP_LANDING_Z, 1))
        statAdvance(AGILITY, NET_XP)
        completeObstacle(OBSTACLE_NET_UP)
    }

    private suspend fun ProtectedAccess.apClimbBranchUp(branch: BoundLocInfo) {
        if (isWithinApRange(branch, AP_RANGE)) climbBranchUp()
    }

    private suspend fun ProtectedAccess.climbBranchUp() {
        if (!checkLevel()) return
        spam("You climb the tree...")
        anim("seq.human_climbing")
        delay(CLIMB_TICKS)
        telejump(BRANCH_TOP)
        statAdvance(AGILITY, BRANCH_XP)
        completeObstacle(OBSTACLE_BRANCH_UP)
        spam("... to the platform above.")
    }

    private suspend fun ProtectedAccess.apCrossRope(rope: BoundLocInfo) {
        if (isWithinApRange(rope, AP_RANGE)) crossRope()
    }

    private suspend fun ProtectedAccess.crossRope() {
        if (!checkLevel()) return
        val eastbound = coords.x < ROPE_EAST_END.x
        val dest = if (eastbound) ROPE_EAST_END else ROPE_WEST_END
        val facing = if (eastbound) em_face_east else em_face_west

        spam("You carefully cross the tightrope.")
        exactMove(coords, dest, delay1 = ROPE_START_CYCLES, delay2 = ROPE_END_CYCLES, dir = facing)
        anim("seq.myq3_human_tightrope")
        delay(ROPE_TICKS)
        statAdvance(AGILITY, ROPE_XP)
        completeObstacle(OBSTACLE_ROPE)
    }

    private suspend fun ProtectedAccess.apClimbBranchDown(branch: BoundLocInfo) {
        if (isWithinApRange(branch, AP_RANGE)) climbBranchDown()
    }

    private suspend fun ProtectedAccess.climbBranchDown() {
        if (!checkLevel()) return
        anim("seq.human_climbing_down")
        delay(CLIMB_TICKS)
        telejump(BRANCH_GROUND)
        statAdvance(AGILITY, BRANCH_XP)
        completeObstacle(OBSTACLE_BRANCH_DOWN)
        spam("You land on the ground.")
    }

    private suspend fun ProtectedAccess.apClimbNetOver(net: BoundLocInfo) {
        if (isWithinApRange(net, AP_RANGE)) climbNetOver()
    }

    private suspend fun ProtectedAccess.climbNetOver() {
        if (!checkLevel()) return
        val northbound = coords.z <= NET_OVER_Z
        val landingZ = if (northbound) NET_OVER_NORTH_Z else NET_OVER_SOUTH_Z

        spam("You climb the netting.")
        anim("seq.human_climbing")
        delay(CLIMB_TICKS)
        telejump(CoordGrid(coords.x, landingZ, 0))
        statAdvance(AGILITY, NET_XP)
        completeObstacle(OBSTACLE_NET_OVER)
    }

    private suspend fun ProtectedAccess.apSqueezePipe(pipe: BoundLocInfo) {
        // Only the southern pipe row is enterable; the northern row is inert scenery.
        if (pipe.z == PIPE_ENTRY_Z && isWithinApRange(pipe, PIPE_AP_RANGE)) {
            squeezePipe(pipe.x)
        }
    }

    private suspend fun ProtectedAccess.squeezePipe(pipeX: Int) {
        if (!checkLevel()) return
        anim("seq.human_pipesqueeze")
        exactMove(coords, CoordGrid(pipeX, PIPE_MIDDLE_Z, 0), delay1 = 0, delay2 = PIPE_HOP1_CYCLES, dir = em_face_north)
        delay(PIPE_HOP_TICKS)

        anim("seq.human_pipesqueeze")
        exactMove(coords, CoordGrid(pipeX, PIPE_EXIT_Z, 0), delay1 = 0, delay2 = PIPE_HOP2_CYCLES, dir = em_face_north)
        delay(PIPE_HOP_TICKS)

        statAdvance(AGILITY, PIPE_XP)
        spam("You pull yourself through the pipes.")

        completeObstacle(OBSTACLE_PIPE)
        if (player.courseProgress == OBSTACLE_PIPE) {
            statAdvance(AGILITY, LAP_BONUS_XP)
            player.courseProgress = 0
            marks.onLapCompleted(player, MARK_SPAWN, REQUIRED_LVL)
        }
    }

    // Progress counts only consecutive in-order obstacles; anything else restarts the lap.
    // The log restart keeps progress=1 so a fresh lap always begins cleanly.
    private fun ProtectedAccess.completeObstacle(index: Int) {
        player.courseProgress =
            when {
                index == OBSTACLE_LOG -> OBSTACLE_LOG
                player.courseProgress == index - 1 -> index
                else -> 0
            }
        trainerBark()
    }

    private fun ProtectedAccess.trainerBark() {
        val zone = ZoneKey.from(coords)
        val trainer =
            npcRepo
                .findAll(zone, 1)
                .filter { it.isType(TRAINER_NPC) }
                .filter { it.coords.chebyshevDistance(coords) <= BARK_RANGE }
                .minByOrNull { it.coords.chebyshevDistance(coords) } ?: return
        trainer.say(BARKS[random.of(BARKS.size)])
    }

    private suspend fun ProtectedAccess.checkLevel(): Boolean {
        if (player.agilityLvl >= REQUIRED_LVL) return true
        mes("You need an Agility level of $REQUIRED_LVL to attempt this.")
        return false
    }

    private companion object {
        const val AGILITY = "stat.agility"
        const val TRAINER_NPC = "npc.gnometrainer"

        const val REQUIRED_LVL = 1
        const val AP_RANGE = 2
        const val PIPE_AP_RANGE = 1
        const val BARK_RANGE = 15

        const val LOG_XP = 10.0
        const val NET_XP = 10.0
        const val BRANCH_XP = 6.5
        const val ROPE_XP = 10.0
        const val PIPE_XP = 7.5
        const val LAP_BONUS_XP = 50.0

        const val OBSTACLE_LOG = 1
        const val OBSTACLE_NET_UP = 2
        const val OBSTACLE_BRANCH_UP = 3
        const val OBSTACLE_ROPE = 4
        const val OBSTACLE_BRANCH_DOWN = 5
        const val OBSTACLE_NET_OVER = 6
        const val OBSTACLE_PIPE = 7

        const val LOG_TICKS = 9
        const val CLIMB_TICKS = 2
        const val ROPE_TICKS = 8
        const val PIPE_HOP_TICKS = 2

        const val LOG_START_CYCLES = 15
        const val LOG_END_CYCLES = 270
        const val ROPE_START_CYCLES = 15
        const val ROPE_END_CYCLES = 240
        const val PIPE_HOP1_CYCLES = 90
        const val PIPE_HOP2_CYCLES = 120

        const val NET_UP_LANDING_Z = 3424
        const val NET_OVER_Z = 3426
        const val NET_OVER_NORTH_Z = 3427
        const val NET_OVER_SOUTH_Z = 3425
        const val PIPE_ENTRY_Z = 3431
        const val PIPE_MIDDLE_Z = 3433
        const val PIPE_EXIT_Z = 3437

        val LOG = CoordGrid(2474, 3434, 0)
        val LOG_NORTH_END = CoordGrid(2474, 3436, 0)
        val LOG_SOUTH_END = CoordGrid(2474, 3429, 0)
        val BRANCH_TOP = CoordGrid(2473, 3420, 2)
        val ROPE_EAST_END = CoordGrid(2483, 3420, 2)
        val ROPE_WEST_END = CoordGrid(2477, 3420, 2)
        val BRANCH_GROUND = CoordGrid(2486, 3419, 0)
        val MARK_SPAWN = CoordGrid(2484, 3418, 2)

        val BARKS =
            listOf(
                "That's it, straight up",
                "Come on scaredy cat get across that rope",
                "My granny can move faster than you",
                "move it, move it, move it!",
            )
    }
}

private var Player.courseProgress by intVarp("varp.agility_course_progress")
