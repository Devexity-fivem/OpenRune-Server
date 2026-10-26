package org.rsmod.content.areas.misc.tutorial

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.inv.isType
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@OptIn(InternalApi::class)
class TutorialIslandInventoryTest {
    @Test
    fun `every gated door symbol resolves to a loc type`() {
        for (symbol in TutorialIsland.DOOR_STAGES.keys) {
            val resolved = runCatching { symbol.asRSCM(RSCMType.LOC) }.getOrNull()
            assertTrue(
                resolved != null && ServerCacheManager.getObject(resolved) != null,
                "Unresolved door symbol: $symbol",
            )
        }
    }

    @Test
    fun `door pairs are symmetric and gated`() {
        for ((symbol, pair) in TutorialIsland.DOOR_PAIRS) {
            val (partner, isLeft) = pair
            assertTrue(
                TutorialIsland.DOOR_STAGES.containsKey(symbol),
                "Paired door has no stage gate: $symbol",
            )
            assertTrue(
                TutorialIsland.DOOR_STAGES.containsKey(partner),
                "Paired partner has no stage gate: $partner",
            )
            val back = TutorialIsland.DOOR_PAIRS.getValue(partner)
            assertEquals(symbol, back.first, "Door pair is not symmetric: $symbol <-> $partner")
            assertEquals(!isLeft, back.second, "Both halves marked as $symbol-left: $partner")
        }
    }

    @Test
    fun `progress is monotonic across the stage order`() {
        val stages =
            listOf(
                TutorialIsland.FRESH,
                TutorialIsland.GUIDE_DONE,
                TutorialIsland.BRYNNA_NET,
                TutorialIsland.SHRIMP_CAUGHT,
                TutorialIsland.AXE_TINDER,
                TutorialIsland.LOGS,
                TutorialIsland.FIRE_LIT,
                TutorialIsland.SHRIMP_COOKED,
                TutorialIsland.LEV_FLOUR,
                TutorialIsland.DOUGH,
                TutorialIsland.BREAD,
                TutorialIsland.QUEST_DONE,
                TutorialIsland.DEZZICK_PICK,
                TutorialIsland.ORES,
                TutorialIsland.BAR,
                TutorialIsland.HAMMER,
                TutorialIsland.DAGGER,
                TutorialIsland.COMBAT_READY,
                TutorialIsland.MELEE_KILL,
                TutorialIsland.BOW_ARROWS,
                TutorialIsland.RANGED_KILL,
                TutorialIsland.BANK_DONE,
                TutorialIsland.ACCOUNT_DONE,
                TutorialIsland.BRACE_DONE,
                TutorialIsland.PAUL_DONE,
                TutorialIsland.RUNES,
                TutorialIsland.CHICKEN_KILL,
                TutorialIsland.DONE,
            )
        assertEquals(stages.sorted(), stages)
        val progress = stages.map(TutorialIsland::progressFor)
        assertEquals(progress.sorted(), progress)
        assertEquals(0, TutorialIsland.progressFor(TutorialIsland.FRESH))
        assertEquals(16384, TutorialIsland.progressFor(TutorialIsland.MAX_STAGE))
    }

    @Test
    fun `supply tops up only what is missing`() {
        val f = Fixture()
        f.player.inv[0] = InvObj("obj.bronze_sword")
        assertTrue(TutorialInventory.supply(f.player, InvObj("obj.bronze_sword"), InvObj("obj.wooden_shield")))
        assertEquals(1, f.player.inv.count("obj.bronze_sword"))
        assertEquals(1, f.player.inv.count("obj.wooden_shield"))
    }

    @Test
    fun `a worn item counts as held for supply`() {
        val f = Fixture()
        f.player.worn[2] = InvObj("obj.shortbow")
        assertTrue(TutorialInventory.supply(f.player, InvObj("obj.shortbow"), InvObj("obj.bronze_arrow", 50)))
        assertEquals(0, f.player.inv.count("obj.shortbow"))
        assertEquals(50, f.player.inv.count("obj.bronze_arrow") + f.player.worn.count("obj.bronze_arrow"))
    }

    @Test
    fun `supply tops up a stack instead of adding a second`() {
        val f = Fixture()
        f.player.inv[0] = InvObj("obj.bronze_arrow", 30)
        assertTrue(TutorialInventory.supply(f.player, InvObj("obj.bronze_arrow", 50)))
        assertEquals(50, f.player.inv.count("obj.bronze_arrow"))
    }

    @Test
    fun `a full inventory rolls the whole grant back`() {
        val f = Fixture()
        for (slot in f.player.inv.indices) f.player.inv[slot] = InvObj("obj.bronze_sword")
        assertEquals(false, TutorialInventory.supply(f.player, InvObj("obj.net"), InvObj("obj.tinderbox")))
        assertTrue(f.player.inv.all { it.isType("obj.bronze_sword") })
        assertEquals(0, f.player.inv.count("obj.net"))
        assertEquals(0, f.player.inv.count("obj.tinderbox"))
    }

    @Test
    fun `exchange rolls inputs back when the outputs cannot fit`() {
        val f = Fixture()
        for (slot in f.player.inv.indices) f.player.inv[slot] = InvObj("obj.bronze_sword")
        f.player.inv[0] = InvObj("obj.copper_ore")
        val exchanged =
            TutorialInventory.exchange(
                f.player,
                listOf(InvObj("obj.copper_ore")),
                listOf(InvObj("obj.bronze_bar"), InvObj("obj.hammer")),
            )
        assertEquals(false, exchanged)
        assertEquals(1, f.player.inv.count("obj.copper_ore"))
        assertEquals(0, f.player.inv.count("obj.bronze_bar"))
        assertEquals(0, f.player.inv.count("obj.hammer"))
    }

    @Test
    fun `exchange completes when the freed slots exactly fit the outputs`() {
        val f = Fixture()
        for (slot in f.player.inv.indices) f.player.inv[slot] = InvObj("obj.bronze_sword")
        f.player.inv[0] = InvObj("obj.copper_ore")
        f.player.inv[1] = InvObj("obj.tin_ore")
        val exchanged =
            TutorialInventory.exchange(
                f.player,
                listOf(InvObj("obj.copper_ore"), InvObj("obj.tin_ore")),
                listOf(InvObj("obj.bronze_bar"), InvObj("obj.hammer")),
            )
        assertEquals(true, exchanged)
        assertEquals(1, f.player.inv.count("obj.bronze_bar"))
        assertEquals(1, f.player.inv.count("obj.hammer"))
    }

    @Test
    fun `exchange fails without touching the inventory when an input is short`() {
        val f = Fixture()
        f.player.inv[0] = InvObj("obj.copper_ore")
        val exchanged =
            TutorialInventory.exchange(
                f.player,
                listOf(InvObj("obj.copper_ore"), InvObj("obj.tin_ore")),
                listOf(InvObj("obj.bronze_bar")),
            )
        assertEquals(false, exchanged)
        assertEquals(1, f.player.inv.count("obj.copper_ore"))
    }

    @Test
    fun `melee and ranged kills only count in the right stage and style`() {
        assertEquals(TutorialIsland.MELEE_KILL, ratKillStage(TutorialIsland.COMBAT_READY, HitType.Melee))
        assertEquals(TutorialIsland.RANGED_KILL, ratKillStage(TutorialIsland.BOW_ARROWS, HitType.Ranged))
        assertNull(ratKillStage(TutorialIsland.COMBAT_READY, HitType.Ranged))
        assertNull(ratKillStage(TutorialIsland.BOW_ARROWS, HitType.Melee))
        assertNull(ratKillStage(TutorialIsland.DAGGER, HitType.Melee))
        assertNull(ratKillStage(TutorialIsland.RANGED_KILL, HitType.Melee))
    }

    private class Fixture {
        val player =
            Player().apply {
                uuid = 1L
                slotId = 1
                assignUid()
                coords = CoordGrid(3094, 3107, 0)
                inv =
                    Inventory(
                        checkNotNull(ServerCacheManager.getInventory("inv.inv".asRSCM())),
                        arrayOfNulls(28),
                    )
                worn =
                    Inventory(
                        checkNotNull(ServerCacheManager.getInventory("inv.worn".asRSCM())),
                        arrayOfNulls(14),
                    )
            }
    }

    companion object {
        private val restored = mutableListOf<() -> Unit>()

        @JvmStatic
        @BeforeAll
        fun cache() {
            ServerCacheManager.init(240).close()
            for ((owner, name) in
                listOf(
                    "org.rsmod.api.invtx.InvTransactionsScriptKt" to "cachedInventoryTransactions",
                    "org.rsmod.api.invtx.VirtualInvTransactionsKt" to "cachedPlayerItemStorage",
                )) {
                val field = Class.forName(owner).getDeclaredField(name).apply { isAccessible = true }
                val old = field.get(null)
                restored += { field.set(null, old) }
            }
            val oldStorage = InvVirtualStorageHolder.instance
            restored += { InvVirtualStorageHolder.instance = oldStorage }
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
                ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        @JvmStatic
        @AfterAll
        fun restore() {
            restored.asReversed().forEach { it() }
            restored.clear()
        }
    }
}
