package org.rsmod.content.skills.runecrafting

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.api.table.runecrafting.RunecraftingAltarsRow
import org.rsmod.api.testing.GameTestState
import org.rsmod.content.skills.runecrafting.action.RunecraftAction.craftCombination
import org.rsmod.content.skills.runecrafting.altar.ComboEvents
import org.rsmod.content.skills.runecrafting.essencepouch.EssencePouch
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid

@Execution(ExecutionMode.SAME_THREAD)
class CombinationRuneInventoryTest {
    @Test
    fun GameTestState.`failed combinations consume ingredients but preserve extracts and grant no experience`() = runGameTest(ComboEvents::class) {
        val altar = RunecraftingAltarsRow.all().first { it.rune.output.internalName == "obj.waterrune" }
        val combo = altar.combo.first { it.input != null && it.output != null && it.talisman != null }
        val input = requireNotNull(combo.input).internalName
        val output = requireNotNull(combo.output).internalName
        val talisman = requireNotNull(combo.talisman).internalName
        player.placeAt(CoordGrid(3500, 4837))
        player.setBaseLevel("stat.runecrafting", 99)
        player.setCurrentLevel("stat.runecrafting", 99)
        repeat(2) { player.inv[it] = InvObj(EssencePouch.PURE_ESSENCE, 1) }
        player.inv[2] = InvObj(input, 2)
        player.inv[3] = InvObj(talisman, 1)
        player.inv[4] = InvObj("obj.scar_extract_twisted", 2)
        random.next = 99
        random.then = 99
        val xp = player.statMap.getFineXP("stat.runecrafting")
        player.withProtectedAccess { craftCombination(combo, XpModifiers(emptySet())) }
        advance(8)
        assertEquals(0, player.inv.count(EssencePouch.PURE_ESSENCE))
        assertEquals(0, player.inv.count(input))
        assertEquals(0, player.inv.count(talisman))
        assertEquals(2, player.inv.count("obj.scar_extract_twisted"))
        assertEquals(0, player.inv.count(output))
        assertEquals(xp, player.statMap.getFineXP("stat.runecrafting"))
    }

    @Test
    fun GameTestState.`only successful combinations consume extracts and receive experience`() = runGameTest(ComboEvents::class) {
        val altar = RunecraftingAltarsRow.all().first { it.rune.output.internalName == "obj.waterrune" }
        val combo = altar.combo.first { it.input != null && it.output != null && it.talisman != null }
        val input = requireNotNull(combo.input).internalName
        val output = requireNotNull(combo.output).internalName
        val talisman = requireNotNull(combo.talisman).internalName
        player.placeAt(CoordGrid(3500, 4837))
        player.setBaseLevel("stat.runecrafting", 99)
        player.setCurrentLevel("stat.runecrafting", 99)
        repeat(2) { player.inv[it] = InvObj(EssencePouch.PURE_ESSENCE, 1) }
        player.inv[2] = InvObj(input, 2)
        player.inv[3] = InvObj(talisman, 1)
        player.inv[4] = InvObj("obj.scar_extract_twisted", 2)
        random.next = 0
        random.then = 99
        val xp = player.statMap.getFineXP("stat.runecrafting")
        player.withProtectedAccess { craftCombination(combo, XpModifiers(emptySet())) }
        advance(8)
        assertEquals(0, player.inv.count(EssencePouch.PURE_ESSENCE))
        assertEquals(0, player.inv.count(input))
        assertEquals(0, player.inv.count(talisman))
        assertEquals(1, player.inv.count("obj.scar_extract_twisted"))
        assertEquals(61, player.inv.count(output))
        assertEquals(xp + requireNotNull(combo.xp), player.statMap.getFineXP("stat.runecrafting"))
    }

    @Test
    fun GameTestState.`combination output overflow preserves every input and experience`() = runGameTest(ComboEvents::class) {
        val altar = RunecraftingAltarsRow.all().first { it.rune.output.internalName == "obj.waterrune" }
        val combo = altar.combo.first { it.input != null && it.output != null && it.talisman != null }
        val input = requireNotNull(combo.input).internalName
        val output = requireNotNull(combo.output).internalName
        val talisman = requireNotNull(combo.talisman).internalName
        player.placeAt(CoordGrid(3500, 4837))
        player.setBaseLevel("stat.runecrafting", 99)
        player.setCurrentLevel("stat.runecrafting", 99)
        player.inv[0] = InvObj(EssencePouch.PURE_ESSENCE, 1)
        player.inv[1] = InvObj(input, 1)
        player.inv[2] = InvObj(talisman, 1)
        player.inv[3] = InvObj(output, Int.MAX_VALUE)
        random.next = 0
        random.then = 0
        val xp = player.statMap.getFineXP("stat.runecrafting")
        player.withProtectedAccess { craftCombination(combo, XpModifiers(emptySet())) }
        advance(8)
        assertEquals(1, player.inv.count(EssencePouch.PURE_ESSENCE))
        assertEquals(1, player.inv.count(input))
        assertEquals(1, player.inv.count(talisman))
        assertEquals(Int.MAX_VALUE, player.inv.count(output))
        assertEquals(xp, player.statMap.getFineXP("stat.runecrafting"))
    }

    @Test
    fun GameTestState.`combination extracts apply per essence without providing experience`() = runGameTest(ComboEvents::class) {
        val altar = RunecraftingAltarsRow.all().first { it.rune.output.internalName == "obj.waterrune" }
        val combo = altar.combo.first { it.input != null && it.output != null && it.talisman != null }
        val input = requireNotNull(combo.input).internalName
        val output = requireNotNull(combo.output).internalName
        val talisman = requireNotNull(combo.talisman).internalName
        player.placeAt(CoordGrid(3500, 4837))
        player.setBaseLevel("stat.runecrafting", 99)
        player.setCurrentLevel("stat.runecrafting", 99)
        repeat(2) { player.inv[it] = InvObj(EssencePouch.PURE_ESSENCE, 1) }
        player.inv[2] = InvObj(input, 2)
        player.inv[3] = InvObj(talisman, 1)
        player.inv[4] = InvObj("obj.scar_extract_twisted", 2)
        random.next = 0
        random.then = 0
        val xp = player.statMap.getFineXP("stat.runecrafting")
        player.withProtectedAccess { craftCombination(combo, XpModifiers(emptySet())) }
        advance(8)
        assertEquals(0, player.inv.count(EssencePouch.PURE_ESSENCE))
        assertEquals(0, player.inv.count(input))
        assertEquals(0, player.inv.count(talisman))
        assertEquals(0, player.inv.count("obj.scar_extract_twisted"))
        assertEquals(122, player.inv.count(output))
        assertEquals(xp + requireNotNull(combo.xp) * 2, player.statMap.getFineXP("stat.runecrafting"))
    }
}
