package org.rsmod.content.quest.area.varrock.dragonslayer

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.death.NpcDeathDropContext
import org.rsmod.api.death.NpcDeathDropHook
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.player.output.mes
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

internal class DragonSlayerDrops @Inject constructor(private val objRepo: ObjRepository) :
    NpcDeathKillHook {

    override fun onKill(context: NpcDeathKillContext) {
        val player = context.hero
        val npc = context.npc
        if (npc.isType("npc.wormbrain")) {
            if (!player.hasAnywhere(MapPart2)) {
                objRepo.add(MapPart2, context.dropCoords, constants.lootdrop_duration, player)
            }
            return
        }
        if (npc.isType("npc.elvarg_alive") ||
            npc.isType("npc.elvarg") ||
            npc.isType("npc.dragonslayer_elvarg_cutscene")
        ) {
            elvargKilled(context)
        }
    }

    private fun elvargKilled(context: NpcDeathKillContext) {
        val player = context.hero
        player.mes("You cut off Elvarg's head as proof of your victory!")
        objRepo.add(
            "obj.dragon_slayer_qip_elvargs_head",
            context.dropCoords,
            constants.lootdrop_duration,
            player,
        )
        if (player.dragonQuest < DragonSlayer.StageElvargSlain) {
            player.dragonQuest = DragonSlayer.StageElvargSlain
        }
    }

    private fun Player.hasAnywhere(obj: String): Boolean =
        inv.contains(obj) || invMap.getOrPut("inv.bank").contains(obj)

    private companion object {
        private const val MapPart2 = "obj.mappart2"
    }
}

internal class DragonSlayerWormbrainTable : NpcDeathDropHook {
    override fun tryConsume(context: NpcDeathDropContext): Boolean = false

    override fun suppressesDropTable(context: NpcDeathKillContext): Boolean =
        context.npc.isType("npc.wormbrain")
}

internal class DragonSlayerWormbrainGate @Inject constructor(private val objRepo: ObjRepository) :
    NpcAttackValidateHook {

    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        if (!npc.isType("npc.wormbrain")) return NpcAttackValidateResult.Pass
        if (player.hasAnywhere("obj.mappart2") || player.hasAnywhere("obj.dragonmap")) {
            return NpcAttackValidateResult.Deny(
                "You have already taken Wormbrain's map piece. There is no use in " +
                    "beating him up further."
            )
        }
        if (mapDropNear(npc)) {
            return NpcAttackValidateResult.Deny(
                "You have just beaten Wormbrain up. Give the poor goblin a break."
            )
        }
        return NpcAttackValidateResult.Pass
    }

    private fun mapDropNear(npc: Npc): Boolean =
        objRepo.findAll(npc.coords).any { it.type == "obj.mappart2".asRSCM(RSCMType.OBJ) }

    private fun Player.hasAnywhere(obj: String): Boolean =
        inv.contains(obj) || invMap.getOrPut("inv.bank").contains(obj)
}
