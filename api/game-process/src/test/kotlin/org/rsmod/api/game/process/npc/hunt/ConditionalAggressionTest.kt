package org.rsmod.api.game.process.npc.hunt

import dev.openrune.ServerCacheManager
import dev.openrune.types.NpcMode
import dev.openrune.util.Wearpos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.npc.aggression.ConditionalAggression
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.map.CoordGrid

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class ConditionalAggressionTest {
    private val gate = ConditionalAggression()
    private val here = CoordGrid(3200, 3200, 0)

    private fun player(vararg worn: Pair<Int, Int>): Player =
        Player().apply {
            coords = here
            this.worn = Inventory.create("inv.worn")
            worn.forEach { (slot, obj) ->
                val type = ServerCacheManager.getItem(obj)
                assertNotNull(type, "obj $obj missing from packed cache")
                this.worn[slot] = InvObj(type!!, 1)
            }
        }

    private fun npc(id: Int): Npc {
        val type = ServerCacheManager.getNpcs()[id]
        assertNotNull(type, "npc $id missing from packed cache")
        return Npc(type!!, here)
    }

    @Test
    fun `cowardly hunt mode resolves to player escape`() {
        val hunt = ServerCacheManager.getHunt(3)
        assertNotNull(hunt)
        assertEquals(NpcMode.PlayerEscape, hunt?.findNewMode)
        assertTrue(hunt?.findKeepHunting == true)
    }

    @Test
    fun `godwars goblin flees and kalphite worker hunts`() {
        val goblin = ServerCacheManager.getNpcs()[2247]
        assertNotNull(goblin)
        assertEquals(3, goblin?.huntMode)
        assertEquals(5, goblin?.huntRange)
        val worker = ServerCacheManager.getNpcs()[955]
        assertNotNull(worker)
        assertEquals(1, worker?.huntMode)
        assertEquals(5, worker?.huntRange)
        assertEquals(15, worker?.maxRange)
    }

    @Test
    fun `ape atoll guards carry greegree and quest gates`() {
        for (id in intArrayOf(5271, 5277, 5278)) {
            val def = ServerCacheManager.getNpcs()[id]
            assertNotNull(def, "npc $id")
            assertEquals(6, def?.huntMode, "npc $id huntMode")
            assertEquals(1, def?.paramsRaw?.get(PARAM_AGGRESSION_TOLERANT), "npc $id tolerant")
            assertEquals(1, def?.paramsRaw?.get(PARAM_AGGRO_UNLESS_GREEGREE), "npc $id greegree")
            assertEquals(
                "monkey_madness_ii",
                def?.paramsRaw?.get(PARAM_AGGRO_UNLESS_QUEST),
                "npc $id quest",
            )
        }
    }

    @Test
    fun `greegree items are holdable in weapon slot`() {
        val greegree = ServerCacheManager.getItem(4031)
        assertNotNull(greegree)
        assertEquals(3, greegree?.wearpos1)
        assertEquals(1, greegree?.paramsRaw?.get(PARAM_GREEGREE))
    }

    @Test
    fun `silver-class weapons share the vyre content group`() {
        val ivandis = ServerCacheManager.getItem(IVANDIS_FLAIL)
        assertNotNull(ivandis)
        assertEquals(CONTENT_VYRE_WEAPON, ivandis?.contentGroup)
        val sunspear = ServerCacheManager.getItem(SUNSPEAR)
        assertNotNull(sunspear)
        assertEquals(CONTENT_VYRE_WEAPON, sunspear?.contentGroup)
    }

    @Test
    fun `vyre disguise blocks aggression and partial sets do not`() {
        val vyre = npc(9739)
        assertTrue(gate.permitsAggression(vyre, player()))
        val disguised =
            player(
                Wearpos.Torso.slot to 9634,
                Wearpos.Legs.slot to 9636,
                Wearpos.Feet.slot to 9638,
            )
        assertFalse(gate.permitsAggression(vyre, disguised))
        val partial = player(Wearpos.Torso.slot to 9634, Wearpos.Legs.slot to 9636)
        assertTrue(gate.permitsAggression(vyre, partial))
    }

    @Test
    fun `battle mage cape gate requires the exact cape`() {
        val mage = npc(1610)
        assertTrue(gate.permitsAggression(mage, player()))
        assertFalse(gate.permitsAggression(mage, player(Wearpos.Back.slot to 2414)))
        assertTrue(gate.permitsAggression(mage, player(Wearpos.Back.slot to 10450)))
    }

    @Test
    fun `greegree gate blocks ape atoll aggression`() {
        val guard = npc(5271)
        assertTrue(gate.permitsAggression(guard, player()))
        assertFalse(gate.permitsAggression(guard, player(Wearpos.RightHand.slot to 4031)))
    }

    @Test
    fun `bandit camp only attacks god-aligned players`() {
        val bandit = npc(690)
        assertFalse(gate.permitsAggression(bandit, player()))
        assertTrue(gate.permitsAggression(bandit, player(Wearpos.Back.slot to 2412)))
    }

    @Test
    fun `merc needs a weapon and guard accepts any armour`() {
        val merc = npc(4656)
        assertFalse(gate.permitsAggression(merc, player()))
        assertTrue(gate.permitsAggression(merc, player(Wearpos.RightHand.slot to 1205)))
        val guard = npc(4660)
        assertFalse(gate.permitsAggression(guard, player()))
        assertTrue(gate.permitsAggression(guard, player(Wearpos.Back.slot to 2412)))
    }

    @Test
    fun `mourner disguise needs the full five-piece set`() {
        val mourner = npc(9236)
        assertTrue(gate.permitsAggression(mourner, player()))
        val full =
            player(
                Wearpos.Torso.slot to 6065,
                Wearpos.Legs.slot to 6067,
                Wearpos.Feet.slot to 6069,
                Wearpos.Hands.slot to 6068,
                Wearpos.Back.slot to 6070,
            )
        assertFalse(gate.permitsAggression(mourner, full))
        val missingBoots =
            player(
                Wearpos.Torso.slot to 6065,
                Wearpos.Legs.slot to 6067,
                Wearpos.Hands.slot to 6068,
                Wearpos.Back.slot to 6070,
            )
        assertTrue(gate.permitsAggression(mourner, missingBoots))
    }

    @Test
    fun `godwars faction gear blocks follower aggression`() {
        val knight = npc(2213)
        assertTrue(gate.permitsAggression(knight, player()))
        assertFalse(gate.permitsAggression(knight, player(Wearpos.Back.slot to 2412)))
    }

    companion object {
        private const val PARAM_AGGRESSION_TOLERANT = 65560
        private const val PARAM_AGGRO_UNLESS_GREEGREE = 65564
        private const val PARAM_AGGRO_UNLESS_QUEST = 65570
        private const val PARAM_GREEGREE = 65571
        private const val CONTENT_VYRE_WEAPON = 64000
        private const val IVANDIS_FLAIL = 22398
        private const val SUNSPEAR = 33722

        @JvmStatic
        @BeforeAll
        fun cache() {
            ServerCacheManager.init(240).close()
        }
    }
}
