package org.rsmod.content.quest.area.falador.piratestreasure

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import dev.openrune.types.NpcServerType
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.npc.queueCombatRetaliate
import org.rsmod.api.config.constants
import org.rsmod.api.player.hook.SpadeDigHook
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpcU
import org.rsmod.api.shops.Shops
import org.rsmod.content.quest.area.falador.piratestreasure.PiratesTreasureDialogue.crateBananas
import org.rsmod.content.quest.area.falador.piratestreasure.PiratesTreasureDialogue.crateRum
import org.rsmod.content.quest.area.falador.piratestreasure.PiratesTreasureDialogue.employed
import org.rsmod.content.quest.area.falador.piratestreasure.PiratesTreasureDialogue.employedFoodStore
import org.rsmod.content.quest.area.falador.piratestreasure.PiratesTreasureDialogue.employedPlantation
import org.rsmod.content.quest.area.falador.piratestreasure.PiratesTreasureDialogue.frankDialogue
import org.rsmod.content.quest.area.falador.piratestreasure.PiratesTreasureDialogue.frankHandRum
import org.rsmod.content.quest.area.falador.piratestreasure.PiratesTreasureDialogue.gardenerProvoked
import org.rsmod.content.quest.area.falador.piratestreasure.PiratesTreasureDialogue.luthasDialogue
import org.rsmod.content.quest.area.falador.piratestreasure.PiratesTreasureDialogue.zemboDialogue
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.isType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import dev.openrune.util.Wearpos
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.ScriptContext

class PiratesTreasure
@Inject
constructor(
    private val locRepo: LocRepository,
    private val npcRepo: NpcRepository,
    private val shops: Shops,
) : QuestScript(KEY, "varp.hunt", rewards {}, ItemRewardDisplay("obj.emerald")), SpadeDigHook {

    override fun ScriptContext.init() {
        check(quest.maxSteps == StageComplete) {
            "Pirate's Treasure end state is ${quest.maxSteps} in the cache dbrow, " +
                "but the treasure dig writes $StageComplete."
        }

        onOpNpc1("npc.redbeard_frank") { frankDialogue(it.npc) }
        onOpNpcU(npcType("npc.redbeard_frank"), objType(KaramjaRum)) {
            useRumOnFrank(it.npc)
        }

        onOpNpc1("npc.luthas") { luthasDialogue(it.npc) }
        onOpNpc1("npc.zembo") { zemboDialogue(it.npc, shops) }
        onOpNpc3("npc.zembo") { openBoozeshop(it.npc) }

        onOpLoc1(BananaCrate) { describeBananaCrate() }
        onOpLoc2(BananaCrate) { fillBananaCrate() }
        onOpLocU(BananaCrate, objType("obj.banana")) { packBanana() }
        onOpLocU(BananaCrate, objType(KaramjaRum)) { stashRum() }

        onOpLoc1(GroceryCrate) { searchGroceryCrate() }
        onOpLoc1(WydinDoor) { openWydinDoor(it.loc) }

        onOpLoc1(PirateChest) { lockedPirateChest() }
        onOpLocU(PirateChest, objType(ChestKey)) { unlockPirateChest(it.loc) }

        onOpHeld1(PirateMessage) { readPirateMessage() }
    }

    private suspend fun ProtectedAccess.useRumOnFrank(npc: Npc) {
        if (quest.getQuestStage(player) != StageFetchRum) {
            startDialogue(npc) {
                chatNpc(happy, "Thanks fer the thought, but I still have to finish this bottle.")
            }
            return
        }
        startDialogue(npc) {
            chatPlayer(happy, "Frank, I have some Karamja Rum.")
            frankHandRum()
        }
    }

    private suspend fun ProtectedAccess.describeBananaCrate() {
        arriveDelay()
        val bananas = player.crateBananas
        val rum = player.crateRum
        if (bananas == 0 && rum == RumNone) {
            mes("The crate is completely empty.")
        } else if (bananas == 0) {
            mes("There is some rum in here, although with no bananas to cover it.")
            mes("It is a little obvious.")
        } else {
            if (bananas == CrateFull) {
                mes("The crate is full of bananas.")
            } else {
                val pluralised = if (bananas == 1) "1 banana" else "$bananas bananas"
                mes("The crate has $pluralised inside.")
            }
            if (rum == RumPlantation) {
                mes("There is also some rum stashed in here too.")
            }
        }
    }

    private suspend fun ProtectedAccess.fillBananaCrate() {
        arriveDelay()
        if (!player.employedPlantation) {
            mes("Why would I want to do that?")
            return
        }
        val carried = invTotal(inv, Banana)
        if (player.crateBananas == CrateFull) {
            mes("The crate is already full.")
            return
        }
        if (carried == 0) {
            mes("You have no bananas to pack into the crate.")
            return
        }
        val packing = minOf(carried, CrateFull - player.crateBananas)
        invDel(inv, Banana, count = packing)
        player.crateBananas += packing
        anim("seq.human_pickuptable")
        soundSynth("synth.put_down")
        mesbox(
            if (packing == 1) "You pack a banana into the crate."
            else "You pack $packing bananas into the crate."
        )
    }

    private suspend fun ProtectedAccess.packBanana() {
        if (!player.employedPlantation) {
            mes("Why would I want to do that?")
            return
        }
        if (player.crateBananas == CrateFull) {
            mes("The crate is already full.")
            return
        }
        if (invDel(inv, Banana, count = 1).failure) {
            return
        }
        player.crateBananas += 1
        anim("seq.human_pickuptable")
        soundSynth("synth.put_down")
        mesbox("You pack a banana into the crate.")
    }

    private suspend fun ProtectedAccess.stashRum() {
        if (!player.employedPlantation) {
            mes("Why would I want to do that?")
            return
        }
        if (quest.getQuestStage(player) != StageFetchRum) {
            mes("I see no reason to do that.")
            return
        }
        when (player.crateRum) {
            RumFoodStore -> {
                mes(
                    "There is already some rum in Wydin's store, I should go and get that " +
                        "first."
                )
            }
            RumPlantation -> mes("There's already some rum in here...")
            else -> {
                if (invDel(inv, KaramjaRum, count = 1).failure) {
                    return
                }
                player.crateRum = RumPlantation
                anim("seq.human_pickuptable")
                soundSynth("synth.put_down")
                mesbox("You stash the rum in the crate.")
            }
        }
    }

    private suspend fun ProtectedAccess.searchGroceryCrate() {
        arriveDelay()
        mes("There are a lot of bananas in the crate.")
        delay(3)
        if (player.crateRum == RumFoodStore) {
            mes("You find your bottle of rum amongst the bananas.")
            anim("seq.human_pickuptable")
            player.crateRum = RumNone
            invAdd(inv, KaramjaRum)
            soundSynth("synth.pick2")
            delay(1)
        }
        startDialogue {
            val takeBanana =
                choice2(
                    "Yes.",
                    true,
                    "No",
                    false,
                    title = "Do you want to take a banana?",
                )
            if (takeBanana) {
                access.mes("You take a banana.")
                access.anim("seq.human_pickuptable")
                access.soundSynth("synth.pick2")
                access.invAdd(access.inv, Banana)
            }
        }
    }

    private suspend fun ProtectedAccess.openWydinDoor(loc: BoundLocInfo) {
        arriveDelay()
        val entering = loc.enteringFrom(player.coords)
        val wydin = findWydin()
        if (entering && wydin != null) {
            if (!player.employedFoodStore) {
                startDialogue(wydin) {
                    chatNpc(
                        neutral,
                        "Hey, you can't go in there. Only employees of the grocery " +
                            "store can go in.",
                    )
                    chatPlayer(neutral, "Sorry, I didn't realise.")
                }
                return
            }
            if (player.worn[Wearpos.Torso.slot]?.isType(WhiteApron) != true) {
                startDialogue(wydin) {
                    chatNpc(
                        neutral,
                        "Can you put your apron on before going in there, please?",
                    )
                }
                return
            }
        }
        passThroughDoor(loc, entering)
    }

    private fun BoundLocInfo.enteringFrom(player: CoordGrid): Boolean =
        when (angle) {
            LocAngle.North,
            LocAngle.South -> player.z == coords.z
            else -> player.x == coords.x
        }

    private fun BoundLocInfo.throughCoords(): CoordGrid =
        when (shape) {
            LocShape.WallStraight ->
                when (angle) {
                    LocAngle.West -> coords.translateX(-1)
                    LocAngle.North -> coords.translateZ(1)
                    LocAngle.East -> coords.translateX(1)
                    else -> coords.translateZ(-1)
                }
            LocShape.WallDiagonal ->
                when (angle) {
                    LocAngle.West -> coords.translateZ(1)
                    LocAngle.North -> coords.translateX(1)
                    LocAngle.East -> coords.translateZ(-1)
                    else -> coords.translateX(-1)
                }
            else -> coords
        }

    private suspend fun ProtectedAccess.passThroughDoor(loc: BoundLocInfo, entering: Boolean) {
        val through = loc.throughCoords()
        val dest =
            if (entering) {
                if (player.coords != loc.coords) {
                    teleport(loc.coords)
                    delay(1)
                }
                through
            } else {
                loc.coords
            }
        teleport(dest)
        locRepo.change(loc, Inviswall, DoorTicks)
        locRepo.add(through, WydinDoorOpen, DoorTicks, loc.turnAngle(1), loc.shape)
        soundSynth("synth.door_open")
    }

    private fun findWydin(): Npc? =
        npcRepo
            .findAll(ZoneKey.from(WydinSpawn), zoneRadius = 1)
            .firstOrNull { it.isType("npc.wydin") }

    private suspend fun ProtectedAccess.lockedPirateChest() {
        arriveDelay()
        mes("The chest is locked.")
        soundSynth("synth.locked")
    }

    private suspend fun ProtectedAccess.unlockPirateChest(loc: BoundLocInfo) {
        delay(1)
        locRepo.change(loc, PirateChestOpen, ChestOpenTicks)
        soundSynth("synth.chest_open")
        mes("All that's in the chest is a message...")
        delay(7)
        mes("You take the message from the chest.")
        invDel(inv, ChestKey, count = 1)
        invAdd(inv, PirateMessage)
    }

    private suspend fun ProtectedAccess.readPirateMessage() {
        if (quest.getQuestStage(player) == StageGotKey) {
            quest.setQuestStage(this, StageReadMessage)
        }
        ifOpenMainModal("interface.letterscroll")
        for (index in 0 until LetterScrollLines) {
            ifSetText("component.letterscroll:lj${index + 1}", PirateMessageLines[index])
        }
    }

    private suspend fun ProtectedAccess.openBoozeshop(npc: Npc) {
        shops.open(
            player = player,
            title = "Karamja Wines, Spirits, and Beers.",
            shopInv = "inv.boozeshop",
            buyPercentage = 70.0,
            sellPercentage = 100.0,
            changePercentage = 2.0,
        )
    }

    override fun claims(player: Player): Boolean =
        player.coords == DigSpot && quest.getQuestStage(player) == StageReadMessage

    private val gardenerType: NpcServerType by lazy {
        checkNotNull(ServerCacheManager.getNpc(FaladorGardener.asRSCM(RSCMType.NPC))) {
            "No npc type '$FaladorGardener' resolved."
        }
    }

    override suspend fun ProtectedAccess.beforeDig(): Boolean {
        if (player.gardenerProvoked == 0) {
            provokeGardener()
            player.gardenerProvoked = 1
            return false
        }
        val gardeners = findQuestGardeners().toList()
        if (gardeners.any { it.isHunting(player) }) {
            mes("I can't dig up anything with him attacking me!")
            return false
        }
        gardeners.forEach { npcRepo.del(it, Int.MAX_VALUE) }
        mes("You dig a hole in the ground...")
        return true
    }

    override suspend fun ProtectedAccess.dig() {
        delay(2)
        mes("and find a little chest of treasure.")
        invAdd(inv, "obj.coins", count = 450)
        invAdd(inv, "obj.gold_ring")
        invAdd(inv, "obj.emerald")
        player.gardenerProvoked = 0
        quest.completeQuest(this)
    }

    private fun ProtectedAccess.provokeGardener() {
        val gardener = Npc(gardenerType, DigSpot.translate(-1, -1))
        gardener.vars[QUEST_GARDENER_VARN] = 1
        npcRepo.add(gardener, GardenerLifespan)
        gardener.say("First moles, now this! Take this, vandal!")
        gardener.queueCombatRetaliate(player)
    }

    private fun findQuestGardeners(): Sequence<Npc> =
        npcRepo
            .findAll(ZoneKey.from(DigSpot), zoneRadius = 4)
            .filter { it.isType(FaladorGardener) && it.vars[QUEST_GARDENER_VARN] != 0 }

    private fun Npc.isHunting(target: Player): Boolean {
        if (vars["varn.aggressive_player"] != target.uid.packed) {
            return false
        }
        return vars["varn.lastcombat"] + constants.combat_activecombat_delay >=
            currentMapClock ||
            mode == NpcMode.OpPlayer2 ||
            mode == NpcMode.ApPlayer2
    }

    override fun subTitle(): String =
        "by talking to <col=800000>Redbeard Frank<col=000080> in <col=800000>Port Sarim<col=000080>."

    override fun questLog(player: ProtectedAccess): String =
        questJournal(player) {
            line(
                "<red>Redbeard Frank</red> in <red>Port Sarim</red> has promised to tell me " +
                    "the location of <red>One-Eyed Hector's</red> treasure if I bring him " +
                    "some <red>Karamja rum</red>."
            )
            when (quest.getQuestStage(player.player)) {
                StageGotKey ->
                    line(
                        "Frank gave me the <red>key to Hector's chest</red> in his old room " +
                            "in the <red>Blue Moon Inn</red> in Varrock. I should see what's " +
                            "inside."
                    )
                StageReadMessage ->
                    line(
                        "The chest contained a message: <red>\"Visit the city of the White " +
                            "Knights, in the park Saradomin points to the X which marks " +
                            "the spot.\"</red> I should dig there with a spade."
                    )
                else ->
                    line(
                        "The customs officer will not let me take rum off Karamja. " +
                            "<red>Luthas</red> at the banana plantation might know a way to " +
                            "smuggle it off the island."
                    )
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "<red>Redbeard Frank</red> in <red>Port Sarim</red> told me where " +
                    "<red>One-Eyed Hector's</red> treasure was buried, in exchange for some " +
                    "<red>Karamja rum</red>."
            )
            addRawLine("")
            line(
                "I smuggled the rum off Karamja in <red>Luthas's</red> banana shipment, " +
                    "found Hector's message in the <red>Blue Moon Inn</red>, and dug up the " +
                    "treasure in <red>Falador park</red>."
            )
        }

    companion object {
        const val KEY = "quest_piratestreasure"

        private const val StageFetchRum = 1
        private const val StageGotKey = 2
        private const val StageReadMessage = 3
        private const val StageComplete = 4

        private const val BananaCrate = "loc.bananacrate"
        private const val GroceryCrate = "loc.grocerycrate"
        private const val WydinDoor = "loc.wydindoor"
        private const val WydinDoorOpen = "loc.wydindooropen"
        private const val PirateChest = "loc.piratechest"
        private const val PirateChestOpen = "loc.piratechestopen"
        private const val Inviswall = "loc.inviswall"

        private const val KaramjaRum = PiratesTreasureDialogue.KaramjaRum
        private const val ChestKey = PiratesTreasureDialogue.ChestKey
        private const val PirateMessage = "obj.piratemessage"
        private const val Banana = "obj.banana"
        private const val WhiteApron = "obj.white_apron"

        private const val RumNone = 0
        private const val RumPlantation = PiratesTreasureDialogue.RumPlantation
        private const val RumFoodStore = PiratesTreasureDialogue.RumFoodStore
        private const val CrateFull = PiratesTreasureDialogue.CrateFull

        private const val DoorTicks = 3
        private const val ChestOpenTicks = 10
        private const val LetterScrollLines = 8

        private val PirateMessageLines =
            listOf(
                "",
                "",
                "",
                "",
                "Visit the city of the White Knights, in the park",
                "Saradomin points to the X which marks the spot.",
                "",
                "",
            )

        private val DigSpot = CoordGrid(2999, 3383, 0)
        private val WydinSpawn = CoordGrid(3014, 3204, 0)

        private const val FaladorGardener = "npc.falador_gardener"
        private const val GardenerLifespan = 1000
        private const val QUEST_GARDENER_VARN = "varn.generic_state_2"

        private fun npcType(internal: String) =
            requireNotNull(ServerCacheManager.getNpc(internal.asRSCM(RSCMType.NPC))) {
                "Missing npc type: $internal"
            }

        private fun objType(internal: String) =
            requireNotNull(ServerCacheManager.getItem(internal.asRSCM(RSCMType.OBJ))) {
                "Missing obj type: $internal"
            }
    }
}
