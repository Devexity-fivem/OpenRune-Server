package org.rsmod.content.minigames.tearsofguthix

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import kotlin.math.abs
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerTimer
import org.rsmod.api.utils.time.runeday
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.timerAt
import org.rsmod.game.loc.LocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class TearsOfGuthix
@Inject
constructor(
    private val locRepo: LocRepository,
    private val random: GameRandom,
    private val protectedAccess: ProtectedAccessLauncher,
    private val worldClock: MapClock,
) : PluginScript() {

    private val streams = mutableListOf<Stream>()
    private val walls = mutableListOf<Wall>()

    override fun ScriptContext.startup() {
        onOpLoc1(JUNA_1OP) { junaTalk() }
        onOpLoc1(JUNA_2OPS) { junaTalk() }
        onOpLoc2(JUNA_2OPS) { junaStory() }
        onPlayerLogin { ejectStaleSession(player) }
        onPlayerTimer(COLLECT_TIMER) { collectTick() }
    }

    private fun ejectStaleSession(player: Player) {
        val inside = player.coords.level == PLANE &&
            player.coords.x in ROOM_X0..ROOM_X1 &&
            player.coords.z in ROOM_Z0..ROOM_Z1
        if (!inside && player.vars[VB_COLLECTING] == 0) {
            return
        }
        protectedAccess.launch(player) {
            player.clearTimer(COLLECT_TIMER)
            vars[VB_COLLECTING] = 0
            vars[VB_COUNTDOWN] = 0
            if (inside) {
                teleport(EXIT)
            }
        }
    }

    private suspend fun ProtectedAccess.junaTalk() {
        if (vars[VB_COLLECTING] == 1 || inside(player.coords)) {
            return
        }
        if (!QuestRequirements.hasCompleted(player, QUEST_KEY)) {
            startDialogue {
                chatNpcSpecific(
                    "Juna",
                    NPC_JUNA,
                    neutral,
                    "I am Juna, servant of Guthix. I guard this place|and the tears that Guthix shed.",
                )
                chatNpcSpecific(
                    "Juna",
                    NPC_JUNA,
                    neutral,
                    "You are not yet ready to hear the story of the|tears. Come back when you have proven yourself.",
                )
            }
            return
        }
        when (val gate = eligibility()) {
            is Gate.Cooldown -> {
                startDialogue {
                    chatNpcSpecific(
                        "Juna",
                        NPC_JUNA,
                        neutral,
                        "You have already drunk of the tears recently.|Return in ${gate.days} more day${if (gate.days == 1) "" else "s"}.",
                    )
                }
            }
            is Gate.NoProgress -> {
                startDialogue {
                    chatNpcSpecific(
                        "Juna",
                        NPC_JUNA,
                        neutral,
                        "You have not grown since last you drank of the|tears. Go out into the world and gain more|experience, or complete another quest, then return.",
                    )
                }
            }
            Gate.Eligible -> {
                var ready = false
                startDialogue {
                    chatNpcSpecific(
                        "Juna",
                        NPC_JUNA,
                        neutral,
                        "I am Juna, servant of Guthix. I watch over the|tears that fell here long ago.",
                    )
                    chatNpcSpecific(
                        "Juna",
                        NPC_JUNA,
                        neutral,
                        "Tell me a story of your adventures, and I shall|let you collect the tears of Guthix.",
                    )
                    if (choice2("Tell a story.", true, "Not now.", false, title = "Tell Juna a story?")) {
                        beginStory()
                        ready = true
                    }
                }
                if (ready) {
                    enter()
                }
            }
        }
    }

    private suspend fun ProtectedAccess.junaStory() {
        if (!QuestRequirements.hasCompleted(player, QUEST_KEY) ||
            vars[VB_COLLECTING] == 1 ||
            inside(player.coords)
        ) {
            return
        }
        when (eligibility()) {
            is Gate.Cooldown, is Gate.NoProgress -> junaTalk()
            Gate.Eligible -> {
                startDialogue { beginStory() }
                enter()
            }
        }
    }

    private suspend fun Dialogue.beginStory() {
        chatNpcSpecific(
            "Juna",
            NPC_JUNA,
            neutral,
            "Tell me a story, then, of what you have done|since last we spoke.",
        )
        chatPlayer(neutral, "Let me tell you about my latest adventures...")
        delay(1)
        chatNpcSpecific(
            "Juna",
            NPC_JUNA,
            neutral,
            "A fine story. Now listen - the tears of Guthix|flow from the walls of this cave. Collect the|blue tears in your bowl, but beware the green|streams, for they will sour what you have|gathered.",
        )
        access.grantBowl()
        chatNpcSpecific(
            "Juna",
            NPC_JUNA,
            neutral,
            "Go now. You may stay for a time equal to the|quests you have completed.",
        )
    }

    private fun ProtectedAccess.grantBowl() {
        if (vars[VB_JUNA_BOWL] < 2) {
            vars[VB_JUNA_BOWL] = 2
        }
        if (inv.count(BOWL) == 0 && worn.count(BOWL) == 0 && !inv.isFull()) {
            invAdd(inv, BOWL, 1)
        }
    }

    private suspend fun ProtectedAccess.enter() {
        val qp = vars[VARP_QP].coerceAtLeast(1)
        vars[VB_QP_BEFORE_RETURN] = qp
        vars[VB_TEARS] = 0
        vars[VB_COUNTDOWN] = qp
        vars[VB_COLLECTING] = 1
        teleport(ENTRY)
        ifOpenOverlay(SIDEPANEL)
        discoverWalls()
        startStreams()
        player.timerAt(COLLECT_TIMER, worldClock.cycle, 1)
    }

    private suspend fun ProtectedAccess.collectTick() {
        val left = vars[VB_COUNTDOWN] - 1
        if (left <= 0 || vars[VB_COLLECTING] != 1 || !inside(player.coords)) {
            player.clearTimer(COLLECT_TIMER)
            finish()
            return
        }
        vars[VB_COUNTDOWN] = left
        tickStreams()
        tickCollection()
        player.timerAt(COLLECT_TIMER, worldClock.cycle, 1)
    }

    private fun tickStreams() {
        for (stream in streams) {
            stream.ticksLeft--
            if (stream.ticksLeft <= 0) {
                relocate(stream)
            }
        }
    }

    private fun relocate(stream: Stream) {
        swap(stream.wall, "back")
        val free = walls.filter { w -> streams.none { it.wall === w } }
        if (free.isEmpty()) {
            stream.ticksLeft = STREAM_TICKS
            return
        }
        val next = free[random.of(0, free.size - 1)]
        stream.wall = next
        stream.ticksLeft = STREAM_TICKS
        swap(next, stream.kind)
    }

    private fun swap(wall: Wall, kind: String) {
        val target = when (kind) {
            "good" -> "loc.tog_weeping_wall_good_${wall.suffix}"
            "bad" -> "loc.tog_weeping_wall_bad_${wall.suffix}"
            else -> "loc.tog_weeping_wall_back_${wall.suffix}"
        }
        val type = ServerCacheManager.getObject(target.asRSCM(RSCMType.LOC)) ?: return
        locRepo.change(wall.loc, type, Int.MAX_VALUE)
    }

    private fun ProtectedAccess.tickCollection() {
        val p = player.coords
        var delta = 0
        for (stream in streams) {
            val w = stream.wall.loc.coords
            if (abs(p.x - w.x) <= 1 && abs(p.z - w.z) <= 1) {
                delta += if (stream.kind == "good") 1 else -1
            }
        }
        if (delta == 0) {
            return
        }
        vars[VB_TEARS] = (vars[VB_TEARS] + delta).coerceIn(0, 511)
        anim(if (delta > 0) SEQ_COLLECT else SEQ_COLLECT_BAD)
    }

    private suspend fun ProtectedAccess.finish() {
        val tears = vars[VB_TEARS]
        player.clearTimer(COLLECT_TIMER)
        vars[VB_COLLECTING] = 0
        vars[VB_COUNTDOWN] = 0
        ifCloseSub(SIDEPANEL)
        teleport(EXIT)
        anim(SEQ_DRINK)
        if (tears > 0) {
            val skill = lowestSkill()
            statAdvance(skill, tears * xpPerTear(skill))
            mes(SKILL_MESSAGES[skill] ?: "")
            if (tears > vars[VB_MAX_TEARS]) {
                vars[VB_MAX_TEARS] = tears
            }
            vars[VB_STORIES] = (vars[VB_STORIES] + 1).coerceAtMost(15)
        }
        persistPlayed()
        mes("Juna takes the bowl from you as you drink the collected tears.")
    }

    private fun ProtectedAccess.xpPerTear(stat: String): Double =
        (10.0 + player.statMap.getXP(stat) / 27 / 10.0).coerceAtMost(60.0)

    private fun ProtectedAccess.persistPlayed() {
        vars[VP_LAST_DAY] = runeday()
        vars[VP_LAST_XP] = totalXp()
        vars[VP_LAST_QP] = vars[VARP_QP]
    }

    private fun ProtectedAccess.eligibility(): Gate {
        val lastDay = vars[VP_LAST_DAY]
        if (lastDay == 0) {
            return Gate.Eligible
        }
        val daysLeft = COOLDOWN_DAYS - (runeday() - lastDay)
        if (daysLeft > 0) {
            return Gate.Cooldown(daysLeft)
        }
        val xpGained = totalXp() - vars[VP_LAST_XP]
        val qpGained = vars[VARP_QP] - vars[VP_LAST_QP]
        return if (xpGained >= XP_REQUIRED || qpGained >= 1) Gate.Eligible else Gate.NoProgress
    }

    private fun discoverWalls() {
        if (walls.isNotEmpty()) {
            return
        }
        for (x in ROOM_X0..ROOM_X1) {
            for (z in ROOM_Z0..ROOM_Z1) {
                for (loc in locRepo.findAll(CoordGrid(x, z, PLANE))) {
                    val name = locName(loc) ?: continue
                    if (!name.startsWith("tog_weeping")) {
                        continue
                    }
                    walls.add(Wall(loc, if (loc.angleId == 1) "r" else "l"))
                }
            }
        }
    }

    private fun startStreams() {
        if (streams.isNotEmpty() || walls.size < STREAM_COUNT) {
            return
        }
        val order = walls.sortedBy { random.of(0, Int.MAX_VALUE - 1) }
        val kinds = listOf("good", "good", "good", "bad", "bad", "bad")
        for (i in 0 until STREAM_COUNT) {
            val wall = order[i]
            streams.add(Stream(wall, kinds[i], STREAM_TICKS - i * STAGGER_TICKS))
            swap(wall, kinds[i])
        }
    }

    private fun inside(coords: CoordGrid): Boolean =
        coords.level == PLANE && coords.x in ROOM_X0..ROOM_X1 && coords.z in ROOM_Z0..ROOM_Z1

    private fun locName(loc: LocInfo): String? =
        runCatching { RSCM.getReverseMapping(RSCMType.LOC, loc.id) }
            .getOrNull()
            ?.substringAfterLast('.')

    private fun ProtectedAccess.lowestSkill(): String =
        SKILLS.filter { skillUnlocked(it) }.minByOrNull { player.statMap.getXP(it) }
            ?: "stat.hitpoints"

    private fun ProtectedAccess.skillUnlocked(stat: String): Boolean = when (stat) {
        "stat.runecrafting" -> QuestRequirements.hasCompleted(player, "quest_runemysteries")
        "stat.herblore" -> QuestRequirements.hasCompleted(player, "quest_druidicritual")
        else -> true
    }

    private fun ProtectedAccess.totalXp(): Int = SKILLS.sumOf { player.statMap.getXP(it) }

    private data class Wall(val loc: LocInfo, val suffix: String)

    private data class Stream(var wall: Wall, val kind: String, var ticksLeft: Int)

    private sealed interface Gate {
        data object Eligible : Gate
        data class Cooldown(val days: Int) : Gate
        data object NoProgress : Gate
    }

    private companion object {
        private const val QUEST_KEY = "quest_tearsofguthix"
        private const val JUNA_1OP = "loc.tog_juna_1op"
        private const val JUNA_2OPS = "loc.tog_juna_2ops"
        private const val NPC_JUNA = "npc.tog_juna_dummy"
        private const val SIDEPANEL = "interface.tog_sidepanel"
        private const val BOWL = "obj.tog_bowl"
        private const val SEQ_COLLECT = "seq.tog_lean_forward_bowl"
        private const val SEQ_COLLECT_BAD = "seq.tog_lean_back_bowl"
        private const val SEQ_DRINK = "seq.tog_drink_bowl"

        private const val VB_JUNA_BOWL = "varbit.tog_juna_bowl"
        private const val VB_STORIES = "varbit.tog_juna_stories"
        private const val VB_COLLECTING = "varbit.tog_minigame_collecting"
        private const val VB_TEARS = "varbit.tog_tears_collected"
        private const val VB_MAX_TEARS = "varbit.tog_max_tears_collected"
        private const val VB_COUNTDOWN = "varbit.tog_countdown"
        private const val VB_QP_BEFORE_RETURN = "varbit.tog_qp_before_return"
        private const val VARP_QP = "varp.qp"
        private const val COLLECT_TIMER = "timer.tog_collect"

        private const val VP_LAST_DAY = "varp.tog_last_played_day"
        private const val VP_LAST_XP = "varp.tog_last_played_xp"
        private const val VP_LAST_QP = "varp.tog_last_played_qp"

        private const val STREAM_COUNT = 6
        private const val STREAM_TICKS = 16
        private const val STAGGER_TICKS = 2
        private const val XP_REQUIRED = 100_000
        private const val COOLDOWN_DAYS = 7
        private const val PLANE = 2

        private val ENTRY = CoordGrid(2, 50, 148, 56, 45)
        private val EXIT = CoordGrid(2, 50, 148, 52, 45)

        private const val ROOM_X0 = 3254
        private const val ROOM_X1 = 3263
        private const val ROOM_Z0 = 9512
        private const val ROOM_Z1 = 9521

        private val SKILLS = listOf(
            "stat.attack", "stat.strength", "stat.ranged", "stat.magic",
            "stat.defence", "stat.hitpoints", "stat.prayer", "stat.agility",
            "stat.herblore", "stat.thieving", "stat.crafting", "stat.runecrafting",
            "stat.mining", "stat.smithing", "stat.fishing", "stat.cooking",
            "stat.firemaking", "stat.woodcutting", "stat.fletching", "stat.slayer",
            "stat.farming", "stat.construction", "stat.hunter",
        )

        private val SKILL_MESSAGES = mapOf(
            "stat.attack" to "You feel a brief surge of aggression!",
            "stat.strength" to "Your muscles bulge!",
            "stat.defence" to "You feel very defensive!",
            "stat.ranged" to "Your aim improves.",
            "stat.prayer" to "You suddenly feel very close to the gods.",
            "stat.magic" to "You feel magical power coursing through your body.",
            "stat.hitpoints" to "You feel more healthy.",
            "stat.agility" to "You feel very nimble.",
            "stat.herblore" to "You gain a deep understanding of all kinds of strange plants.",
            "stat.thieving" to "You feel your respect for others' property slipping away.",
            "stat.crafting" to "Your fingers feel nimble and suited to delicate work.",
            "stat.fletching" to "You gain a deep understanding of wooden sticks.",
            "stat.mining" to "You gain a deep understanding of the stones of the earth.",
            "stat.smithing" to "You gain a deep understanding of metal.",
            "stat.fishing" to "You gain a deep understanding of the creatures of the sea.",
            "stat.cooking" to "You have a brief urge to cook some food.",
            "stat.firemaking" to "You have a brief urge to set light to something!",
            "stat.woodcutting" to "You gain a deep understanding of the trees in the forest.",
            "stat.runecrafting" to "You gain a deep understanding of runes.",
            "stat.slayer" to "You gain a deep understanding of many strange creatures.",
            "stat.farming" to "You gain a deep understanding of the cycles of nature.",
            "stat.construction" to "You feel homesick.",
            "stat.hunter" to "You briefly experience the joy of the hunt.",
        )
    }
}
