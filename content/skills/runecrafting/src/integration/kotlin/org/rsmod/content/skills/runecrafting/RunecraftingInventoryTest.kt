package org.rsmod.content.skills.runecrafting

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.table.runecrafting.RunecraftingAltarsRow
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.skills.runecrafting.altar.AltarEvents
import org.rsmod.content.skills.runecrafting.essencepouch.EssencePouch
import org.rsmod.content.skills.runecrafting.items.BloodEssence
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid

@Execution(ExecutionMode.SAME_THREAD)
class RunecraftingInventoryTest {
    @Test
    fun GameTestState.`rune overflow preserves pure essence and experience`() = runAltarTest { loc, _ ->
        player.inv[0] = InvObj(EssencePouch.PURE_ESSENCE, 1)
        player.inv[1] = InvObj(WATER, Int.MAX_VALUE)
        val xp = player.statMap.getFineXP(RUNECRAFTING)
        player.opLoc1(loc)
        advance(8)
        assertEquals(1, player.inv.count(EssencePouch.PURE_ESSENCE))
        assertEquals(Int.MAX_VALUE, player.inv.count(WATER))
        assertEquals(xp, player.statMap.getFineXP(RUNECRAFTING))
    }

    @Test
    fun GameTestState.`rune overflow preserves daeyalt essence and experience`() = runAltarTest { loc, _ ->
        player.inv[0] = InvObj(EssencePouch.DAEYALT_ESSENCE, 1)
        player.inv[1] = InvObj(WATER, Int.MAX_VALUE)
        val xp = player.statMap.getFineXP(RUNECRAFTING)
        player.opLoc1(loc)
        advance(8)
        assertEquals(1, player.inv.count(EssencePouch.DAEYALT_ESSENCE))
        assertEquals(Int.MAX_VALUE, player.inv.count(WATER))
        assertEquals(xp, player.statMap.getFineXP(RUNECRAFTING))
    }

    @Test
    fun GameTestState.`failed output does not consume a runic extract`() = runAltarTest(level = 60) { loc, altar ->
        val extract = altar.rune.extract.internalName
        player.inv[0] = InvObj(EssencePouch.PURE_ESSENCE, 1)
        player.inv[1] = InvObj(WATER, Int.MAX_VALUE)
        player.inv[2] = InvObj(extract, 1)
        val xp = player.statMap.getFineXP(RUNECRAFTING)
        player.opLoc1(loc)
        advance(8)
        assertEquals(1, player.inv.count(EssencePouch.PURE_ESSENCE))
        assertEquals(1, player.inv.count(extract))
        assertEquals(Int.MAX_VALUE, player.inv.count(WATER))
        assertEquals(xp, player.statMap.getFineXP(RUNECRAFTING))
    }

    @Test
    fun GameTestState.`mixed essence in a full inventory is consumed exactly once`() = runAltarTest { loc, _ ->
        player.inv[0] = InvObj(EssencePouch.PURE_ESSENCE, 1)
        player.inv[1] = InvObj(EssencePouch.RUNE_ESSENCE, 1)
        for (slot in 2 until 28) player.inv[slot] = InvObj("obj.bones", 1)
        val xp = player.statMap.getFineXP(RUNECRAFTING)
        repeat(2) { player.opLoc1(loc) }
        advance(8)
        assertEquals(0, player.inv.count(EssencePouch.PURE_ESSENCE))
        assertEquals(0, player.inv.count(EssencePouch.RUNE_ESSENCE))
        assertEquals(26, player.inv.count("obj.bones"))
        assertEquals(4, player.inv.count(WATER))
        assertEquals(xp + 120, player.statMap.getFineXP(RUNECRAFTING))
    }

    @Test
    fun GameTestState.`a source removed during the animation grants no output or experience`() = runAltarTest { loc, _ ->
        player.inv[0] = InvObj(EssencePouch.PURE_ESSENCE, 1)
        val xp = player.statMap.getFineXP(RUNECRAFTING)
        player.opLoc1(loc)
        advance(1)
        player.inv[0] = InvObj("obj.bones", 1)
        advance(7)
        assertEquals(1, player.inv.count("obj.bones"))
        assertEquals(0, player.inv.count(WATER))
        assertEquals(xp, player.statMap.getFineXP(RUNECRAFTING))
    }

    @Test
    fun GameTestState.`blood rune overflow preserves the final charge and active essence`() = runAltarTest(BloodEssence.BLOOD_RUNE, 77) { loc, _ ->
        player.inv[0] = InvObj(EssencePouch.PURE_ESSENCE, 1)
        player.inv[1] = InvObj(BloodEssence.BLOOD_RUNE, Int.MAX_VALUE)
        player.inv[2] = InvObj(BloodEssence.ACTIVE, 1)
        player.testBloodEssenceCharges = 1
        random.next = 0
        random.then = 0
        val xp = player.statMap.getFineXP(RUNECRAFTING)
        player.opLoc1(loc)
        advance(8)
        assertEquals(1, player.inv.count(EssencePouch.PURE_ESSENCE))
        assertEquals(1, player.inv.count(BloodEssence.ACTIVE))
        assertEquals(1, player.testBloodEssenceCharges)
        assertEquals(Int.MAX_VALUE, player.inv.count(BloodEssence.BLOOD_RUNE))
        assertEquals(xp, player.statMap.getFineXP(RUNECRAFTING))
    }

    @Test
    fun GameTestState.`the last blood essence charge commits with its bonus rune`() = runAltarTest(BloodEssence.BLOOD_RUNE, 77) { loc, _ ->
        player.inv[0] = InvObj(EssencePouch.PURE_ESSENCE, 1)
        player.inv[1] = InvObj(BloodEssence.ACTIVE, 1)
        player.testBloodEssenceCharges = 1
        random.next = 0
        random.then = 0
        val xp = player.statMap.getFineXP(RUNECRAFTING)
        repeat(2) { player.opLoc1(loc) }
        advance(8)
        assertEquals(0, player.inv.count(EssencePouch.PURE_ESSENCE))
        assertEquals(0, player.inv.count(BloodEssence.ACTIVE))
        assertEquals(0, player.testBloodEssenceCharges)
        assertEquals(2, player.inv.count(BloodEssence.BLOOD_RUNE))
        assertEquals(xp + 105, player.statMap.getFineXP(RUNECRAFTING))
    }

    @Test
    fun GameTestState.`unused blood essence charges remain with the active item`() = runAltarTest(BloodEssence.BLOOD_RUNE, 77) { loc, _ ->
        player.inv[0] = InvObj(EssencePouch.PURE_ESSENCE, 1)
        player.inv[1] = InvObj(BloodEssence.ACTIVE, 1)
        player.testBloodEssenceCharges = 5
        random.next = 0
        random.then = 0
        player.opLoc1(loc)
        advance(8)
        assertEquals(0, player.inv.count(EssencePouch.PURE_ESSENCE))
        assertEquals(1, player.inv.count(BloodEssence.ACTIVE))
        assertEquals(4, player.testBloodEssenceCharges)
        assertEquals(2, player.inv.count(BloodEssence.BLOOD_RUNE))
    }

    @Test
    fun GameTestState.`extracts are consumed per essence without multiplying experience`() = runAltarTest(level = 60) { loc, altar ->
        val extract = altar.rune.extract.internalName
        repeat(2) { player.inv[it] = InvObj(EssencePouch.PURE_ESSENCE, 1) }
        player.inv[2] = InvObj(extract, 3)
        val xp = player.statMap.getFineXP(RUNECRAFTING)
        player.opLoc1(loc)
        advance(8)
        assertEquals(0, player.inv.count(EssencePouch.PURE_ESSENCE))
        assertEquals(1, player.inv.count(extract))
        assertEquals(508, player.inv.count(WATER))
        assertEquals(xp + 120, player.statMap.getFineXP(RUNECRAFTING))
    }

    @Test
    fun GameTestState.`limited extracts enhance only the matching number of essence`() = runAltarTest(level = 60) { loc, altar ->
        repeat(2) { player.inv[it] = InvObj(EssencePouch.PURE_ESSENCE, 1) }
        player.inv[2] = InvObj(altar.rune.extract.internalName, 1)
        player.opLoc1(loc)
        advance(8)
        assertEquals(0, player.inv.count(EssencePouch.PURE_ESSENCE))
        assertEquals(0, player.inv.count(altar.rune.extract.internalName))
        assertEquals(258, player.inv.count(WATER))
    }

    @Test
    fun GameTestState.`extract bonuses work with daeyalt without boosting the bonus runes or experience`() = runAltarTest(level = 60) { loc, altar ->
        player.inv[0] = InvObj(EssencePouch.DAEYALT_ESSENCE, 1)
        player.inv[1] = InvObj(altar.rune.extract.internalName, 2)
        val xp = player.statMap.getFineXP(RUNECRAFTING)
        player.opLoc1(loc)
        advance(8)
        assertEquals(0, player.inv.count(EssencePouch.DAEYALT_ESSENCE))
        assertEquals(1, player.inv.count(altar.rune.extract.internalName))
        assertEquals(254, player.inv.count(WATER))
        assertEquals(xp + 90, player.statMap.getFineXP(RUNECRAFTING))
    }

    @Test
    fun GameTestState.`batch extract output overflow rolls back the whole conversion`() = runAltarTest(level = 60) { loc, altar ->
        repeat(2) { player.inv[it] = InvObj(EssencePouch.PURE_ESSENCE, 1) }
        player.inv[2] = InvObj(altar.rune.extract.internalName, 2)
        player.inv[3] = InvObj(WATER, Int.MAX_VALUE - 500)
        val xp = player.statMap.getFineXP(RUNECRAFTING)
        player.opLoc1(loc)
        advance(8)
        assertEquals(2, player.inv.count(EssencePouch.PURE_ESSENCE))
        assertEquals(2, player.inv.count(altar.rune.extract.internalName))
        assertEquals(Int.MAX_VALUE - 500, player.inv.count(WATER))
        assertEquals(xp, player.statMap.getFineXP(RUNECRAFTING))
    }

    @Test
    fun GameTestState.`a visible boost permits an ordinary altar requirement`() = runAltarTest(level = 4) { loc, _ ->
        player.setCurrentLevel(RUNECRAFTING, 5)
        player.inv[0] = InvObj(EssencePouch.PURE_ESSENCE, 1)
        val xp = player.statMap.getFineXP(RUNECRAFTING)
        player.opLoc1(loc)
        advance(8)
        assertEquals(0, player.inv.count(EssencePouch.PURE_ESSENCE))
        assertEquals(1, player.inv.count(WATER))
        assertEquals(xp + 60, player.statMap.getFineXP(RUNECRAFTING))
    }

    @Test
    fun GameTestState.`water multiples use the visible boost without multiplying experience`() {
        for (essence in listOf(EssencePouch.PURE_ESSENCE, EssencePouch.DAEYALT_ESSENCE)) {
            runAltarTest(level = 37) { loc, _ ->
                player.setCurrentLevel(RUNECRAFTING, 38)
                player.inv[0] = InvObj(essence, 1)
                val xp = player.statMap.getFineXP(RUNECRAFTING)
                player.opLoc1(loc)
                advance(8)
                assertEquals(0, player.inv.count(essence))
                assertEquals(3, player.inv.count(WATER))
                val fineXp = if (essence == EssencePouch.DAEYALT_ESSENCE) 90 else 60
                assertEquals(xp + fineXp, player.statMap.getFineXP(RUNECRAFTING))
            }
        }
    }

    @Test
    fun GameTestState.`a plus five boost above level 99 produces five earth runes per essence`() = runAltarTest("obj.earthrune", 99) { loc, _ ->
        player.setCurrentLevel(RUNECRAFTING, 104)
        player.inv[0] = InvObj(EssencePouch.PURE_ESSENCE, 1)
        val xp = player.statMap.getFineXP(RUNECRAFTING)
        player.opLoc1(loc)
        advance(8)
        assertEquals(0, player.inv.count(EssencePouch.PURE_ESSENCE))
        assertEquals(5, player.inv.count("obj.earthrune"))
        assertEquals(xp + 65, player.statMap.getFineXP(RUNECRAFTING))
    }

    @Test
    fun GameTestState.`a boost expiring before conversion preserves the essence and experience`() = runAltarTest(level = 4) { loc, _ ->
        player.setCurrentLevel(RUNECRAFTING, 5)
        player.inv[0] = InvObj(EssencePouch.PURE_ESSENCE, 1)
        val xp = player.statMap.getFineXP(RUNECRAFTING)
        player.opLoc1(loc)
        advance(1)
        player.setCurrentLevel(RUNECRAFTING, 4)
        advance(7)
        assertEquals(1, player.inv.count(EssencePouch.PURE_ESSENCE))
        assertEquals(0, player.inv.count(WATER))
        assertEquals(xp, player.statMap.getFineXP(RUNECRAFTING))
    }

    private fun GameTestState.runAltarTest(
        rune: String = WATER,
        level: Int = 27,
        test: GameTestScope.(BoundLocInfo, RunecraftingAltarsRow) -> Unit,
    ) = runGameTest(AltarEvents::class) {
        val altar = RunecraftingAltarsRow.all().first { row ->
            row.rune.output.internalName == rune && row.rune.input.any { it.internalName == EssencePouch.PURE_ESSENCE }
        }
        val loc = placeMapLoc(CoordGrid(3501, 4837), altar.altarObject.internalName)
        player.placeAt(CoordGrid(3500, 4837))
        player.setBaseLevel(RUNECRAFTING, level)
        player.setCurrentLevel(RUNECRAFTING, level)
        test(loc, altar)
    }

    private var Player.testBloodEssenceCharges by intVarBit("varbit.blood_essence_charges")

    private companion object {
        const val WATER = "obj.waterrune"
        const val RUNECRAFTING = "stat.runecrafting"
    }
}
