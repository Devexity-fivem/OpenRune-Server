package dev.openrune.gamevals

import java.io.File
import org.junit.jupiter.api.Test

class ThievingSymbolCheck {
    private val extraCandidates =
        listOf(
            "npc.favour_female_ham_civilian",
            "npc.favour_male_ham_civilian",
            "npc.dttd_ham_guard_1",
            "npc.dttd_ham_guard_2",
            "npc.dttd_ham_guard_3",
            "npc.dttd_ham_guard_4",
            "npc.dttd_ham_guard_5",
            "npc.dttd_ham_guard_extra",
            "npc.dttd_ham_guard_mill",
            "npc.dttd_ham_guard_1_postquest",
            "npc.dttd_ham_guard_2_postquest",
            "npc.dttd_ham_guard_3_postquest",
            "npc.dttd_ham_guard_4_postquest",
            "npc.dttd_ham_guard_5_postquest",
            "npc.feud_villager_multi_1",
            "npc.feud_villager_multi_2",
            "npc.feud_villager_multi_3",
            "npc.cave_goblin",
            "npc.cave_goblin2",
            "npc.cave_goblin3",
            "npc.cave_goblin4",
            "npc.misc_man_2",
            "npc.misc_man_3",
            "npc.misc_man_5",
            "npc.misc_man_7",
            "npc.misc_woman_1",
            "npc.misc_woman_4",
            "npc.misc_woman_6",
            "npc.guard_bandit",
            "npc.uncerter_bandit",
            "npc.varlamore_wealthy_citizen_a",
            "npc.varlamore_wealthy_citizen_b",
            "npc.varlamore_wealthy_citizen_c",
            "npc.varlamore_wealthy_citizen_d",
            "npc.knight_of_ardougne_f",
            "npc.pirate_pickpocketable_1",
            "npc.pirate_pickpocketable_2",
            "npc.pirate_pickpocketable_3",
            "npc.pirate_pickpocketable_4",
            "npc.pirate_pickpocketable_5",
            "npc.wom_watchman",
            "npc.thug",
            "npc.paladin_variant01",
            "npc.paladin_variant02",
            "npc.paladin_f_variant01",
            "npc.browclothedgnome",
            "npc.darkskinned_gnome",
            "npc.gnomechild",
            "npc.gnomechildgreen",
            "npc.gnomechildblue",
            "npc.hero_f",
            "npc.hero_variant01",
            "npc.darkm_vyrewatch_male_1",
            "npc.darkm_vyrewatch_male_2",
            "npc.darkm_vyrewatch_male_3",
            "npc.darkm_vyrewatch_male_4",
            "npc.darkm_vyrewatch_female_1",
            "npc.darkm_vyrewatch_female_2",
            "npc.darkm_vyrewatch_female_3",
            "npc.darkm_vyrewatch_female_4",
            "npc.gwenith_elf_1",
            "npc.gwenith_elf_2",
            "npc.gwenith_elf_3",
            "npc.gwenith_elf_4",
            "npc.gwenith_elf_5",
            "npc.gwenith_elf_6",
            "npc.prif_citizen_amrod",
            "npc.prif_citizen_tatie",
            "npc.wilderness_rogue",
            "npc.master_farmer_1_f",
            "npc.master_farmer_2",
            "npc.master_farmer_2_f",
            "npc.varlamore_master_farmer_m_1",
            "npc.varlamore_master_farmer_f_1",
            "npc.kastori_master_farmer_m_1",
            "npc.kastori_master_farmer_f_1",
            "npc.ardougne_guard_variant01",
            "npc.ardougne_guard_f",
            "npc.tzhaar_hur1",
            "npc.tzhaar_hur2",
            "npc.man_indoor",
            "npc.farmer4",
            "npc.misc_farmer",
            "npc.misc_etc_farmer",
            "npc.mugger",
        )

    @Test
    fun `validate all symbols bound in ThievingTables`() {
        val source =
            File(
                "../content/skills/thieving/src/main/kotlin/org/rsmod/content/skills/thieving/ThievingTables.kt"
            ).readText()
        val symbols = Regex("\"((?:npc|loc|obj|seq|spotanim|param|quest|varbit|varp|timer)\\.[^\"]+)\"")
            .findAll(source)
            .map { it.groupValues[1] }
            .toSortedSet()
        val provider = GameValProvider.loadIsolated("../")
        var missing = 0
        for (symbol in symbols + extraCandidates) {
            val table = symbol.substringBefore(".")
            val found = provider.mappings[table]?.get(symbol)
            if (found == null) {
                missing++
                println("MISS $symbol")
            } else {
                println("OK   $symbol -> $found")
            }
        }
        println("SUMMARY missing=$missing total=${symbols.size + extraCandidates.size}")
    }
}
