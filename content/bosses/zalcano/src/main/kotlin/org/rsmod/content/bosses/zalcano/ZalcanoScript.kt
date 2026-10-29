package org.rsmod.content.bosses.zalcano

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.combat.manager.PlayerAttackManager
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invDel
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.miningLvl
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onNpcHit
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.content.skills.mining.scripts.Mining
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.hit.HitType
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.type.getInvObj
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class ZalcanoScript
@Inject
constructor(
    private val manager: ZalcanoManager,
    private val playerList: PlayerList,
    private val attackManager: PlayerAttackManager,
    private val npcHitModifier: NpcHitModifier,
    private val random: GameRandom,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<GameLifecycle.LateCycle> { manager.tick() }

        onOpLoc1("loc.zalcano_rock_active") { mineRock(it.loc) }

        onOpLoc1("loc.zalcano_furnace") { refineOre() }

        onOpLoc1("loc.zalcano_altar") { imbueOre() }

        onOpNpc1("npc.zalcano") { attackManager.continueCombat(player, it.npc) }

        onOpNpc1("npc.zalcano_weak") { mineDowned(it.npc) }

        onNpcHit(ZALCANO_TYPE) {
            if (!hit.isFromPlayer || (!hit.isRighthandObj(IMBUED_TEPHRA) && !hit.isSecondaryObj(IMBUED_TEPHRA))) {
                return@onNpcHit
            }
            val attacker = hit.resolvePlayerSource(playerList)
            npc.hitpoints = (npc.hitpoints + hit.damage).coerceAtMost(npc.baseHitpointsLvl)
            if (attacker != null && manager.tephraImpact()) {
                attacker.statAdvance("stat.smithing", TEPHRA_XP)
            }
        }
    }

    private suspend fun ProtectedAccess.mineRock(loc: BoundLocInfo) {
        player.anim("seq.human_mining_zalcano_pickaxe_wall")
        delay(3)
        if (!manager.isInArena(player)) return
        if (player.invAdd(player.inv, "obj.zalcano_ore", count = 1).success) {
            player.statAdvance("stat.mining", miningXp(player.miningLvl))
            player.mes("You manage to mine some Zalcano ore.")
        } else {
            player.mes("Your inventory is too full to hold any more ore.")
        }
    }

    private suspend fun ProtectedAccess.refineOre() {
        if (!player.invDel(player.inv, "obj.zalcano_ore", count = 1).success) return
        delay(1)
        player.invAdd(player.inv, "obj.zalcano_refined_ore", count = 1)
        player.statAdvance("stat.smithing", REFINE_XP)
        player.mes("You refine the ore in the furnace.")
    }

    private suspend fun ProtectedAccess.imbueOre() {
        if (!player.invDel(player.inv, "obj.zalcano_refined_ore", count = 1).success) return
        delay(1)
        player.invAdd(player.inv, "obj.zalcano_imbued_ore", count = 1)
        player.mes("The altar imbues your tephra with power.")
    }

    private suspend fun ProtectedAccess.mineDowned(npc: Npc) {
        val pickaxe = Mining.findPickaxe(player)
        if (pickaxe == null) {
            player.mes("You need a pickaxe to mine Zalcano.")
            return
        }
        val swing = downedMiningAnim(pickaxe)
        while (npc.isSlotAssigned && npc.visType.id == ZALCANO_WEAK_TYPE.id && manager.isDowned()) {
            player.anim(swing)
            delay(Mining.pickaxeActionDelay(pickaxe, random))
            if (!manager.isDowned() || npc.visType.id != ZALCANO_WEAK_TYPE.id) return
            val damage = DOWNED_MINE_MIN + random.of(DOWNED_MINE_VARIANCE)
            npc.queueHit(player, delay = 1, HitType.Typeless, damage, npcHitModifier)
            player.statAdvance("stat.mining", miningXp(player.miningLvl))
        }
    }

    private fun downedMiningAnim(pickaxe: InvObj): String {
        val seq = with(Mining.Companion) { getInvObj(pickaxe).pickaxeAnim }
        return RSCM.getReverseMapping(RSCMType.SEQ, seq.id)
    }

    private fun miningXp(level: Int): Double = MINING_XP_BASE + level / 10.0

    companion object {
        const val MINING_XP_BASE = 40.0
        const val REFINE_XP = 30.0
        const val TEPHRA_XP = 90.0
        const val DOWNED_MINE_MIN = 6
        const val DOWNED_MINE_VARIANCE = 8

        private val IMBUED_TEPHRA =
            ServerCacheManager.getItem("obj.zalcano_imbued_ore".asRSCM(RSCMType.OBJ))
                ?: error("Obj type not found: obj.zalcano_imbued_ore")

        private val ZALCANO_TYPE =
            ServerCacheManager.getNpc("npc.zalcano".asRSCM(RSCMType.NPC))
                ?: error("Npc type not found: npc.zalcano")

        private val ZALCANO_WEAK_TYPE =
            ServerCacheManager.getNpc("npc.zalcano_weak".asRSCM(RSCMType.NPC))
                ?: error("Npc type not found: npc.zalcano_weak")
    }
}
