package org.rsmod.content.minigames.inferno

internal object InfernoWaves {
    const val JAL_NIB = "npc.inferno_nibbler"
    const val JAL_MEJRAH = "npc.inferno_creature_harpie"
    const val JAL_AK = "npc.inferno_creature_splitter"
    const val JAL_IMKOT = "npc.inferno_creature_melee"
    const val JAL_XIL = "npc.inferno_creature_ranger"
    const val JAL_ZEK = "npc.inferno_creature_mager"
    const val JALTOK_JAD = "npc.inferno_jad"
    const val JALTOK_JAD_HEALER = "npc.inferno_jad_healer"
    const val TZKAL_ZUK = "npc.inferno_tzkalzuk_placeholder"
    const val ZUK_HEALER = "npc.inferno_zuk_healer"

    const val LAST_WAVE = 69
    const val SINGLE_JAD_WAVE = 67
    const val TRIPLE_JAD_WAVE = 68
    const val SINGLE_JAD_HEALERS = 5
    const val TRIPLE_JAD_HEALERS = 3

    val RANGED = setOf(JAL_MEJRAH, JAL_AK, JAL_XIL, JAL_ZEK, JALTOK_JAD, TZKAL_ZUK)

    private val N = JAL_NIB
    private val R = JAL_MEJRAH
    private val A = JAL_AK
    private val I = JAL_IMKOT
    private val X = JAL_XIL
    private val Z = JAL_ZEK

    val WAVES: List<List<String>> =
        listOf(
            listOf(N, N, N, R),
            listOf(N, N, N, R, R),
            listOf(N, N, N, N, N, N),
            listOf(N, N, N, A),
            listOf(N, N, N, A, R),
            listOf(N, N, N, A, R, R),
            listOf(N, N, N, A, A),
            listOf(N, N, N, N, N, N),
            listOf(N, N, N, I),
            listOf(N, N, N, I, R),
            listOf(N, N, N, I, R, R),
            listOf(N, N, N, I, A),
            listOf(N, N, N, I, A, R),
            listOf(N, N, N, I, A, R, R),
            listOf(N, N, N, I, A, A),
            listOf(N, N, N, I, I),
            listOf(N, N, N, N, N, N),
            listOf(N, N, N, X),
            listOf(N, N, N, X, R),
            listOf(N, N, N, X, R, R),
            listOf(N, N, N, X, A),
            listOf(N, N, N, X, A, R),
            listOf(N, N, N, X, A, R, R),
            listOf(N, N, N, X, A, A),
            listOf(N, N, N, X, I),
            listOf(N, N, N, X, I, R),
            listOf(N, N, N, X, I, R, R),
            listOf(N, N, N, X, I, A),
            listOf(N, N, N, X, I, A, R),
            listOf(N, N, N, X, I, A, R, R),
            listOf(N, N, N, X, I, A, A),
            listOf(N, N, N, X, I, I),
            listOf(N, N, N, X, X),
            listOf(N, N, N, N, N, N),
            listOf(N, N, N, Z),
            listOf(N, N, N, Z, R),
            listOf(N, N, N, Z, R, R),
            listOf(N, N, N, Z, A),
            listOf(N, N, N, Z, A, R),
            listOf(N, N, N, Z, A, R, R),
            listOf(N, N, N, Z, A, A),
            listOf(N, N, N, Z, I),
            listOf(N, N, N, Z, I, R),
            listOf(N, N, N, Z, I, R, R),
            listOf(N, N, N, Z, I, A),
            listOf(N, N, N, Z, I, A, R),
            listOf(N, N, N, Z, I, A, R, R),
            listOf(N, N, N, Z, I, A, A),
            listOf(N, N, N, Z, I, I),
            listOf(N, N, N, Z, X),
            listOf(N, N, N, Z, X, R),
            listOf(N, N, N, Z, X, R, R),
            listOf(N, N, N, Z, X, A),
            listOf(N, N, N, Z, X, A, R),
            listOf(N, N, N, Z, X, A, R, R),
            listOf(N, N, N, Z, X, A, A),
            listOf(N, N, N, Z, X, I),
            listOf(N, N, N, Z, X, I, R),
            listOf(N, N, N, Z, X, I, R, R),
            listOf(N, N, N, Z, X, I, A),
            listOf(N, N, N, Z, X, I, A, R),
            listOf(N, N, N, Z, X, I, A, R, R),
            listOf(N, N, N, Z, X, I, A, A),
            listOf(N, N, N, Z, X, I, I),
            listOf(N, N, N, Z, X, X),
            listOf(N, N, N, Z, Z),
        )

    val SINGLE_JAD = listOf(JALTOK_JAD)
    val TRIPLE_JAD = listOf(JALTOK_JAD, JALTOK_JAD, JALTOK_JAD)
    val FINAL_WAVE = listOf(TZKAL_ZUK)

    val SPAWN_OFFSETS: List<Pair<Int, Int>> =
        listOf(
            32 to 12,
            48 to 18,
            52 to 34,
            46 to 50,
            18 to 50,
            14 to 32,
        )

    val ZUK_OFFSET = 32 to 16
    val JAD_OFFSETS = listOf(24 to 30, 40 to 30, 32 to 42)
    val HEALER_OFFSETS = listOf(28 to 27, 36 to 27, 28 to 33, 36 to 33)
    val ZUK_HEALER_OFFSETS = listOf(28 to 20, 36 to 20)
}
