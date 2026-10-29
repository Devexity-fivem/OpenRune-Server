package org.rsmod.content.bosses.maggotking

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.text.NumberFormat
import java.util.Locale
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.api.db.gateway.GameDbManager
import org.rsmod.api.db.gateway.model.GameDbResult
import org.rsmod.api.db.gateway.model.isOk
import org.rsmod.api.instances.events.InstanceKillTimeEvent
import org.rsmod.api.instances.events.instanceEventId
import org.rsmod.api.instances.timer.formatInstanceKillTime
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpLoc1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

@Singleton
class MaggotKingWorldStats @Inject constructor(private val db: GameDbManager) {
    private val logger = InlineLogger()

    var kills: Long = 0
        private set

    var deaths: Long = 0
        private set

    var bestTicks: Int = 0
        private set

    fun load() = add()

    fun addKill() = add(kills = 1)

    fun addDeath() = add(deaths = 1)

    fun addKillTime(ticks: Int) = add(bestTicks = ticks)

    private fun add(kills: Int = 0, deaths: Int = 0, bestTicks: Int = 0) {
        db.request(
            request = { connection ->
                GameDbResult.Ok(upsert(connection, kills, deaths, bestTicks))
            },
            response = { result ->
                if (result.isOk()) {
                    val (storedKills, storedDeaths, storedBest) = result.value
                    this.kills = storedKills
                    this.deaths = storedDeaths
                    this.bestTicks = storedBest
                } else {
                    logger.warn { "Could not update the Maggot King scoreboard: $result" }
                }
            },
        )
    }

    private fun upsert(
        connection: DatabaseConnection,
        kills: Int,
        deaths: Int,
        bestTicks: Int,
    ): Triple<Long, Long, Int> {
        connection.prepareStatement(CREATE_TABLE).use { it.executeUpdate() }
        return connection.prepareStatement(UPSERT).use { statement ->
            statement.setString(1, KEY)
            statement.setLong(2, kills.toLong())
            statement.setLong(3, deaths.toLong())
            statement.setInt(4, bestTicks)
            statement.executeQuery().use { rs ->
                rs.next()
                Triple(rs.getLong(1), rs.getLong(2), rs.getInt(3))
            }
        }
    }

    private companion object {
        private const val KEY = "maggot_king"

        private const val CREATE_TABLE =
            "CREATE TABLE IF NOT EXISTS boss_world_stats (" +
                "boss TEXT PRIMARY KEY, " +
                "kills BIGINT NOT NULL DEFAULT 0, " +
                "deaths BIGINT NOT NULL DEFAULT 0, " +
                "best_ticks INT NOT NULL DEFAULT 0)"

        private const val UPSERT =
            "INSERT INTO boss_world_stats AS s (boss, kills, deaths, best_ticks) " +
                "VALUES (?, ?, ?, ?) ON CONFLICT (boss) DO UPDATE SET " +
                "kills = s.kills + EXCLUDED.kills, " +
                "deaths = s.deaths + EXCLUDED.deaths, " +
                "best_ticks = CASE WHEN EXCLUDED.best_ticks > 0 AND " +
                "(s.best_ticks = 0 OR EXCLUDED.best_ticks < s.best_ticks) " +
                "THEN EXCLUDED.best_ticks ELSE s.best_ticks END " +
                "RETURNING kills, deaths, best_ticks"
    }
}

class MaggotKingScoreboard @Inject constructor(private val worldStats: MaggotKingWorldStats) :
    PluginScript() {
    override fun ScriptContext.startup() {
        worldStats.load()
        onOpLoc1(SCOREBOARD) { read() }
        onEvent<InstanceKillTimeEvent>(instanceEventId(MaggotKingInstance.INSTANCE_KEY)) {
            worldStats.addKillTime(ticks)
        }
    }

    private fun ProtectedAccess.read() {
        val precise = vars["varbit.option_precise_timing"] != 0
        val personalBest =
            vars[BEST_TIME].let { if (it == 0) NO_TIME else formatInstanceKillTime(it, precise) }
        val worldBest =
            worldStats.bestTicks.let { if (it == 0) NO_TIME else formatInstanceKillTime(it, precise) }
        ifOpenMainModal(INTERFACE)
        ifSetText("$INTERFACE_COM:pkc_content", number(vars[KILLS].toLong()))
        ifSetText("$INTERFACE_COM:pd_content", number(vars[DEATHS].toLong()))
        ifSetText("$INTERFACE_COM:pbt_content", personalBest)
        ifSetText("$INTERFACE_COM:gkc_content", number(worldStats.kills))
        ifSetText("$INTERFACE_COM:gd_content", number(worldStats.deaths))
        ifSetText("$INTERFACE_COM:gbt_content", worldBest)
    }

    private fun number(value: Long): String = NumberFormat.getIntegerInstance(Locale.UK).format(value)

    companion object {
        const val SCOREBOARD = "loc.scoreboard_magggot01_default01"
        const val KILLS = "varp.total_maggot_king_kills"
        const val DEATHS = "varp.maggot_king_deaths"
        const val BEST_TIME = "varp.maggot_king_best_time"
        private const val INTERFACE = "interface.maggot_king_scoreboard"
        private const val INTERFACE_COM = "component.maggot_king_scoreboard"
        private const val NO_TIME = "0:00.00"
    }
}
