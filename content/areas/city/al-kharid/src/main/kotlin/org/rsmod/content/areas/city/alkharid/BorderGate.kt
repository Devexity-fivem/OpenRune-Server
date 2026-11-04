package org.rsmod.content.areas.city.alkharid

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc4
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.generic.locs.gate.GateTranslations
import org.rsmod.content.quest.area.draynor.princealirescue.paStage
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The toll gate between Lumbridge and Al Kharid, transcribed from LostCity's
 * `area_alkharid/scripts/border_gate.rs2`. Friends of Al Kharid (Prince Ali
 * Rescue saved) cross for free; everyone else pays a 10 coin toll.
 */
class BorderGate
@Inject
constructor(
    private val locRepo: LocRepository,
    private val npcRepo: NpcRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1("npc.borderguard1") { talkToBorderGuard(it.npc) }
        onOpNpc1("npc.borderguard2") { talkToBorderGuard(it.npc) }
        onOpLoc1("loc.kharidmetalgateclosedl") { openGate(it.loc) }
        onOpLoc1("loc.kharidmetalgateclosedr") { openGate(it.loc) }
        onOpLoc4("loc.kharidmetalgateclosedl") { payToll(it.loc) }
        onOpLoc4("loc.kharidmetalgateclosedr") { payToll(it.loc) }
    }

    private suspend fun ProtectedAccess.openGate(loc: BoundLocInfo) {
        arriveDelay()
        if (player.paStage() >= StageSaved) {
            passGate(loc)
            return
        }
        val guard = findBorderGuard() ?: return
        startDialogue(guard) { tollDialogue(loc) }
    }

    private suspend fun ProtectedAccess.talkToBorderGuard(npc: Npc) {
        startDialogue(npc) { tollDialogue(null) }
    }

    /**
     * The Lumbridge-side guard (borderguard1) serves players crossing east, the
     * Al-Kharid-side guard (borderguard2) those heading west.
     */
    private fun ProtectedAccess.findBorderGuard(): Npc? {
        val type = if (player.coords.x < GateX) "npc.borderguard1" else "npc.borderguard2"
        return npcRepo.findAll(ZoneKey.from(player.coords), zoneRadius = 1).firstOrNull {
            it.coords.chebyshevDistance(player.coords) <= GuardRange && it.isType(type)
        }
    }

    private suspend fun Dialogue.tollDialogue(loc: BoundLocInfo?) {
        chatPlayer(quiz, "Can I come through this gate?")
        if (player.paStage() >= StageSaved) {
            chatNpc(quiz, "You may pass for free, you are a friend of Al-Kharid.")
            access.passGate(loc)
            return
        }
        chatNpc(quiz, "You must pay a toll of 10 gold coins to pass.")
        when (
            choice3(
                "No thank you, I'll walk around.",
                1,
                "Who does my money go to?",
                2,
                "Yes, ok.",
                3,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "No, thank you. I'll walk around.")
                chatNpc(neutral, "Ok suit yourself.")
            }
            2 -> {
                chatPlayer(quiz, "Who does my money go to?")
                chatNpc(neutral, "The money goes to the city of Al-Kharid.")
            }
            3 -> {
                chatPlayer(quiz, "Yes, ok.")
                if (access.invTotal(access.inv, Coins) < TollPrice) {
                    chatPlayer(quiz, "Oh dear, I don't actually seem to have enough money.")
                    return
                }
                access.chargeToll()
                access.passGate(loc)
            }
        }
    }

    private suspend fun ProtectedAccess.payToll(loc: BoundLocInfo) {
        arriveDelay()
        if (invTotal(inv, Coins) < TollPrice) {
            mes("You do not have enough money.")
            return
        }
        chargeToll()
        passGate(loc)
    }

    private fun ProtectedAccess.chargeToll() {
        if (invDel(inv, Coins, TollPrice).success) {
            mes("You pay the guard.")
        }
    }

    private fun ProtectedAccess.passGate(loc: BoundLocInfo?) {
        val enterEast = player.coords.x < GateX
        val gateZ =
            loc?.takeIf { player.coords.chebyshevDistance(it.coords) <= 1 }?.coords?.z
                ?: player.coords.z.coerceIn(LeftGateTile.z, RightGateTile.z)
        val destination =
            CoordGrid(if (enterEast) GateX else GateX - 1, gateZ, player.coords.level)
        swingPanels(loc, destination)
        soundSynth(OpenSound)
        faceSquare(destination)
        teleport(destination)
    }

    private fun swingPanels(clicked: BoundLocInfo?, crossing: CoordGrid) {
        val panels = clicked?.let(::gatePair) ?: allGatePanels()
        panels.filter { it.coords.z in crossing.z - 1..crossing.z + 1 }.forEach { swing(it) }
    }

    /** The clicked gate's left or right partner, sharing its column. */
    private fun gatePair(loc: BoundLocInfo): List<BoundLocInfo> {
        val left = loc.id == LeftGateId
        val pairOffset = GateTranslations.leftGateRightPair(loc.shape, loc.angle)
        val pairCoords = if (left) loc.coords + pairOffset else loc.coords - pairOffset
        val pairType = if (left) RightGateType else LeftGateType
        val pair = locRepo.findExact(pairCoords, pairType)?.let { BoundLocInfo(it, pairType) }
        return if (pair != null) listOf(loc, pair) else listOf(loc)
    }

    private fun allGatePanels(): List<BoundLocInfo> =
        listOfNotNull(
            locRepo.findExact(LeftGateTile, LeftGateType)?.let { BoundLocInfo(it, LeftGateType) },
            locRepo.findExact(RightGateTile, RightGateType)?.let { BoundLocInfo(it, RightGateType) },
        )

    private fun swing(panel: BoundLocInfo) {
        val translation =
            if (panel.id == LeftGateId) {
                GateTranslations.leftGateOpen(panel.shape, panel.angle)
            } else {
                GateTranslations.rightGateOpen(panel.shape, panel.angle)
            }
        val symbol = panel.internalName
        locRepo.del(panel, GateOpenTicks)
        locRepo.add(panel.coords, InvisibleWall, GateOpenTicks, panel.angle, panel.shape)
        locRepo.add(panel.coords + translation, symbol, GateOpenTicks, panel.turnAngle(3), panel.shape)
    }

    private companion object {
        private const val StageSaved = 100
        private const val TollPrice = 10
        private const val GateX = 3268
        private const val GuardRange = 4
        private const val GateOpenTicks = 4

        private const val Coins = "obj.coins"
        private const val InvisibleWall = "loc.inviswall"
        private const val OpenSound = "synth.picketgate_open"

        private val LeftGateType =
            requireNotNull(
                ServerCacheManager.getObject("loc.kharidmetalgateclosedl".asRSCM(RSCMType.LOC))
            )
        private val RightGateType =
            requireNotNull(
                ServerCacheManager.getObject("loc.kharidmetalgateclosedr".asRSCM(RSCMType.LOC))
            )
        private val LeftGateId = LeftGateType.id
        private val LeftGateTile = CoordGrid(3268, 3227, 0)
        private val RightGateTile = CoordGrid(3268, 3228, 0)
    }
}
