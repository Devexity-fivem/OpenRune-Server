package org.rsmod.content.minigames.fightcave

internal object FightCaveWaves {
    const val TZ_KIH = "npc.tzhaar_fightcave_swarm_1a"
    const val TZ_KEK = "npc.tzhaar_fightcave_swarm_2a"
    const val TZ_KEK_MINI = "npc.tzhaar_fightcave_swarm_2spawn"
    const val TOK_XIL = "npc.tzhaar_fightcave_swarm_3a"
    const val YT_MEJKOT = "npc.tzhaar_fightcave_swarm_4a"
    const val KET_ZEK = "npc.tzhaar_fightcave_swarm_5a"
    const val TZTOK_JAD = "npc.tzhaar_fightcave_swarm_boss"
    const val YT_HURKOT = "npc.tzhaar_fightcave_swarm_boss_cleric"

    const val LAST_WAVE = 63

    val RANGED = setOf(TOK_XIL, KET_ZEK, TZTOK_JAD)

    private val K = TZ_KIH
    private val E = TZ_KEK
    private val X = TOK_XIL
    private val M = YT_MEJKOT
    private val Z = KET_ZEK

    val WAVES: List<List<String>> =
        listOf(
            listOf(K),
            listOf(K, K),
            listOf(E),
            listOf(E, K),
            listOf(E, K, K),
            listOf(E, E),
            listOf(X),
            listOf(X, K),
            listOf(X, K, K),
            listOf(X, E),
            listOf(X, E, K),
            listOf(X, E, K, K),
            listOf(X, E, E),
            listOf(X, X),
            listOf(M),
            listOf(M, K),
            listOf(M, K, K),
            listOf(M, E),
            listOf(M, E, K),
            listOf(M, E, K, K),
            listOf(M, E, E),
            listOf(M, X),
            listOf(M, X, K),
            listOf(M, X, K, K),
            listOf(M, X, E),
            listOf(M, X, E, K),
            listOf(M, X, E, K, K),
            listOf(M, X, E, E),
            listOf(M, X, X),
            listOf(M, M),
            listOf(Z),
            listOf(Z, K),
            listOf(Z, K, K),
            listOf(Z, E),
            listOf(Z, E, K),
            listOf(Z, E, K, K),
            listOf(Z, E, E),
            listOf(Z, X),
            listOf(Z, X, K),
            listOf(Z, X, K, K),
            listOf(Z, X, E),
            listOf(Z, X, E, K),
            listOf(Z, X, E, K, K),
            listOf(Z, X, E, E),
            listOf(Z, X, X),
            listOf(Z, M),
            listOf(Z, M, K),
            listOf(Z, M, K, K),
            listOf(Z, M, E),
            listOf(Z, M, E, K),
            listOf(Z, M, E, K, K),
            listOf(Z, M, E, E),
            listOf(Z, M, X),
            listOf(Z, M, X, K),
            listOf(Z, M, X, K, K),
            listOf(Z, M, X, E),
            listOf(Z, M, X, E, K),
            listOf(Z, M, X, E, K, K),
            listOf(Z, M, X, E, E),
            listOf(Z, M, X, X),
            listOf(Z, M, M),
            listOf(Z, Z),
            listOf(TZTOK_JAD),
        )

    val SPAWN_OFFSETS: List<Pair<Int, Int>> =
        listOf(
            31 to 11,
            47 to 16,
            51 to 33,
            47 to 48,
            15 to 48,
            15 to 16,
        )

    val JAD_OFFSET = 31 to 30
    val HEALER_OFFSETS = listOf(28 to 27, 34 to 27, 28 to 33, 34 to 33)
}
