package org.rsmod.content.bosses.zulrah

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.content.bosses.zulrah.ZulrahRotations.Act
import org.rsmod.content.bosses.zulrah.ZulrahRotations.Form
import org.rsmod.content.bosses.zulrah.ZulrahRotations.Pos
import org.rsmod.content.bosses.zulrah.ZulrahRotations.ROTATIONS
import org.rsmod.content.bosses.zulrah.ZulrahRotations.Style

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class ZulrahSpecTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun loadCache() {
            ServerCacheManager.init(240)
        }
    }

    @Test
    fun `spec symbols resolve`() {
        for (npc in
            listOf(
                "npc.snakeboss_boss_ranged",
                "npc.snakeboss_boss_melee",
                "npc.snakeboss_boss_magic",
                "npc.snakeboss_minion_melee",
                "npc.snakeboss_minion_magic",
            )) {
            assertDoesNotThrow { npc.asRSCM(RSCMType.NPC) }
        }
        for (seq in
            listOf(
                "seq.snakeboss_emergefast",
                "seq.snakeboss_sinkfast",
                "seq.snakeboss_death",
                "seq.snakeboss_attack_acidx1",
                "seq.snakeboss_attack_acidx3",
                "seq.snakeboss_attack_tail_left",
                "seq.snakeboss_attack_tail_right",
            )) {
            assertDoesNotThrow { seq.asRSCM(RSCMType.SEQ) }
        }
        for (spot in
            listOf(
                "spotanim.snakeboss_orb",
                "spotanim.snakeboss_double_orb",
            )) {
            assertDoesNotThrow { spot.asRSCM(RSCMType.SPOTANIM) }
        }
        assertDoesNotThrow { "loc.snakeboss_poisoncloud".asRSCM(RSCMType.LOC) }
    }

    @Test
    fun `zulrah stats match wiki`() {
        for (form in Form.entries) {
            val boss = ServerCacheManager.getNpc(form.npc.asRSCM(RSCMType.NPC))
            assertEquals(725, boss?.combatLevel, form.npc)
        }
    }

    @Test
    fun `four rotations start green in the middle`() {
        assertEquals(4, ROTATIONS.size)
        for (rotation in ROTATIONS) {
            val first = rotation.first()
            assertEquals(Pos.MIDDLE, first.pos)
            assertEquals(Form.GREEN, first.form)
            assertTrue(first.acts.any { it is Act.Clouds })
        }
    }

    @Test
    fun `emerge positions match captured deltas`() {
        // RSProx captures put the side pools at (0,-11) and (+-10,-2) off the
        // opening spawn in the middle pool.
        val middle = Pos.MIDDLE.tile
        assertEquals(middle.translate(0, -11), Pos.SOUTH.tile)
        assertEquals(middle.translate(-10, -2), Pos.WEST.tile)
        assertEquals(middle.translate(10, -2), Pos.EAST.tile)
    }

    @Test
    fun `rotation phase shapes match osrs`() {
        assertEquals(11, ROTATIONS[0].size)
        assertEquals(11, ROTATIONS[1].size)
        assertEquals(12, ROTATIONS[2].size)
        assertEquals(13, ROTATIONS[3].size)

        // Every rotation closes on the shared middle-green ranged+clouds phase
        // that counts as the first phase of the next rotation.
        for (rotation in ROTATIONS) {
            val last = rotation.last()
            assertEquals(Pos.MIDDLE, last.pos)
            assertEquals(Form.GREEN, last.form)
            val attacks = last.acts.filterIsInstance<Act.Attack>()
            assertEquals(1, attacks.size)
            assertEquals(Style.RANGED, attacks.single().style)
            assertEquals(5, attacks.single().count)
            assertTrue(last.acts.any { it is Act.Clouds })
        }

        for (i in 0..1) {
            val jad = ROTATIONS[i].flatMap { it.acts }.filterIsInstance<Act.Jad>().single()
            assertEquals(Style.RANGED, jad.start)
            assertEquals(10, jad.count)
        }

        val serpJad = ROTATIONS[2].flatMap { it.acts }.filterIsInstance<Act.Jad>().single()
        assertEquals(Style.MAGIC, serpJad.start)
        assertEquals(10, serpJad.count)
        val tanzJad = ROTATIONS[3].flatMap { it.acts }.filterIsInstance<Act.Jad>().single()
        assertEquals(Style.MAGIC, tanzJad.start)
        assertEquals(8, tanzJad.count)
    }
}
