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
    const val TRIPLE_JAD_WAVE = 67

    private val N = JAL_NIB
    private val R = JAL_MEJRAH
    private val A = JAL_AK
    private val I = JAL_IMKOT
    private val X = JAL_XIL
    private val Z = JAL_ZEK

    val WAVES: List<List<String>> =
        listOf(
            listOf(N, N, N),
            listOf(N, N, R),
            listOf(N, N, R, R),
            listOf(N, R, R),
            listOf(N, R, R, R),
            listOf(R, R, R),
            listOf(N, A),
            listOf(N, A, R),
            listOf(A, R, R),
            listOf(A, A),
            listOf(N, I),
            listOf(I, R),
            listOf(I, R, R),
            listOf(I, A),
            listOf(I, A, R),
            listOf(I, I),
            listOf(N, X),
            listOf(X, R),
            listOf(X, R, R),
            listOf(X, A),
            listOf(X, A, R),
            listOf(X, I),
            listOf(X, I, R),
            listOf(X, X),
            listOf(N, Z),
            listOf(Z, R),
            listOf(Z, R, R),
            listOf(Z, A),
            listOf(Z, A, R),
            listOf(Z, I),
            listOf(Z, I, R),
            listOf(Z, X),
            listOf(Z, X, R),
            listOf(Z, Z),
            listOf(N, I, X),
            listOf(I, X, R),
            listOf(I, X, A),
            listOf(I, X, A, R),
            listOf(I, X, I),
            listOf(Z, I, X),
            listOf(Z, X, A),
            listOf(Z, X, A, R),
            listOf(Z, X, I),
            listOf(Z, X, X),
            listOf(Z, Z, R),
            listOf(Z, Z, A),
            listOf(Z, Z, I),
            listOf(Z, Z, X),
            listOf(N, I, X, Z),
            listOf(I, X, Z, R),
            listOf(I, X, Z, A),
            listOf(I, X, Z, I),
            listOf(X, Z, X),
            listOf(Z, I, X, Z),
            listOf(Z, X, Z, A),
            listOf(Z, X, Z, I),
            listOf(Z, X, Z, X),
            listOf(Z, Z, Z),
            listOf(Z, Z, I, X),
            listOf(Z, Z, X, Z),
            listOf(Z, Z, Z, X),
            listOf(Z, Z, Z, Z),
            listOf(N, N, Z, Z, Z),
            listOf(I, X, Z, Z, Z),
        )

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
