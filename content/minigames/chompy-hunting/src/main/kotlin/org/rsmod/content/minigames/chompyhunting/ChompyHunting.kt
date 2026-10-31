package org.rsmod.content.minigames.chompyhunting

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.events.interact.HeldDropEvents
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onApLoc1
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onNpcQueueWithArgs
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.script.onOpNpcU
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.isType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class ChompyHunting
@Inject
constructor(
    private val npcRepo: NpcRepository,
    private val objRepo: ObjRepository,
    private val random: GameRandom,
    private val locInteractions: LocInteractions,
) : PluginScript() {

    override fun ScriptContext.startup() {
        locInteractions.setApRange(SWAMP_BUBBLES, SUCK_RANGE)
        onApLoc1(SWAMP_BUBBLES) { fillBellows(it.loc) }
        onOpLocU(SWAMP_BUBBLES, EMPTY_BELLOWS) { fillBellows(it.loc) }
        onOpNpc1(TOAD) { inflateToad(it.npc) }
        onOpNpcU(TOAD_TYPE, BELLOWS_1_TYPE) { inflateToad(it.npc) }
        onOpNpcU(TOAD_TYPE, BELLOWS_2_TYPE) { inflateToad(it.npc) }
        onOpNpcU(TOAD_TYPE, BELLOWS_3_TYPE) { inflateToad(it.npc) }
        onOpHeld3(OGRE_BOW) { checkKills() }
        onOpHeld3(COMP_OGRE_BOW) { checkKills() }
        onOpNpc4(DEAD_CHOMPY) { pluckCarcass(it.npc) }
        onOpLoc1(CHOMPY_CHEST) { openChest(it.loc) }
        onOpNpc1(RANTZ) { rantz(it.npc) }
        onOpNpc1(RANTZ_PRE_QUEST) { rantz(it.npc) }
        onOpNpc1(RANTZ_POST_QUEST) { rantz(it.npc) }
        onOpHeld4(BLOATED_TOAD) { releaseAllToads() }
        onEvent<HeldDropEvents.Release> {
            if (type == BLOATED_TOAD) {
                repeat(obj.count.coerceAtLeast(1)) { releaseToad(player) }
            }
        }
        onNpcQueueWithArgs<Int?>(BLOATED_TOAD_NPC, ATTRACT_QUEUE) { attractChompy(it.args) }
    }

    private suspend fun ProtectedAccess.fillBellows(loc: BoundLocInfo) {
        if (inv.count(EMPTY_BELLOWS) == 0) {
            mes("You need some ogre bellows to suck up the swamp gas.")
            return
        }
        arriveDelay()
        faceLoc(loc)
        anim(BELLOWS_FILL_SEQ)
        delay(1)
        invDel(inv, EMPTY_BELLOWS, 1)
        invAdd(inv, BELLOWS_3, 1)
        mes("You fill the bellows with swamp gas.")
    }

    private suspend fun ProtectedAccess.inflateToad(toad: Npc) {
        val bellows = BELLOWS_CHARGES.firstOrNull { inv.count(it) > 0 }
        if (bellows == null) {
            mes("You need some ogre bellows loaded with swamp gas to inflate the toad.")
            return
        }
        if (inv.count(BLOATED_TOAD) >= MAX_TOADS) {
            mes("You can't carry more than $MAX_TOADS bloated toads.")
            return
        }
        if (inv.isFull()) {
            mes("You don't have enough inventory space.")
            return
        }
        faceEntitySquare(toad)
        anim(BELLOWS_USE_SEQ)
        delay(1)
        invDel(inv, bellows, 1)
        invAdd(inv, BELLOWS_DRAIN.getValue(bellows), 1)
        invAdd(inv, BLOATED_TOAD, 1)
        npcRepo.del(toad, TOAD_RESPAWN_TICKS)
        mes("You inflate the toad with swamp gas.")
    }

    private fun ProtectedAccess.checkKills() {
        val kills = vars[KILLS_VARP]
        mes("You have killed $kills chompy bird${if (kills == 1) "" else "s"} so far.")
    }

    private suspend fun ProtectedAccess.pluckCarcass(carcass: Npc) {
        faceEntitySquare(carcass)
        anim(PLUCK_SEQ)
        delay(1)
        npcRepo.del(carcass, Int.MAX_VALUE)
        statAdvance(HUNTER, PLUCK_XP)
        mes("You start plucking the chompy bird.", ChatType.Spam)
    }

    private suspend fun ProtectedAccess.openChest(loc: BoundLocInfo) {
        faceLoc(loc)
        anim(BELLOWS_USE_SEQ)
        delay(1)
        if (inv.count(EMPTY_BELLOWS) == 0) {
            invAdd(inv, EMPTY_BELLOWS, 1)
            mes("You find some ogre bellows in the chest.")
        } else {
            mes("You already have some ogre bellows.")
        }
    }

    private fun releaseToad(player: Player) {
        val bait = Npc(BLOATED_TOAD_NPC, player.coords)
        npcRepo.add(bait, TOAD_LIFE_TICKS)
        bait.queue(ATTRACT_QUEUE, ATTRACT_INTERVAL, TOAD_LIFE_TICKS - ATTRACT_INTERVAL)
    }

    private fun ProtectedAccess.releaseAllToads() {
        val count = inv.count(BLOATED_TOAD)
        if (count == 0) {
            return
        }
        invDel(inv, BLOATED_TOAD, count)
        repeat(count) { releaseToad(player) }
    }

    private fun StandardNpcAccess.attractChompy(remaining: Int?) {
        val ticks = remaining ?: 0
        if (inMarsh(npc.coords) && random.of(ATTRACT_ODDS) == 0) {
            npcRepo.del(npc, Int.MAX_VALUE)
            npcRepo.add(Npc(CHOMPY_NPC, npc.coords), CHOMPY_LIFE_TICKS)
            return
        }
        if (ticks <= 0) {
            npcRepo.del(npc, Int.MAX_VALUE)
            return
        }
        npc.queue(ATTRACT_QUEUE, ATTRACT_INTERVAL, ticks - ATTRACT_INTERVAL)
    }

    private fun inMarsh(coords: CoordGrid): Boolean =
        coords.level == 0 && MARSHES.any { coords.x in it.xRange && coords.z in it.zRange }

    private suspend fun ProtectedAccess.rantz(npc: Npc) =
        startDialogue(npc) {
            chatNpc(neutral, "Hey yous! Yous wanna help Rantz catch da chompy birdies?")
            when (
                choice3(
                    "What do I need to hunt dem?",
                    1,
                    "I've come for me hatsies!",
                    2,
                    "Nothing, thanks.",
                    3,
                )
            ) {
                1 -> {
                    chatPlayer(quiz, "What do I need to hunt dem?")
                    chatNpc(happy, "Yous need da ogre bow, da ogre bellows and some ogre arrows!")
                    giveGear()
                    chatNpc(
                        neutral,
                        "Suck up da swamp bubbles wiv da bellows, den inflate da toads and " +
                            "drop da bloated toads as bait. Den shoot da birdies!",
                    )
                }
                2 -> {
                    chatPlayer(happy, "I've come for me hatsies!")
                    claimHats()
                }
                else -> chatPlayer(neutral, "Nothing, thanks.")
            }
        }

    private suspend fun Dialogue.giveGear() {
        val hasBellows = (BELLOWS_CHARGES + EMPTY_BELLOWS).any { player.inv.count(it) > 0 }
        if (!hasBellows) {
            access.invAddOrDrop(objRepo, EMPTY_BELLOWS, 1)
        }
        val hasBow =
            player.inv.count(OGRE_BOW) > 0 ||
                player.worn[Wearpos.RightHand.slot]?.isType(OGRE_BOW) == true
        if (!hasBow) {
            access.invAddOrDrop(objRepo, OGRE_BOW, 1)
        }
        if (player.inv.count(OGRE_ARROW) == 0) {
            access.invAddOrDrop(objRepo, OGRE_ARROW, 10)
        }
    }

    private suspend fun Dialogue.claimHats() {
        val kills = vars[KILLS_VARP]
        val unlocked = HATS.filter { kills >= it.first }
        if (unlocked.isEmpty()) {
            chatNpc(angry, "Yous ain't got no hatsies yet! Go kill more chompy birdies!")
            return
        }
        val missing = unlocked.filter { player.inv.count(it.second) == 0 }
        if (missing.isEmpty()) {
            chatNpc(neutral, "Yous already got all da hatsies yous earned!")
            return
        }
        for (hat in missing) {
            access.invAddOrDrop(objRepo, hat.second, 1)
        }
        chatNpc(happy, "Yous earned yerself some hatsies! Well done, runt!")
    }

    internal companion object {
        const val KILLS_VARP = "varp.chompy_kills"

        const val SWAMP_BUBBLES = "loc.swampbubbles"
        const val TOAD = "npc.toad"
        const val DEAD_CHOMPY = "npc.chompybird_dead"
        const val RANTZ = "npc.rantz"
        const val RANTZ_PRE_QUEST = "npc.rantz_pre_quest"
        const val RANTZ_POST_QUEST = "npc.rantz_post_quest"
        const val CHOMPY_CHEST = "loc.chompybird_chest"

        const val EMPTY_BELLOWS = "obj.empty_ogre_bellows"
        const val BELLOWS_1 = "obj.filled_ogre_bellow1"
        const val BELLOWS_2 = "obj.filled_ogre_bellow2"
        const val BELLOWS_3 = "obj.filled_ogre_bellow3"
        const val BLOATED_TOAD = "obj.bloated_toad"
        const val OGRE_BOW = "obj.ogre_bow"
        const val COMP_OGRE_BOW = "obj.zogre_bow"
        const val OGRE_ARROW = "obj.ogre_arrow"

        const val BELLOWS_FILL_SEQ = "seq.human_chompybird_ogrebellows"
        const val BELLOWS_USE_SEQ = "seq.ogre_bellows_use"
        const val PLUCK_SEQ = "seq.human_pickuptable"
        const val HUNTER = "stat.hunter"

        const val ATTRACT_QUEUE = "queue.chompy_attract"
        const val SUCK_RANGE = 3
        const val ATTRACT_INTERVAL = 4
        const val ATTRACT_ODDS = 3
        const val TOAD_LIFE_TICKS = 110
        const val CHOMPY_LIFE_TICKS = 100
        const val TOAD_RESPAWN_TICKS = 30
        const val MAX_TOADS = 3
        const val PLUCK_XP = 30.0

        private val BELLOWS_CHARGES = listOf(BELLOWS_1, BELLOWS_2, BELLOWS_3)
        private val BELLOWS_DRAIN =
            mapOf(BELLOWS_3 to BELLOWS_2, BELLOWS_2 to BELLOWS_1, BELLOWS_1 to EMPTY_BELLOWS)

        private val MARSHES =
            listOf(
                Marsh(2490..2665, 2935..3010),
                Marsh(2300..2430, 3025..3095),
            )

        private val HATS =
            listOf(
                30 to "obj.cbhat1",
                40 to "obj.cbhat2",
                50 to "obj.cbhat3",
                70 to "obj.cbhat4",
                95 to "obj.cbhat5",
                125 to "obj.cbhat6",
                170 to "obj.cbhat7",
                225 to "obj.cbhat8",
                300 to "obj.cbhat9",
                400 to "obj.cbhat10",
                550 to "obj.cbhat11",
                700 to "obj.cbhat12",
                1000 to "obj.cbhat13",
                1300 to "obj.cbhat14",
                1700 to "obj.cbhat15",
                2250 to "obj.cbhat16",
                3000 to "obj.cbhat17",
                4000 to "obj.cbhat18",
            )

        private val HAT_NAMES =
            mapOf(
                30 to "an ogre bowman hat",
                40 to "a bowman hat",
                50 to "an ogre yeoman hat",
                70 to "a yeoman hat",
                95 to "an ogre marksman hat",
                125 to "a marksman hat",
                170 to "an ogre woodsman hat",
                225 to "a woodsman hat",
                300 to "an ogre forester hat",
                400 to "a forester hat",
                550 to "an ogre bowmaster hat",
                700 to "a bowmaster hat",
                1000 to "an ogre expert hat",
                1300 to "an expert hat",
                1700 to "an ogre dragon archer hat",
                2250 to "a dragon archer hat",
                3000 to "an expert ogre dragon archer hat",
                4000 to "an expert dragon archer hat",
            )

        private val TOAD_TYPE =
            checkNotNull(ServerCacheManager.getNpc("npc.toad".asRSCM(RSCMType.NPC)))
        private val BLOATED_TOAD_NPC =
            checkNotNull(ServerCacheManager.getNpc("npc.bloated_toad".asRSCM(RSCMType.NPC)))
        private val CHOMPY_NPC =
            checkNotNull(ServerCacheManager.getNpc("npc.chompybird".asRSCM(RSCMType.NPC)))
        private val BELLOWS_1_TYPE =
            checkNotNull(ServerCacheManager.getItem("obj.filled_ogre_bellow1".asRSCM(RSCMType.OBJ)))
        private val BELLOWS_2_TYPE =
            checkNotNull(ServerCacheManager.getItem("obj.filled_ogre_bellow2".asRSCM(RSCMType.OBJ)))
        private val BELLOWS_3_TYPE =
            checkNotNull(ServerCacheManager.getItem("obj.filled_ogre_bellow3".asRSCM(RSCMType.OBJ)))

        fun hatUnlockedAt(kills: Int): String? = HAT_NAMES[kills]
    }

    private class Marsh(val xRange: IntRange, val zRange: IntRange)
}
