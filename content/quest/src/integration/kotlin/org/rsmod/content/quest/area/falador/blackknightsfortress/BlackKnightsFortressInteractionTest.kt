package org.rsmod.content.quest.area.falador.blackknightsfortress

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.NpcServerType
import dev.openrune.types.ObjectServerType
import dev.openrune.types.util.UncheckedType
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.api.player.events.interact.LocUEvents
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.quest.area.falador.blackknightsfortress.BlackKnightsFortress.Companion.Cabbage
import org.rsmod.content.quest.area.falador.blackknightsfortress.BlackKnightsFortress.Companion.DraynorCabbage
import org.rsmod.content.quest.area.falador.blackknightsfortress.BlackKnightsFortress.Companion.Grill
import org.rsmod.content.quest.area.falador.blackknightsfortress.BlackKnightsFortress.Companion.Hole
import org.rsmod.content.quest.area.falador.blackknightsfortress.BlackKnightsFortress.Companion.StageAccepted
import org.rsmod.content.quest.area.falador.blackknightsfortress.BlackKnightsFortress.Companion.StageComplete
import org.rsmod.content.quest.area.falador.blackknightsfortress.BlackKnightsFortress.Companion.StageOverheard
import org.rsmod.content.quest.area.falador.blackknightsfortress.BlackKnightsFortress.Companion.StageRuined
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.ui.UserInterface
import org.rsmod.map.CoordGrid

private var Player.testQuestPoints by intVarp("varp.qp")

@OptIn(UncheckedType::class)
@Execution(ExecutionMode.SAME_THREAD)
class BlackKnightsFortressInteractionTest {
    @Test
    fun GameTestState.`amik rejects questers below 12 quest points`() =
        runInjectedGameTest(Deps::class, null, BlackKnightsFortress::class) {
            val amik = spawnNpc(player.coords.translateZ(1), npc("npc.sir_amik_varze"))
            player.testQuestPoints = 0
            player.withProtectedAccess { eventBus.publish(this, NpcEvents.Op1(amik)) }
            driveDialogue(player, options = listOf(1))
            assertEquals(0, player.vars["varp.spy"])
        }

    @Test
    fun GameTestState.`amik assigns the spy mission`() =
        runInjectedGameTest(Deps::class, null, BlackKnightsFortress::class) {
            val amik = spawnNpc(player.coords.translateZ(1), npc("npc.sir_amik_varze"))
            player.testQuestPoints = 12
            player.withProtectedAccess { eventBus.publish(this, NpcEvents.Op1(amik)) }
            driveDialogue(player, options = listOf(1, 1))
            assertEquals(StageAccepted, player.vars["varp.spy"])
        }

    @Test
    fun GameTestState.`listening at the grill reveals the secret weapon`() =
        runInjectedGameTest(Deps::class, null, BlackKnightsFortress::class) {
            player.spyStage = StageAccepted
            val (grill, type) = boundLoc(Grill, player.coords)
            player.withProtectedAccess {
                assertTrue(eventBus.publish(this, LocEvents.Op1(grill, grill, type)))
            }
            driveDialogue(player)
            assertEquals(StageOverheard, player.vars["varp.spy"])
        }

    @Test
    fun GameTestState.`the draynor cabbage is the wrong sort`() =
        runInjectedGameTest(Deps::class, null, BlackKnightsFortress::class) {
            player.spyStage = StageOverheard
            player.clearInv()
            player.inv[0] = InvObj(obj(DraynorCabbage))
            useOn(Hole, DraynorCabbage)
            driveDialogue(player)
            assertEquals(StageOverheard, player.vars["varp.spy"])
            assertEquals(1, player.inv.count(obj(DraynorCabbage)))
        }

    @Test
    fun GameTestState.`a plain cabbage ruins the potion`() =
        runInjectedGameTest(Deps::class, null, BlackKnightsFortress::class) {
            player.spyStage = StageOverheard
            player.clearInv()
            player.inv[0] = InvObj(obj(Cabbage))
            useOn(Hole, Cabbage)
            driveDialogue(player)
            assertEquals(StageRuined, player.vars["varp.spy"])
            assertEquals(0, player.inv.count(obj(Cabbage)))
        }

    @Test
    fun GameTestState.`cabbage on the hole means nothing before the eavesdrop`() =
        runInjectedGameTest(Deps::class, null, BlackKnightsFortress::class) {
            player.spyStage = StageAccepted
            player.clearInv()
            player.inv[0] = InvObj(obj(Cabbage))
            useOn(Hole, Cabbage)
            driveDialogue(player)
            assertEquals(StageAccepted, player.vars["varp.spy"])
            assertEquals(1, player.inv.count(obj(Cabbage)))
        }

    @Test
    fun GameTestState.`amik rewards the saboteur`() =
        runInjectedGameTest(Deps::class, null, BlackKnightsFortress::class) {
            val amik = spawnNpc(player.coords.translateZ(1), npc("npc.sir_amik_varze"))
            player.spyStage = StageRuined
            player.clearInv()
            val qp = player.testQuestPoints
            player.withProtectedAccess { eventBus.publish(this, NpcEvents.Op1(amik)) }
            driveDialogue(player)
            assertEquals(StageComplete, player.vars["varp.spy"])
            assertEquals(2500, player.inv.count(obj("obj.coins")))
            assertEquals(qp + 3, player.testQuestPoints)
        }

    private fun GameTestScope.useOn(locSymbol: String, itemSymbol: String) {
        val (loc, type) = boundLoc(locSymbol, player.coords)
        val item = obj(itemSymbol)
        player.withProtectedAccess {
            eventBus.publish(this, LocUEvents.Op(loc, loc, type, item, 0))
        }
    }

    private fun boundLoc(symbol: String, coords: CoordGrid): Pair<BoundLocInfo, ObjectServerType> {
        val type = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM(RSCMType.LOC)))
        val loc =
            BoundLocInfo(
                LocInfo(2, coords, LocEntity(type.id, LocShape.CentrepieceStraight.id, LocAngle.West.id)),
                type,
            )
        return loc to type
    }

    private fun GameTestScope.driveDialogue(p: Player, options: List<Int> = emptyList()) {
        val selections = options.iterator()
        repeat(300) {
            val coroutine = p.activeCoroutine ?: return
            if (coroutine.isIdle) return
            if (coroutine.isAwaiting(ResumePauseButtonInput::class)) {
                val (modal, component, sub) = pauseComponent(p, selections)
                val hosted = p.ui.modals.getComponent(UserInterface(modal))
                if (hosted != null) {
                    p.ui.queueClose(hosted)
                }
                p.resumeActiveCoroutine(ResumePauseButtonInput(component, sub))
            }
            advance()
        }
        fail<Unit>("Dialogue did not finish for ${p.displayName}")
    }

    private fun pauseComponent(
        p: Player,
        selections: Iterator<Int>,
    ): Triple<dev.openrune.cache.filestore.definition.InterfaceType, String, Int> =
        when {
            p.ui.containsModal("interface.chatmenu") ->
                Triple(
                    ServerCacheManager.fromInterface("interface.chatmenu"),
                    "component.chatmenu:options",
                    if (selections.hasNext()) selections.next() else 1,
                )
            p.ui.containsModal("interface.objectbox_double") ->
                Triple(
                    ServerCacheManager.fromInterface("interface.objectbox_double"),
                    "component.objectbox_double:pausebutton",
                    -1,
                )
            p.ui.containsModal("interface.messagebox") ->
                Triple(
                    ServerCacheManager.fromInterface("interface.messagebox"),
                    "component.messagebox:continue",
                    -1,
                )
            p.ui.containsModal("interface.chat_right") ->
                Triple(
                    ServerCacheManager.fromInterface("interface.chat_right"),
                    "component.chat_right:continue",
                    -1,
                )
            p.ui.containsModal("interface.chat_left") ->
                Triple(
                    ServerCacheManager.fromInterface("interface.chat_left"),
                    "component.chat_left:continue",
                    -1,
                )
            else -> fail("No resumable dialogue modal open for ${p.displayName}")
        }

    private fun obj(name: String): ItemServerType =
        checkNotNull(ServerCacheManager.getItem(name.asRSCM(RSCMType.OBJ))) {
            "Obj type not found: $name"
        }

    private fun npc(name: String): NpcServerType =
        checkNotNull(ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC))) {
            "Npc type not found: $name"
        }

    class Deps @Inject constructor(val locRepo: LocRepository)
}
