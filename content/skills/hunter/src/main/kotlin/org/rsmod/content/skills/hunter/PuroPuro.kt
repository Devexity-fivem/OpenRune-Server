package org.rsmod.content.skills.hunter

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import kotlin.math.abs
import org.rsmod.api.config.constants
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.invtx.invDel
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.strengthLvl
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.player.stat.thievingLvl
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onNpcQueueWithArgs
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc5
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.api.table.hunter.HunterImplingsRow
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

class PuroPuro
@Inject
constructor(
    private val puro: PuroPuroManager,
    private val npcRepo: NpcRepository,
    private val objRepo: ObjRepository,
    private val playerList: PlayerList,
    private val random: GameRandom,
    private val collision: CollisionFlagMap,
) : PluginScript() {

    override fun ScriptContext.startup() {
        for (loc in PUSH_WHEAT) {
            onOpLoc5(loc) { pushThrough(it.loc) }
        }
        onOpLoc1(CROP_CIRCLE_LOC) { enterCircle() }
        onOpLoc1(EXIT_PORTAL) { exitPortal() }
        onOpLoc2(EXIT_PORTAL) {
            mes(
                "A swirling vortex of wheat-scented magical energy, leading back to " +
                    "the field you came through."
            )
        }
        onPlayerCoordsChanged {
            if (puro.circleNear(player.coords)) {
                enterWanderingCircle(player)
            }
        }
        onEvent<NpcStateEvents.Spawn>(DEFENDER_TYPE_ID) {
            npc.queue(DEFENDER_QUEUE, DEFENDER_INTERVAL, 0)
        }
        onNpcQueueWithArgs<Int?>(DEFENDER_TYPE, DEFENDER_QUEUE) { defenderScan() }
        onEvent<GameLifecycle.LateCycle> {
            puro.tick()
            val day = utcDay()
            for (other in playerList) {
                puro.tickAffinity(other)
                if (other.vars[DAILY_JARS_STAMP_VARP] != day) {
                    VarPlayerIntMapSetter.set(other, DAILY_JARS_STAMP_VARP, day)
                    VarPlayerIntMapSetter.set(other, DAILY_JARS_VARBIT, 0)
                }
            }
        }
        puro.startup()
    }

    private suspend fun ProtectedAccess.enterCircle() {
        arriveDelay()
        anim(II_TELEPORT_SEQ)
        delay(1)
        teleport(PuroPuroManager.PURO_ENTRY)
        vars[CIRCLE_ENTERED_VARBIT] = 0
    }

    private fun enterWanderingCircle(eventPlayer: Player) {
        PathingEntityCommon.teleport(eventPlayer, collision, PuroPuroManager.PURO_ENTRY)
        VarPlayerIntMapSetter.set(
            eventPlayer,
            CIRCLE_ENTERED_VARBIT,
            puro.fieldIndex + 1,
        )
        puro.grantAffinity(eventPlayer)
        eventPlayer.mes(
            "You feel a brief burst of farmer's affinity as you slip through the circle."
        )
    }

    private suspend fun ProtectedAccess.exitPortal() {
        arriveDelay()
        anim(II_TELEPORT_SEQ)
        delay(1)
        val entered = vars[CIRCLE_ENTERED_VARBIT]
        val dest =
            if (entered in 1..PuroPuroManager.FIELDS.size) {
                PuroPuroManager.FIELDS[entered - 1].coords
            } else {
                PuroPuroManager.ZANARIS_EXIT
            }
        teleport(dest)
        vars[CIRCLE_ENTERED_VARBIT] = 0
    }

    private suspend fun ProtectedAccess.pushThrough(loc: BoundLocInfo) {
        arriveDelay()
        faceLoc(loc)
        val dx = loc.coords.x - player.coords.x
        val dz = loc.coords.z - player.coords.z
        if (dx == 0 && dz == 0) {
            return
        }
        val stepX = if (abs(dx) >= abs(dz)) dx.coerceIn(-1, 1) else 0
        val stepZ = if (abs(dz) > abs(dx)) dz.coerceIn(-1, 1) else 0
        val dest = loc.coords.translate(stepX, stepZ)
        if (
            !collision.isZoneValid(dest) ||
            collision[dest.x, dest.z, dest.level] and BLOCK_MASK != 0
        ) {
            mes("There's no room to push through here.")
            return
        }
        val outcome = puro.wheatOutcome(player.strengthLvl)
        val affinity = vars[PuroPuroManager.AFFINITY_VARBIT] > 0
        val ticks =
            PUSH_TICKS[outcome] -
                if (affinity) PuroPuroManager.AFFINITY_TICK_REDUCTION else 0
        val seq = if (affinity) PUSH_AFFINITY_SEQS[outcome] else PUSH_SEQS[outcome]
        val dir =
            when {
                stepX > 0 -> constants.em_face_east
                stepX < 0 -> constants.em_face_west
                stepZ > 0 -> constants.em_face_north
                else -> constants.em_face_south
            }
        anim(seq)
        exactMove(player.coords, dest, 0, ticks, dir)
        delay(ticks)
        if (vars[STRENGTH_XP_OFF_VARBIT] == 0) {
            statAdvance(STAT_STRENGTH, PUSH_XP[outcome])
        }
        mes(PUSH_MESSAGES[outcome], ChatType.Spam)
    }

    private fun StandardNpcAccess.defenderScan() {
        if (!PuroPuroManager.inPuro(npc.coords)) {
            return
        }
        val target =
            playerList.firstOrNull { other ->
                PuroPuroManager.inPuro(other.coords) &&
                    other.coords.chebyshevDistance(npc.coords) <= DEFENDER_RANGE &&
                    lowestJarred(other) != null
            }
        if (target != null) {
            npc.facePlayer(target)
            val odds =
                (DEFENDER_BASE_ODDS - target.thievingLvl)
                    .coerceIn(8, DEFENDER_BASE_ODDS)
                    .let { if (target.inv.count(IMP_REPELLENT) > 0) it / 2 else it }
            if (random.of(256) < odds) {
                releaseImpling(target, checkNotNull(lowestJarred(target)))
            } else {
                npc.anim(PICKPOCKET_FAIL_SEQ)
            }
        }
        npc.queue(DEFENDER_QUEUE, DEFENDER_INTERVAL, 0)
    }

    private fun StandardNpcAccess.releaseImpling(target: Player, prey: HunterEvents.ImplingPrey) {
        npc.anim(RELEASE_SEQ)
        npc.say("Be free!")
        target.invDel(target.inv, prey.jarred, 1)
        val dropTile = jarDropTile(target.coords)
        objRepo.add(IMPLING_JAR_TYPE, dropTile, duration = 100)
        val mazeSymbol = prey.npcMazeSymbol ?: prey.npcSymbol
        val type = ServerCacheManager.getNpc(mazeSymbol.asRSCM(RSCMType.NPC)) ?: return
        val freed = Npc(type, dropTile)
        npcRepo.add(freed, RELEASED_LIFE_TICKS)
        puro.markReleased(freed, target)
        target.mes(
            "A cheeky imp defender sneaks up and releases your ${prey.name.lowercase()}!",
            ChatType.GameMessage,
        )
    }

    private fun jarDropTile(near: CoordGrid): CoordGrid {
        for (r in 1..3) {
            for (dx in -r..r) {
                for (dz in -r..r) {
                    if (abs(dx) != r && abs(dz) != r) {
                        continue
                    }
                    val tile = near.translate(dx, dz)
                    if (
                        collision.isZoneValid(tile) &&
                        collision[tile.x, tile.z, tile.level] and BLOCK_MASK == 0
                    ) {
                        return tile
                    }
                }
            }
        }
        return near
    }

    private fun lowestJarred(player: Player): HunterEvents.ImplingPrey? =
        puroImplings
            .filter { it.jarred != CRYSTAL_JAR && player.inv.count(it.jarred) > 0 }
            .minByOrNull { it.level }

    private val puroImplings: List<HunterEvents.ImplingPrey> by lazy {
        HunterImplingsRow.all().map { row ->
            HunterEvents.ImplingPrey(
                npcSymbol = row.npc.internalName,
                npcMazeSymbol = row.npcMaze?.internalName,
                name = ServerCacheManager.getNpc(row.npc.internalName.asRSCM(RSCMType.NPC))?.name
                    ?: row.npc.internalName,
                level = row.level,
                xpPuroTenths = row.xpPuro,
                xpGielinorTenths = row.xpGielinor,
                jarred = row.jarred.internalName,
                respawn = row.respawn,
            )
        }
    }

    companion object {
        const val CROP_CIRCLE_LOC = "loc.ii_crop_circle"
        const val EXIT_PORTAL = "loc.ii_portal"
        const val CIRCLE_ENTERED_VARBIT = "varbit.ii_crop_circle_entered"
        const val DAILY_JARS_VARBIT = "varbit.ii_daily_jars_purchased"
        const val DAILY_JARS_STAMP_VARP = "varp.ii_daily_jars_stamp"
        const val STRENGTH_XP_OFF_VARBIT = "varbit.ii_strength_xp_off"
        const val STAT_STRENGTH = "stat.strength"

        const val II_TELEPORT_SEQ = "seq.ii_human_teleport"
        const val PICKPOCKET_FAIL_SEQ = "seq.ii_imp_pickpocket_fail"
        const val RELEASE_SEQ = "seq.ii_imp_release"
        const val IMPLING_JAR = "obj.ii_impling_jar"
        const val IMP_REPELLENT = "obj.ii_imp_repellent"
        const val CRYSTAL_JAR = "obj.ii_captured_impling_12"
        const val DEFENDER_QUEUE = "queue.ii_defender"

        const val DEFENDER_INTERVAL = 5
        const val DEFENDER_RANGE = 8
        const val DEFENDER_BASE_ODDS = 128
        const val RELEASED_LIFE_TICKS = 3000

        fun utcDay(): Int = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toEpochDay().toInt()

        const val BLOCK_MASK = CollisionFlag.LOC or
            CollisionFlag.BLOCK_WALK or
            CollisionFlag.GROUND_DECOR

        val PUSH_SEQS =
            listOf(
                "seq.ii_human_walk_thru_wheat_fast",
                "seq.ii_human_walk_thru_wheat_mid_speed",
                "seq.ii_human_walk_thru_wheat_slowly",
            )
        val PUSH_AFFINITY_SEQS =
            listOf(
                "seq.ii_human_walk_thru_wheat_fast_affinity",
                "seq.ii_human_walk_thru_wheat_mid_speed_affinity",
                "seq.ii_human_walk_thru_wheat_slowly_affinity",
            )
        val PUSH_TICKS = listOf(6, 8, 10)
        val PUSH_XP = listOf(4.0, 2.0, 0.0)
        val PUSH_MESSAGES =
            listOf(
                "You quickly dash through the wheat.",
                "You push your way through the wheat.",
                "You struggle to push through the wheat.",
            )

        private val PUSH_WHEAT =
            listOf(25016, 25019, 25020, 25021, 25029).map {
                RSCM.getReverseMapping(RSCMType.LOC, it)
            }

        private val DEFENDER_TYPE =
            checkNotNull(
                ServerCacheManager.getNpc("npc.imp_defender_no_patrol".asRSCM(RSCMType.NPC))
            )
        private val DEFENDER_TYPE_ID = DEFENDER_TYPE.id.toLong()
        private val IMPLING_JAR_TYPE =
            checkNotNull(ServerCacheManager.getItem(IMPLING_JAR.asRSCM(RSCMType.OBJ)))
    }
}
