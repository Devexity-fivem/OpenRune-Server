package org.rsmod.content.bosses.wintertodt

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.SequenceServerType
import jakarta.inject.Inject
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.objParam
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invAddOrDrop
import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.firemakingLvl
import org.rsmod.api.player.stat.fletchingLvl
import org.rsmod.api.player.stat.herbloreLvl
import org.rsmod.api.player.stat.woodcuttingLvl
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onArea
import org.rsmod.api.script.onAreaExit
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.bosses.wintertodt.WintertodtManager.Brazier
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.type.getInvObj
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class WintertodtScript
@Inject
constructor(
    private val manager: WintertodtManager,
    private val objRepo: ObjRepository,
    private val eventBus: EventBus,
    private val random: GameRandom,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<GameLifecycle.LateCycle> { manager.tick() }

        onArea("area.wintertodt_prison") { enterArena() }
        onAreaExit("area.wintertodt_prison") { exitArena() }

        onOpLoc1("loc.wint_brazier") { lightBrazier(it.loc) }
        onOpLoc1("loc.wint_brazier_lit") { feedLoop(it.loc) }
        onOpLoc1("loc.wint_brazier_broken") { repairBrazier(it.loc) }
        onOpLocU("loc.wint_brazier_lit", "obj.wint_bruma_root") { feedOnce(it.loc, false) }
        onOpLocU("loc.wint_brazier_lit", "obj.wint_bruma_kindling") { feedOnce(it.loc, true) }

        onOpLoc1("loc.wint_roots") { chopRoots(it.loc, false) }
        onOpLoc1("loc.wint_herb_roots") { chopRoots(it.loc, true) }

        onOpLoc1("loc.wint_chest_tinderbox") { takeSupply("obj.tinderbox") }
        onOpLoc1("loc.wint_chest_axe") { takeSupply("obj.bronze_axe") }
        onOpLoc1("loc.wint_chest_hammer") { takeSupply("obj.hammer") }
        onOpLoc1("loc.wint_chest_knife") { takeSupply("obj.knife") }
        onOpLoc1("loc.wint_chest_vial") { takeSupply("obj.wint_vial") }

        onOpHeldU("obj.knife", "obj.wint_bruma_root") { fletchKindling() }
        onOpHeldU("obj.wint_herb", "obj.wint_vial") { brewPotion() }

        for (potion in POTIONS) {
            onOpNpcU(npcType("npc.wint_wizard_down"), objType(potion)) {
                healPyro(it.npc, potion)
            }
        }
        onOpNpc1("npc.wint_wizard_down") { helpPyro(it.npc) }
        onOpNpc1("npc.wint_brewma") { brewmaHelp() }

        onOpHeld1("obj.wint_potion4") { drinkPotion("obj.wint_potion4") }
        onOpHeld1("obj.wint_potion3") { drinkPotion("obj.wint_potion3") }
        onOpHeld1("obj.wint_potion2") { drinkPotion("obj.wint_potion2") }
        onOpHeld1("obj.wint_potion1") { drinkPotion("obj.wint_potion1") }

        onOpHeld1("obj.wint_reward_box") { openRewardBox() }
    }

    private fun ProtectedAccess.enterArena() {
        player.ifOpenOverlay("interface.wint_status", eventBus)
        if (player.warmth <= 0) {
            player.warmth = WintertodtManager.MAX_WARMTH
        }
        manager.sendStatus(player)
    }

    private fun ProtectedAccess.exitArena() {
        player.ifCloseOverlay("interface.wint_status", eventBus)
        player.warmth = 0
    }

    private fun ProtectedAccess.lightBrazier(loc: BoundLocInfo) {
        val brazier = manager.brazierAt(loc.coords) ?: return
        if (brazier.state != Brazier.State.UNLIT) return
        val index = manager.brazierIndex(brazier)
        if (manager.pyroDown(index)) {
            mes("The pyromancer is incapacitated; they cannot maintain this brazier.")
            return
        }
        if (!hasItem("obj.tinderbox")) {
            mes("You need a tinderbox to light the brazier.")
            return
        }
        anim("seq.wint_light_brazier_bruma")
        manager.lightBrazier(brazier)
        player.wintertodtPoints += WintertodtManager.POINTS_LIGHT
        statAdvance("stat.firemaking", player.firemakingLvl * 6.0)
        spam("You light the brazier.")
        manager.sendStatus(player)
    }

    private suspend fun ProtectedAccess.feedLoop(loc: BoundLocInfo) {
        while (true) {
            val brazier = manager.brazierAt(loc.coords) ?: return
            if (brazier.state != Brazier.State.LIT) return
            val kindling = hasItem("obj.wint_bruma_kindling")
            val root = hasItem("obj.wint_bruma_root")
            if (!kindling && !root) {
                spam("You have no bruma roots or kindling to burn.")
                return
            }
            anim("seq.wint_light_brazier_bruma")
            if (kindling) {
                player.invDel(player.inv, "obj.wint_bruma_kindling", count = 1)
                manager.feedBrazier(true)
                player.wintertodtPoints += WintertodtManager.POINTS_KINDLING
                statAdvance("stat.firemaking", player.firemakingLvl * 3.8)
            } else {
                player.invDel(player.inv, "obj.wint_bruma_root", count = 1)
                manager.feedBrazier(false)
                player.wintertodtPoints += WintertodtManager.POINTS_ROOT
                statAdvance("stat.firemaking", player.firemakingLvl * 3.0)
            }
            spam("You feed the brazier.")
            manager.sendStatus(player)
            delay(2)
        }
    }

    private fun ProtectedAccess.feedOnce(loc: BoundLocInfo, kindling: Boolean) {
        val brazier = manager.brazierAt(loc.coords) ?: return
        if (brazier.state != Brazier.State.LIT) return
        val obj = if (kindling) "obj.wint_bruma_kindling" else "obj.wint_bruma_root"
        if (!player.invDel(player.inv, obj, count = 1).success) return
        anim("seq.wint_light_brazier_bruma")
        manager.feedBrazier(kindling)
        player.wintertodtPoints +=
            if (kindling) WintertodtManager.POINTS_KINDLING else WintertodtManager.POINTS_ROOT
        statAdvance("stat.firemaking", player.firemakingLvl * if (kindling) 3.8 else 3.0)
        spam("You feed the brazier.")
        manager.sendStatus(player)
    }

    private fun ProtectedAccess.repairBrazier(loc: BoundLocInfo) {
        val brazier = manager.brazierAt(loc.coords) ?: return
        if (brazier.state != Brazier.State.BROKEN) return
        if (!hasItem("obj.hammer")) {
            mes("You need a hammer to repair the brazier.")
            return
        }
        anim("seq.human_smithing")
        manager.repairBrazier(brazier)
        player.wintertodtPoints += WintertodtManager.POINTS_REPAIR
        spam("You repair the brazier.")
        manager.sendStatus(player)
    }

    private suspend fun ProtectedAccess.chopRoots(loc: BoundLocInfo, herbs: Boolean) {
        val axe =
            findAxe()
                ?: run {
                    mes("You need an axe to chop the roots.")
                    return
                }
        while (true) {
            if (player.inv.isFull()) {
                mes("Your inventory is too full to hold any more roots.")
                return
            }
            anim(axeAnim(axe))
            delay(3)
            if (player.coords.chebyshevDistance(loc.coords) > 2) return
            if (herbs) {
                player.invAdd(player.inv, "obj.wint_herb", count = 1)
                spam("You get a bruma herb.")
            } else {
                player.invAdd(player.inv, "obj.wint_bruma_root", count = 1)
                spam("You get some bruma roots.")
                statAdvance("stat.woodcutting", player.woodcuttingLvl * 0.3)
            }
        }
    }

    private fun ProtectedAccess.takeSupply(obj: String) {
        player.invAdd(player.inv, obj, count = 1)
    }

    private fun ProtectedAccess.fletchKindling() {
        if (!player.invDel(player.inv, "obj.wint_bruma_root", count = 1).success) return
        anim("seq.human_fletching")
        player.invAdd(player.inv, "obj.wint_bruma_kindling", count = 1)
        statAdvance("stat.fletching", player.fletchingLvl * 0.6)
    }

    private fun ProtectedAccess.brewPotion() {
        if (!hasItem("obj.wint_herb") || !hasItem("obj.wint_vial")) return
        player.invDel(player.inv, "obj.wint_herb", count = 1)
        player.invDel(player.inv, "obj.wint_vial", count = 1)
        player.invAdd(player.inv, "obj.wint_potion4", count = 1)
        statAdvance("stat.herblore", player.herbloreLvl * 10.0)
        mes("You brew the herb into a rejuvenation potion.")
    }

    private fun ProtectedAccess.healPyro(npc: Npc, potion: String) {
        val index = manager.pyroIndexOf(npc)
        if (index < 0 || !manager.pyroDown(index)) return
        if (!player.invDel(player.inv, potion, count = 1).success) return
        downgradePotion(potion)
        manager.healPyromancer(index)
        player.wintertodtPoints += WintertodtManager.POINTS_HEAL_PYRO
        statAdvance("stat.herblore", player.herbloreLvl * 30.0)
        spam("The pyromancer is rejuvenated.")
        manager.sendStatus(player)
    }

    private fun ProtectedAccess.helpPyro(npc: Npc) {
        val index = manager.pyroIndexOf(npc)
        if (index < 0) return
        if (!manager.pyroDown(index)) {
            mes("The pyromancer doesn't need help right now.")
            return
        }
        val potion =
            POTIONS.firstOrNull { hasItem(it) }
        if (potion == null) {
            mes("You need a rejuvenation potion to heal the pyromancer.")
            return
        }
        player.invDel(player.inv, potion, count = 1)
        downgradePotion(potion)
        manager.healPyromancer(index)
        player.wintertodtPoints += WintertodtManager.POINTS_HEAL_PYRO
        statAdvance("stat.herblore", player.herbloreLvl * 30.0)
        spam("The pyromancer is rejuvenated.")
        manager.sendStatus(player)
    }

    private fun ProtectedAccess.brewmaHelp() {
        if (hasItem("obj.wint_potion4")) {
            mes("Brew'ma has already given you a potion.")
            return
        }
        player.invAdd(player.inv, "obj.wint_potion4", count = 1)
        mes("Brew'ma hands you a rejuvenation potion.")
    }

    private fun ProtectedAccess.drinkPotion(potion: String) {
        if (!player.invDel(player.inv, potion, count = 1).success) return
        downgradePotion(potion)
        player.warmth = (player.warmth + POTION_WARMTH).coerceAtMost(WintertodtManager.MAX_WARMTH)
        spam("You feel warmer.")
        manager.sendStatus(player)
    }

    private fun ProtectedAccess.downgradePotion(potion: String) {
        val next =
            when (potion) {
                "obj.wint_potion4" -> "obj.wint_potion3"
                "obj.wint_potion3" -> "obj.wint_potion2"
                "obj.wint_potion2" -> "obj.wint_potion1"
                else -> null
            }
        if (next != null) {
            player.invAdd(player.inv, next, count = 1)
        } else {
            player.invAdd(player.inv, "obj.wint_vial", count = 1)
        }
    }

    private fun ProtectedAccess.openRewardBox() {
        if (!player.invDel(player.inv, "obj.wint_reward_box", count = 1).success) return
        mes("You open the supply crate.")
        for (i in 0 until 1 + random.of(3)) {
            rollReward()
        }
    }

    private fun ProtectedAccess.rollReward() {
        val roll = random.of(1000)
        val (obj, count) =
            when {
                roll < 20 -> "obj.wint_burnt_page" to (10 + random.of(30))
                roll < 50 -> "obj.dragon_axe" to 1
                roll < 100 -> "obj.wint_torch" to 1
                roll < 140 -> "obj.wint_reroll_box" to 1
                roll < 200 -> "obj.magic_logs" to (10 + random.of(20))
                roll < 300 -> "obj.ranarr_seed" to (1 + random.of(3))
                roll < 400 -> "obj.snapdragon_seed" to (1 + random.of(3))
                roll < 500 -> "obj.uncut_diamond" to (2 + random.of(5))
                roll < 600 -> "obj.uncut_ruby" to (3 + random.of(8))
                roll < 700 -> "obj.coal" to (10 + random.of(20))
                roll < 800 -> "obj.iron_ore" to (10 + random.of(30))
                roll < 900 -> "obj.silver_ore" to (10 + random.of(30))
                roll < 970 -> "obj.coins" to (10000 + random.of(20000))
                else -> "obj.wint_pyro_set" to 1
            }
        player.invAddOrDrop(objRepo, obj, count)
    }

    private fun ProtectedAccess.hasItem(obj: String): Boolean =
        player.inv.any { it != null && getInvObj(it).internalName == obj }

    private fun ProtectedAccess.findAxe(): InvObj? {
        val worn = player.righthand
        if (worn != null && getInvObj(worn).isContentType("content.woodcutting_axe")) {
            return worn
        }
        return player.inv.firstOrNull {
            it != null && getInvObj(it).isContentType("content.woodcutting_axe")
        }
    }

    private fun axeAnim(axe: InvObj): String =
        RSCM.getReverseMapping(RSCMType.SEQ, getInvObj(axe).axeAnimParam.id)

    private companion object {
        const val POTION_WARMTH = 300

        val POTIONS =
            listOf("obj.wint_potion4", "obj.wint_potion3", "obj.wint_potion2", "obj.wint_potion1")

        fun npcType(internal: String) =
            requireNotNull(ServerCacheManager.getNpc(internal.asRSCM(RSCMType.NPC))) {
                "Missing npc type: $internal"
            }

        fun objType(internal: String) =
            requireNotNull(ServerCacheManager.getItem(internal.asRSCM(RSCMType.OBJ))) {
                "Missing obj type: $internal"
            }

        val ItemServerType.axeAnimParam: SequenceServerType by objParam(params.skill_anim)
    }
}
