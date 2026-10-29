package org.rsmod.content.bosses.maggotking

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dtx.core.ArgMap
import dtx.core.RollResult
import dtx.core.flatten
import jakarta.inject.Inject
import java.text.NumberFormat
import java.util.Locale
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.dropRollable
import org.rsmod.api.droptable.nothingDrop
import org.rsmod.api.droptable.rollCount
import org.rsmod.api.droptable.rsPlayerPrerollTable
import org.rsmod.api.droptable.rsPlayerTertiaryTable
import org.rsmod.api.droptable.rsPlayerWeightedTable
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.invtx.invAddOrDrop
import org.rsmod.api.invtx.invDel
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.ClientScripts
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.drops.clueScrollTransformObj
import org.rsmod.content.interfaces.collectionlog.CollectionLog
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The corpse left after a kill offers the OSRS loot fork: "Open-stomach" rolls the normal drop
 * table, "Take-eggs" forgoes it for a maggot egg (the pet's boosted route). Either op consumes
 * the corpse and starts the nine-tick respawn.
 */
class MaggotKingLoot
@Inject
constructor(
    private val deps: BossDeps,
    private val instances: InstanceManager,
    private val objRepo: ObjRepository,
    private val aiPlayerInteractions: AiPlayerInteractions,
) : PluginScript() {
    private val Player.lootNotificationOn by boolVarBit("varbit.option_lootnotification_on")
    private val Player.lootNotificationUntradeables by
        boolVarBit("varbit.option_lootnotification_untradeables")
    private val Player.lootNotificationValue by intVarBit("varbit.option_lootnotification_value")

    override fun ScriptContext.startup() {
        onOpNpc1(CORPSE) { loot(it.npc, ::openStomach) }
        onOpNpc3(CORPSE) { loot(it.npc, ::takeEggs) }
        for (egg in EGGS.keys) {
            onOpHeld1(egg) { popEgg(egg) }
        }
    }

    private suspend fun ProtectedAccess.loot(corpse: Npc, roll: (Player, Npc) -> List<RolledLoot>) {
        if (corpse.vars[LOOTED] and LOOT_BIT != 0) return
        corpse.vars[LOOTED] = corpse.vars[LOOTED] or LOOT_BIT
        for (item in roll(player, corpse)) {
            give(player, corpse.type.id, item)
        }
        despawnAndRespawn(player, corpse)
    }

    private fun despawnAndRespawn(player: Player, corpse: Npc) {
        val spawn = corpse.spawnCoords
        val instanceId = instances.instanceForNpc(corpse)
        deps.npcRepo.del(corpse, Int.MAX_VALUE)
        deps.worldQueues.add(RESPAWN_TICKS) {
            if (instanceId == null || instances.sessionForId(instanceId) == null) return@add
            val type = ServerCacheManager.getNpc(BOSS_TYPE.asRSCM(RSCMType.NPC)) ?: return@add
            val revived = Npc(type, spawn)
            deps.npcRepo.add(revived, Int.MAX_VALUE)
            instances.registerSessionNpc(player, revived)
            if (player.isSlotAssigned) {
                revived.apPlayer2(player, aiPlayerInteractions)
            }
        }
    }

    private fun give(player: Player, npcId: Int, rolled: RolledLoot) {
        val item = rolled.item
        if (item.isNothing || !item.condition(player)) return
        val obj = item.transformObj(player) ?: item.obj
        if (obj.isEmpty()) return
        val type = ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ)) ?: return
        val count = item.rollCount(deps.random)
        CollectionLog.grant(player, obj, count)
        notifyValuableDrop(player, type.name, type.cost, type.tradeable, count)
        player.invAddOrDrop(objRepo, obj, count)
        ClientScripts.lootTrackerAddLoot(player, npcId, LOOT_EVENT, type.id, count)
    }

    private fun notifyValuableDrop(player: Player, name: String, cost: Int, tradeable: Boolean, count: Int) {
        if (!player.lootNotificationOn) return
        if (!tradeable && !player.lootNotificationUntradeables) return
        val value = cost.toLong() * count
        val threshold = player.lootNotificationValue
        if (threshold > 0 && value < threshold) return
        player.mes(
            "<col=ef1020>Valuable drop: $count x $name (${NUMBER.format(value)} coins)</col>",
            ChatType.GameMessage,
        )
    }

    private fun openStomach(player: Player, corpse: Npc): List<RolledLoot> {
        val rolled = mutableListOf<RolledLoot>()
        rolled += rollTable(UNIQUES, player)
        rolled += rollTable(MAIN, player)
        if (corpse.vars[LOOTED] and KONAR_BIT != 0 && deps.random.of(BRIMSTONE_RATE) == 0) {
            rolled += RolledLoot(DropRollItem("obj.konar_key", 1))
        }
        rolled += rollTable(TERTIARY, player)
        return rolled
    }

    // UNVERIFIED: the supply-bundle weight of an egg take is not published; mirrored to the
    // open-stomach supply rate (41/159 ~= 387/1500) so either op yields supplies at parity.
    private fun takeEggs(player: Player, corpse: Npc): List<RolledLoot> {
        val rolled = rollTable(EGG_TAKE, player)
        if (rolled.none { !it.item.isNothing }) {
            player.mes(EGG_POP_MESSAGE, ChatType.GameMessage)
        }
        return rolled + rollTable(EGG_TERTIARY, player)
    }

    private suspend fun ProtectedAccess.popEgg(egg: String) {
        if (!player.invDel(player.inv, egg, count = 1).success) return
        val petOdds = EGGS[egg] ?: return
        if (deps.random.of(petOdds) == 0) {
            CollectionLog.grant(player, PET, 1)
            player.invAddOrDrop(objRepo, PET, 1)
            player.mes(PET_MESSAGE, ChatType.GameMessage)
        } else {
            mes(EGG_POP_MESSAGE)
        }
    }

    private fun rollTable(
        table: dtx.table.Table<Player, DropRollItem>,
        player: Player,
    ): List<RolledLoot> {
        val result = table.roll(player, ArgMap()).flatten()
        return when (result) {
            is RollResult.Nothing -> emptyList()
            is RollResult.Single -> listOf(RolledLoot(result.result))
            is RollResult.ListOf -> result.results.map(::RolledLoot)
        }
    }

    private class RolledLoot(val item: DropRollItem)

    companion object {
        private const val CORPSE = "npc.maggot_king_corpse"
        private const val BOSS_TYPE = "npc.maggot_king"
        private const val PET = "obj.maggotkingpet"
        private const val RESPAWN_TICKS = 9
        private const val LOOT_EVENT = 0
        private const val BRIMSTONE_RATE = 50
        private const val EGG_POP_MESSAGE = "The eggs pop as you try to take them."
        private const val PET_MESSAGE = "Something wriggles free of the egg and takes a liking to you!"
        private val NUMBER = NumberFormat.getIntegerInstance(Locale.UK)

        internal const val LOOTED = "varn.maggot_king_looted"
        internal const val LOOT_BIT = 1
        internal const val KONAR_BIT = 2

        // Pop odds per egg type, each contributing an equal ~1/6 of the blended egg-take pet
        // rate; the total works out to ~1/1502.5 against the wiki's published 1/1502.4.
        private val EGGS =
            mapOf(
                "obj.maggot_egg" to 1800,
                "obj.sickly_maggot_egg" to 900,
                "obj.warm_maggot_egg" to 200,
                "obj.pulsating_maggot_egg" to 36,
                "obj.wriggling_maggot_egg" to 18,
                "obj.writhing_maggot_egg" to 6,
            )

        private val UNIQUES =
            rsPlayerPrerollTable {
                1 outOf 340 weight "obj.elder_venator_fang" count 1
                1 outOf 520 weight "obj.crimson_kisten" count 1
            }

        private val MAIN =
            rsPlayerWeightedTable(total = 159) {
                name("Maggot King")
                41 weight
                    dropRollable(
                        DropRollItem(
                            "obj.stymphike_tartare",
                            2,
                            4,
                            bonusDrops = listOf(DropRollItem("obj.dull_zarosian_medal", 1)),
                        )
                    )
                15 weight "obj.cert_uncut_ruby" count 10..13
                10 weight "obj.stymphike_feather" count 51..74
                10 weight "obj.cert_mort_myre_fungus" count 50..76
                10 weight "obj.letvek" count 23..34
                10 weight "obj.cert_adamantite_ore" count 30..40
                5 weight "obj.cert_runite_ore" count 12..15
                5 weight "obj.blood_sac" count 8..11
                5 weight "obj.cert_gold_ore" count 24..32
                5 weight "obj.cert_mithril_ore" count 36..41
                4 weight "obj.orikalkum_gravel" count 4..5
                2 weight "obj.venator_fang" count 112..179
                2 weight "obj.venator_tooth" count 178..219
                12 weight "obj.tarnished_spear" count 1
                6 weight "obj.tarnished_ring" count 1
                3 weight "obj.tarnished_bracelet" count 1
                3 weight "obj.tarnished_necklace" count 1
                3 weight "obj.tarnished_amulet" count 1
                2 weight "obj.tarnished_battleaxe" count 1
                2 weight "obj.tarnished_longsword" count 1
                2 weight "obj.tarnished_halberd" count 1
                2 weight "obj.tarnished_2h_sword" count 1
            }

        private val TERTIARY =
            rsPlayerTertiaryTable {
                1 outOf 3500 weight "obj.maggotkingpet" count 1
                1 outOf 40 weight "obj.trail_elite_emote_exp1" count 1 transformObj { player ->
                    player.clueScrollTransformObj("obj.trail_elite_emote_exp1")
                }
            }

        private val EGG_TAKE =
            rsPlayerWeightedTable(total = 1500) {
                name("Maggot King egg take")
                300 weight "obj.maggot_egg" count 1
                150 weight "obj.sickly_maggot_egg" count 1
                33 weight "obj.warm_maggot_egg" count 1
                6 weight "obj.pulsating_maggot_egg" count 1
                3 weight "obj.wriggling_maggot_egg" count 1
                1 weight "obj.writhing_maggot_egg" count 1
                387 weight
                    dropRollable(
                        DropRollItem(
                            "obj.stymphike_tartare",
                            2,
                            4,
                            bonusDrops = listOf(DropRollItem("obj.dull_zarosian_medal", 1)),
                        )
                    )
                620 weight dropRollable(nothingDrop { true })
            }

        private val EGG_TERTIARY =
            rsPlayerTertiaryTable {
                1 outOf 40 weight "obj.trail_elite_emote_exp1" count 1 transformObj { player ->
                    player.clueScrollTransformObj("obj.trail_elite_emote_exp1")
                }
            }
    }
}
