package org.rsmod.content.quest.area.varrock.shieldofarrav

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.NpcServerType
import dev.openrune.types.util.UncheckedType
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.player.events.interact.HeldUEvents
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.events.interact.PlayerUEvents
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArrav.Companion.BlackArmComplete
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArrav.Companion.BlackArmJoined
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArrav.Companion.CertLeft
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArrav.Companion.CertRight
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArrav.Companion.Certificate
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArrav.Companion.CoinReward
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArrav.Companion.ShieldRight
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArrav.Companion.StageComplete
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.ui.UserInterface

@OptIn(UncheckedType::class)
@Execution(ExecutionMode.SAME_THREAD)
class ShieldOfArravInteractionTest {
    @Test
    fun GameTestState.`curator turns a shield half into two half-certificates`() =
        runInjectedGameTest(Deps::class, null, ShieldOfArrav::class) { deps ->
            val curator = spawnNpc(player.coords.translateZ(1), npc("npc.curator"))
            player.clearInv()
            player.inv[0] = InvObj(obj(ShieldRight))
            player.withProtectedAccess { eventBus.publish(this, NpcEvents.Op1(curator)) }
            driveDialogue(player, deps)
            assertEquals(0, player.inv.count(obj(ShieldRight)))
            assertEquals(2, player.inv.count(obj(CertRight)))
            assertEquals(2, player.vars["varbit.soa_shield_given"])
        }

    @Test
    fun GameTestState.`half-certificates combine into a full certificate`() =
        runInjectedGameTest(Deps::class, null, ShieldOfArrav::class) { deps ->
            player.clearInv()
            player.inv[0] = InvObj(obj(CertLeft))
            player.inv[1] = InvObj(obj(CertRight))
            player.withProtectedAccess {
                eventBus.publish(this, HeldUEvents.Type(obj(CertLeft), 0, obj(CertRight), 1))
            }
            driveDialogue(player, deps, options = listOf(1))
            assertEquals(0, player.inv.count(obj(CertLeft)))
            assertEquals(0, player.inv.count(obj(CertRight)))
            assertEquals(1, player.inv.count(obj(Certificate)))
        }

    @Test
    fun GameTestState.`using a half-certificate on a partner drops it for them`() =
        runInjectedGameTest(Deps::class, null, ShieldOfArrav::class) { deps ->
            val friend = registerPlayer(player.coords.translateX(1))
            friend.observerUUID = friend.uuid
            player.clearInv()
            player.inv[0] = InvObj(obj(CertLeft))
            player.withProtectedAccess {
                eventBus.publish(this, PlayerUEvents.Op(friend, 0, obj(CertLeft)))
            }
            driveDialogue(player, deps, options = listOf(1))
            assertEquals(0, player.inv.count(obj(CertLeft)))
            val dropped = deps.objRepo.findAll(player.coords).singleOrNull {
                it.type == obj(CertLeft).id
            }
            assertNotNull(dropped) { "No dropped certificate at ${player.coords}" }
            assertEquals(friend.observerUUID, checkNotNull(dropped).receiverId)
        }

    @Test
    fun GameTestState.`king roald grants the reward for a full certificate`() =
        runInjectedGameTest(Deps::class, null, ShieldOfArrav::class) { deps ->
            val roald = spawnNpc(player.coords.translateZ(1), npc("npc.king_roald"))
            player.clearInv()
            player.inv[0] = InvObj(obj(Certificate))
            player.blackArmGang = BlackArmJoined
            player.withProtectedAccess { eventBus.publish(this, NpcEvents.Op1(roald)) }
            driveDialogue(player, deps, options = listOf(1))
            assertEquals(0, player.inv.count(obj(Certificate)))
            assertEquals(CoinReward, player.inv.count(obj("obj.coins")))
            assertEquals(BlackArmComplete, player.blackArmGang)
            assertEquals(StageComplete, player.vars["varbit.shieldofarrav"])
        }

    @Test
    fun GameTestState.`king roald rejects a lone half-certificate`() =
        runInjectedGameTest(Deps::class, null, ShieldOfArrav::class) { deps ->
            val roald = spawnNpc(player.coords.translateZ(1), npc("npc.king_roald"))
            player.clearInv()
            player.inv[0] = InvObj(obj(CertLeft))
            player.blackArmGang = BlackArmJoined
            player.withProtectedAccess { eventBus.publish(this, NpcEvents.Op1(roald)) }
            driveDialogue(player, deps, options = listOf(1))
            assertEquals(1, player.inv.count(obj(CertLeft)))
            assertEquals(0, player.inv.count(obj("obj.coins")))
            assertEquals(BlackArmJoined, player.blackArmGang)
            assertEquals(0, player.vars["varbit.shieldofarrav"])
        }

    private fun GameTestScope.driveDialogue(
        p: Player,
        deps: Deps,
        options: List<Int> = emptyList(),
    ) {
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

    class Deps @Inject constructor(val objRepo: ObjRepository)
}
