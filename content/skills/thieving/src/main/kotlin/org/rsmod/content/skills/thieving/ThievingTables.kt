package org.rsmod.content.skills.thieving

import org.rsmod.api.random.GameRandom

internal data class Loot(val obj: String, val min: Int = 1, val max: Int = min)

internal class LootTable(private val entries: List<Pair<Int, Loot>>) {
    private val total = entries.sumOf { it.first }

    fun roll(random: GameRandom): Loot {
        var pick = random.of(0, total - 1)
        for ((weight, loot) in entries) {
            if (pick < weight) return loot
            pick -= weight
        }
        return entries.last().second
    }
}

internal class Stall(
    val loc: String,
    val emptyLoc: String?,
    val level: Int,
    val xp: Double,
    val restockTicks: Int,
    val owners: List<String>,
    val guards: List<String>,
    val attemptMessage: String,
    val loot: LootTable,
)

internal class CoinPouch(val obj: String, val coinsMin: Int, val coinsMax: Int = coinsMin)

internal class Pickpocket(
    val npcs: List<String>,
    val level: Int,
    val xp: Double,
    val lowChance: Int,
    val highChance: Int,
    val stunDamage: Int,
    val caughtShout: String,
    val stunDamageMax: Int = stunDamage,
    val loot: LootTable? = null,
    val pouch: CoinPouch? = null,
    val lowercaseName: Boolean = false,
    val pocketName: String? = null,
    val quest: String? = null,
    val attemptDelay: Int = 1,
)

internal object ThievingTables {
    private val seedStallLoot =
        LootTable(
            listOf(
                120 to Loot("obj.hammerstone_hop_seed"),
                119 to Loot("obj.potato_seed"),
                119 to Loot("obj.marigold_seed"),
                118 to Loot("obj.barley_seed"),
                89 to Loot("obj.onion_seed"),
                83 to Loot("obj.asgarnian_hop_seed"),
                71 to Loot("obj.cabbage_seed"),
                47 to Loot("obj.yanillian_hop_seed"),
                36 to Loot("obj.rosemary_seed"),
                35 to Loot("obj.nasturtium_seed"),
                35 to Loot("obj.tomato_seed"),
                35 to Loot("obj.jute_seed"),
                30 to Loot("obj.sweetcorn_seed"),
                24 to Loot("obj.krandorian_hop_seed"),
                18 to Loot("obj.strawberry_seed"),
                12 to Loot("obj.wildblood_hop_seed"),
                9 to Loot("obj.watermelon_seed"),
            )
        )

    private val wineStallLoot =
        LootTable(
            listOf(
                39 to Loot("obj.jug_empty"),
                20 to Loot("obj.jug_water"),
                17 to Loot("obj.grapes"),
                13 to Loot("obj.jug_wine"),
                11 to Loot("obj.rag_bottle_wine"),
            )
        )

    private val vegetableStallLoot =
        LootTable(
            listOf(
                30 to Loot("obj.onion"),
                25 to Loot("obj.cabbage"),
                20 to Loot("obj.potato"),
                15 to Loot("obj.tomato"),
                10 to Loot("obj.garlic"),
            )
        )

    private val bakeryStallLoot =
        LootTable(
            listOf(
                50 to Loot("obj.bread"),
                30 to Loot("obj.cake"),
                20 to Loot("obj.chocolate_slice"),
            )
        )

    private val fruitStallLoot =
        LootTable(
            listOf(
                20 to Loot("obj.cooking_apple"),
                15 to Loot("obj.banana"),
                10 to Loot("obj.strawberry"),
                10 to Loot("obj.redberries"),
                10 to Loot("obj.jangerberries"),
                8 to Loot("obj.lime"),
                8 to Loot("obj.lemon"),
                7 to Loot("obj.pineapple"),
                6 to Loot("obj.papaya"),
                4 to Loot("obj.golovanova_top"),
                2 to Loot("obj.didyouknow_strange_fruit"),
            )
        )

    private val furStallLoot =
        LootTable(
            listOf(
                85 to Loot("obj.fur"),
                15 to Loot("obj.grey_wolf_fur"),
            )
        )

    private val fishStallLoot =
        LootTable(
            listOf(
                55 to Loot("obj.raw_salmon"),
                30 to Loot("obj.raw_tuna"),
                15 to Loot("obj.raw_lobster"),
            )
        )

    private val silverStallLoot =
        LootTable(
            listOf(
                60 to Loot("obj.silver_ore"),
                30 to Loot("obj.silver_bar"),
                10 to Loot("obj.tiara"),
            )
        )

    private val gemStallLoot =
        LootTable(
            listOf(
                50 to Loot("obj.uncut_sapphire"),
                30 to Loot("obj.uncut_emerald"),
                15 to Loot("obj.uncut_ruby"),
                5 to Loot("obj.uncut_diamond"),
            )
        )

    private val draynorGuards = listOf("npc.farming_market_guard")
    private val ardougneGuards = listOf("npc.ardougne_guard", "npc.guard1")
    private val kourendGuards = listOf("npc.guard1")
    private val varlamoreGuards = listOf("npc.guard1")
    private val keldagrimGuards = listOf("npc.guard1")

    val stalls: List<Stall> =
        listOf(
            Stall(
                loc = "loc.seed_stall",
                emptyLoc = null,
                level = 27,
                xp = 10.0,
                restockTicks = 4,
                owners = listOf("npc.seed_merchant"),
                guards = draynorGuards,
                attemptMessage = "You attempt to steal some seeds from the seed merchant's stall.",
                loot = seedStallLoot,
            ),
            Stall(
                loc = "loc.rag_market_stall",
                emptyLoc = "loc.rag_market_stall_empty",
                level = 22,
                xp = 27.0,
                restockTicks = 8,
                owners = listOf("npc.rag_wine_merchant"),
                guards = draynorGuards,
                attemptMessage = "You attempt to steal something from the wine merchant's stall.",
                loot = wineStallLoot,
            ),
            Stall(
                loc = "loc.hos_stall_veg",
                emptyLoc = null,
                level = 2,
                xp = 10.0,
                restockTicks = 2,
                owners = emptyList(),
                guards = kourendGuards,
                attemptMessage = "You attempt to steal some vegetables from the stall.",
                loot = vegetableStallLoot,
            ),
            Stall(
                loc = "loc.fortis_market_stall",
                emptyLoc = null,
                level = 5,
                xp = 16.0,
                restockTicks = 4,
                owners = listOf("npc.baker_merchant", "npc.baker_merchant_ardougne", "npc.baker_merchant_ardougne2"),
                guards = varlamoreGuards,
                attemptMessage = "You attempt to steal some baked goods from the stall.",
                loot = bakeryStallLoot,
            ),
            Stall(
                loc = "loc.keldagrim_bread_stall",
                emptyLoc = null,
                level = 5,
                xp = 16.0,
                restockTicks = 4,
                owners = listOf("npc.baker_merchant"),
                guards = keldagrimGuards,
                attemptMessage = "You attempt to steal some baked goods from the stall.",
                loot = bakeryStallLoot,
            ),
            Stall(
                loc = "loc.aldarin_market_stall_veg",
                emptyLoc = null,
                level = 2,
                xp = 10.0,
                restockTicks = 2,
                owners = emptyList(),
                guards = varlamoreGuards,
                attemptMessage = "You attempt to steal some vegetables from the stall.",
                loot = vegetableStallLoot,
            ),
            Stall(
                loc = "loc.tea_stall",
                emptyLoc = null,
                level = 5,
                xp = 16.0,
                restockTicks = 4,
                owners = listOf("npc.tea_seller"),
                guards = kourendGuards,
                attemptMessage = "You attempt to steal a cup of tea from the stall.",
                loot = LootTable(listOf(1 to Loot("obj.cup_of_tea"))),
            ),
            Stall(
                loc = "loc.silkthiefstall",
                emptyLoc = "loc.silkthiefstall_noop",
                level = 20,
                xp = 24.0,
                restockTicks = 8,
                owners = listOf("npc.silk_merchant", "npc.silk_merchant_ardougne", "npc.silk_trader"),
                guards = ardougneGuards,
                attemptMessage = "You attempt to steal some silk from the stall.",
                loot = LootTable(listOf(1 to Loot("obj.silk"))),
            ),
            Stall(
                loc = "loc.shayzien_silk_stall",
                emptyLoc = null,
                level = 20,
                xp = 24.0,
                restockTicks = 8,
                owners = listOf("npc.silk_trader"),
                guards = kourendGuards,
                attemptMessage = "You attempt to steal some silk from the stall.",
                loot = LootTable(listOf(1 to Loot("obj.silk"))),
            ),
            Stall(
                loc = "loc.prif_marketstall_silk",
                emptyLoc = null,
                level = 20,
                xp = 24.0,
                restockTicks = 8,
                owners = emptyList(),
                guards = kourendGuards,
                attemptMessage = "You attempt to steal some silk from the stall.",
                loot = LootTable(listOf(1 to Loot("obj.silk"))),
            ),
            Stall(
                loc = "loc.fortis_market_stall_silk",
                emptyLoc = null,
                level = 20,
                xp = 24.0,
                restockTicks = 8,
                owners = listOf("npc.silk_trader"),
                guards = varlamoreGuards,
                attemptMessage = "You attempt to steal some silk from the stall.",
                loot = LootTable(listOf(1 to Loot("obj.silk"))),
            ),
            Stall(
                loc = "loc.hos_fruit_stall",
                emptyLoc = null,
                level = 25,
                xp = 28.5,
                restockTicks = 2,
                owners = emptyList(),
                guards = kourendGuards,
                attemptMessage = "You attempt to steal some fruit from the stall.",
                loot = fruitStallLoot,
            ),
            Stall(
                loc = "loc.hos_fruit_stall_01",
                emptyLoc = null,
                level = 25,
                xp = 28.5,
                restockTicks = 2,
                owners = emptyList(),
                guards = kourendGuards,
                attemptMessage = "You attempt to steal some fruit from the stall.",
                loot = fruitStallLoot,
            ),
            Stall(
                loc = "loc.hos_fruit_stall_02",
                emptyLoc = null,
                level = 25,
                xp = 28.5,
                restockTicks = 2,
                owners = emptyList(),
                guards = kourendGuards,
                attemptMessage = "You attempt to steal some fruit from the stall.",
                loot = fruitStallLoot,
            ),
            Stall(
                loc = "loc.fortis_market_stall_fur",
                emptyLoc = null,
                level = 35,
                xp = 45.0,
                restockTicks = 12,
                owners = listOf("npc.fur_merchant", "npc.fur_merchant_ardougne"),
                guards = varlamoreGuards,
                attemptMessage = "You attempt to steal some fur from the stall.",
                loot = furStallLoot,
            ),
            Stall(
                loc = "loc.fish_stall_warrens",
                emptyLoc = null,
                level = 42,
                xp = 42.0,
                restockTicks = 12,
                owners = listOf("npc.piscarilius_fish_trader", "npc.piscarilius_fish_trader_0"),
                guards = kourendGuards,
                attemptMessage = "You attempt to steal some fish from the stall.",
                loot = fishStallLoot,
            ),
            Stall(
                loc = "loc.keldagrim_silver_stall",
                emptyLoc = null,
                level = 50,
                xp = 205.0,
                restockTicks = 32,
                owners = listOf("npc.dwarf_city_silver_trader1", "npc.dwarf_city_silver_trader2"),
                guards = keldagrimGuards,
                attemptMessage = "You attempt to steal some silver from the stall.",
                loot = silverStallLoot,
            ),
            Stall(
                loc = "loc.fortis_market_stall_spice",
                emptyLoc = null,
                level = 65,
                xp = 81.0,
                restockTicks = 12,
                owners = listOf("npc.spice_merchant", "npc.spice_merchant_ardougne"),
                guards = varlamoreGuards,
                attemptMessage = "You attempt to steal some spice from the stall.",
                loot = LootTable(listOf(1 to Loot("obj.spicespot"))),
            ),
            Stall(
                loc = "loc.prif_marketstall_spice",
                emptyLoc = null,
                level = 65,
                xp = 81.0,
                restockTicks = 12,
                owners = listOf("npc.spice_merchant"),
                guards = kourendGuards,
                attemptMessage = "You attempt to steal some spice from the stall.",
                loot = LootTable(listOf(1 to Loot("obj.spicespot"))),
            ),
            Stall(
                loc = "loc.keldagrim_gem_stall",
                emptyLoc = null,
                level = 75,
                xp = 408.0,
                restockTicks = 100,
                owners = emptyList(),
                guards = keldagrimGuards,
                attemptMessage = "You attempt to steal a gem from the stall.",
                loot = gemStallLoot,
            ),
            Stall(
                loc = "loc.prif_marketstall_gem",
                emptyLoc = null,
                level = 75,
                xp = 408.0,
                restockTicks = 100,
                owners = emptyList(),
                guards = kourendGuards,
                attemptMessage = "You attempt to steal a gem from the stall.",
                loot = gemStallLoot,
            ),
            Stall(
                loc = "loc.fortis_market_stall_gems",
                emptyLoc = null,
                level = 75,
                xp = 408.0,
                restockTicks = 100,
                owners = emptyList(),
                guards = varlamoreGuards,
                attemptMessage = "You attempt to steal a gem from the stall.",
                loot = gemStallLoot,
            ),
            Stall(
                loc = "loc.aldarin_market_stall_gems",
                emptyLoc = null,
                level = 75,
                xp = 408.0,
                restockTicks = 100,
                owners = emptyList(),
                guards = varlamoreGuards,
                attemptMessage = "You attempt to steal a gem from the stall.",
                loot = gemStallLoot,
            ),
        )

    private val masterFarmerLoot =
        LootTable(
            listOf(
                17700 to Loot("obj.potato_seed", 1, 4),
                13280 to Loot("obj.onion_seed", 1, 3),
                6944 to Loot("obj.cabbage_seed", 1, 3),
                6369 to Loot("obj.tomato_seed", 1, 2),
                2212 to Loot("obj.sweetcorn_seed", 1, 2),
                1106 to Loot("obj.strawberry_seed"),
                529 to Loot("obj.watermelon_seed"),
                385 to Loot("obj.snape_grass_seed"),
                5556 to Loot("obj.barley_seed", 1, 12),
                5556 to Loot("obj.hammerstone_hop_seed", 1, 9),
                4184 to Loot("obj.asgarnian_hop_seed", 1, 6),
                4149 to Loot("obj.jute_seed", 1, 9),
                2770 to Loot("obj.yanillian_hop_seed", 1, 6),
                1385 to Loot("obj.krandorian_hop_seed", 1, 6),
                704 to Loot("obj.wildblood_hop_seed", 1, 3),
                4587 to Loot("obj.marigold_seed"),
                3040 to Loot("obj.nasturtium_seed"),
                1965 to Loot("obj.rosemary_seed"),
                1451 to Loot("obj.woad_seed"),
                1159 to Loot("obj.limpwurt_seed"),
                3876 to Loot("obj.redberry_bush_seed"),
                2717 to Loot("obj.cadavaberry_bush_seed"),
                1942 to Loot("obj.dwellberry_bush_seed"),
                775 to Loot("obj.jangerberry_bush_seed"),
                282 to Loot("obj.whiteberry_bush_seed"),
                107 to Loot("obj.poisonivy_bush_seed"),
                203 to Loot("obj.mushroom_seed"),
                122 to Loot("obj.belladonna_seed"),
                81 to Loot("obj.cactus_seed"),
                53 to Loot("obj.seaweed_seed"),
                41 to Loot("obj.potato_cactus_seed"),
                1713 to Loot("obj.guam_seed"),
                1046 to Loot("obj.marrentill_seed"),
                714 to Loot("obj.tarromin_seed"),
                485 to Loot("obj.harralander_seed"),
                372 to Loot("obj.ranarr_seed"),
                226 to Loot("obj.toadflax_seed"),
                154 to Loot("obj.irit_seed"),
                106 to Loot("obj.avantoe_seed"),
                72 to Loot("obj.kwuarm_seed"),
                54 to Loot("obj.snapdragon_seed"),
                34 to Loot("obj.cadantine_seed"),
                24 to Loot("obj.lantadyme_seed"),
                14 to Loot("obj.dwarf_weed_seed"),
                11 to Loot("obj.torstol_seed"),
            )
        )

    private val digsiteWorkmanLoot =
        LootTable(
            listOf(
                1 to Loot("obj.cup_of_tea"),
            )
        )

    private val villagerLoot =
        LootTable(
            listOf(
                1 to Loot("obj.coins", 1, 7),
            )
        )

    val pickpockets: List<Pickpocket> =
        listOf(
            Pickpocket(
                npcs =
                    listOf(
                        "npc.man",
                        "npc.man2",
                        "npc.man3",
                        "npc.woman",
                        "npc.woman2",
                        "npc.woman3",
                    ),
                level = 1,
                xp = 8.0,
                lowChance = 180,
                highChance = 240,
                stunDamage = 1,
                caughtShout = "What do you think you're doing?",
                pouch = CoinPouch("obj.pickpocket_coin_pouch_citizen", 3),
                lowercaseName = true,
            ),
            Pickpocket(
                npcs =
                    listOf(
                        "npc.farmer1",
                        "npc.farmer2",
                        "npc.farmer3",
                        "npc.farmer1_f",
                        "npc.farmer2_f",
                        "npc.farmer3_f",
                    ),
                level = 10,
                xp = 14.5,
                lowChance = 40,
                highChance = 240,
                stunDamage = 1,
                caughtShout = "What do you think you're doing?",
                pouch = CoinPouch("obj.pickpocket_coin_pouch_farmer", 9),
                lowercaseName = true,
            ),
            Pickpocket(
                npcs =
                    listOf(
                        "npc.pickpocket_guide_fham",
                        "npc.pickpocket_guide_mham",
                    ),
                level = 15,
                xp = 22.2,
                lowChance = 90,
                highChance = 240,
                stunDamage = 1,
                caughtShout = "What do you think you're doing?",
                stunDamageMax = 3,
                pouch = CoinPouch("obj.pickpocket_coin_pouch_ham", 1, 21),
                lowercaseName = true,
                pocketName = "the H.A.M. member",
            ),
            Pickpocket(
                npcs =
                    listOf(
                        "npc.al_kharid_warrior",
                        "npc.pickpocket_guide_warrior",
                        "npc.warrior_woman",
                    ),
                level = 25,
                xp = 26.0,
                lowChance = 70,
                highChance = 240,
                stunDamage = 2,
                caughtShout = "What do you think you're doing?",
                pouch = CoinPouch("obj.pickpocket_coin_pouch_warrior", 18),
                pocketName = "the warrior",
            ),
            Pickpocket(
                npcs =
                    listOf(
                        "npc.digworkman1",
                        "npc.digworkman2",
                        "npc.qip_digsite_digworkman_03",
                        "npc.qip_digsite_digworkman_04",
                    ),
                level = 25,
                xp = 10.4,
                lowChance = 70,
                highChance = 240,
                stunDamage = 1,
                caughtShout = "What do you think you're doing?",
                loot = digsiteWorkmanLoot,
                lowercaseName = true,
                pocketName = "the workman",
                attemptDelay = 3,
            ),
            Pickpocket(
                npcs = listOf("npc.pickpocket_guide_villager"),
                level = 30,
                xp = 8.0,
                lowChance = 110,
                highChance = 240,
                stunDamage = 2,
                caughtShout = "What do you think you're doing?",
                loot = villagerLoot,
                lowercaseName = true,
            ),
            Pickpocket(
                npcs =
                    listOf(
                        "npc.pickpocket_guide_rogue",
                        "npc.rogue",
                    ),
                level = 32,
                xp = 36.5,
                lowChance = 42,
                highChance = 240,
                stunDamage = 2,
                caughtShout = "What do you think you're doing?",
                pouch = CoinPouch("obj.pickpocket_coin_pouch_rogue", 25, 40),
                lowercaseName = true,
            ),
            Pickpocket(
                npcs = listOf("npc.pickpocket_guide_dorgesh"),
                level = 36,
                xp = 40.0,
                lowChance = 100,
                highChance = 240,
                stunDamage = 1,
                caughtShout = "What do you think you're doing?",
                pouch = CoinPouch("obj.pickpocket_coin_pouch_cavegoblin", 10, 50),
                lowercaseName = true,
                pocketName = "the cave goblin",
            ),
            Pickpocket(
                npcs = listOf("npc.master_farmer_1", "npc.martin_the_master_farmer"),
                level = 38,
                xp = 43.0,
                lowChance = 42,
                highChance = 240,
                stunDamage = 3,
                caughtShout = "Cor blimey mate, what are ye doing in me pockets?",
                loot = masterFarmerLoot,
            ),
            Pickpocket(
                npcs =
                    listOf(
                        "npc.pickpocket_guide_guard",
                        "npc.guard1",
                        "npc.ardougne_guard",
                    ),
                level = 40,
                xp = 46.8,
                lowChance = 20,
                highChance = 240,
                stunDamage = 2,
                caughtShout = "What do you think you're doing?",
                pouch = CoinPouch("obj.pickpocket_coin_pouch_guard", 30),
                lowercaseName = true,
            ),
            Pickpocket(
                npcs = listOf("npc.pickpocket_guide_fremennik_citizen"),
                level = 45,
                xp = 65.0,
                lowChance = 30,
                highChance = 240,
                stunDamage = 2,
                caughtShout = "What do you think you're doing?",
                pouch = CoinPouch("obj.pickpocket_coin_pouch_fremennik", 40),
                lowercaseName = true,
                pocketName = "the Fremennik citizen",
                quest = "quest_fremenniktrials",
            ),
            Pickpocket(
                npcs = listOf("npc.feud_bandit_toughguy"),
                level = 45,
                xp = 65.0,
                lowChance = 30,
                highChance = 240,
                stunDamage = 5,
                caughtShout = "What do you think you're doing?",
                pouch = CoinPouch("obj.pickpocket_coin_pouch_bandit", 40),
                lowercaseName = true,
                pocketName = "the bandit",
            ),
            Pickpocket(
                npcs = listOf("npc.pickpocket_guide_wealthy_citizen"),
                level = 50,
                xp = 96.0,
                lowChance = 25,
                highChance = 240,
                stunDamage = 3,
                caughtShout = "What do you think you're doing?",
                pouch = CoinPouch("obj.pickpocket_coin_pouch_varlamore_wealthy", 85),
                lowercaseName = true,
                pocketName = "the wealthy citizen",
                quest = "quest_childrenofthesun",
            ),
            Pickpocket(
                npcs = listOf("npc.pickpocket_guide_desert_bandit"),
                level = 53,
                xp = 79.4,
                lowChance = 20,
                highChance = 240,
                stunDamage = 3,
                caughtShout = "What do you think you're doing?",
                pouch = CoinPouch("obj.pickpocket_coin_pouch_desertbandit", 30),
                lowercaseName = true,
                pocketName = "the desert bandit",
            ),
            Pickpocket(
                npcs =
                    listOf(
                        "npc.pickpocket_guide_knight",
                        "npc.knight_of_ardougne",
                        "npc.knight_of_ardougne2",
                        "npc.knight_of_ardougne_west",
                        "npc.knight_of_ardougne_f_west",
                    ),
                level = 55,
                xp = 84.3,
                lowChance = 20,
                highChance = 240,
                stunDamage = 3,
                caughtShout = "What do you think you're doing?",
                pouch = CoinPouch("obj.pickpocket_coin_pouch_knight", 50),
                lowercaseName = true,
                pocketName = "the knight",
            ),
            Pickpocket(
                npcs =
                    listOf(
                        "npc.pickpocket_guide_pirate",
                        "npc.pirate1",
                        "npc.pirate2",
                        "npc.pirate_aggressive",
                    ),
                level = 60,
                xp = 72.0,
                lowChance = 25,
                highChance = 240,
                stunDamage = 3,
                caughtShout = "What do you think you're doing?",
                pouch = CoinPouch("obj.pickpocket_coin_pouch_pirate", 72),
                lowercaseName = true,
            ),
            Pickpocket(
                npcs =
                    listOf(
                        "npc.pickpocket_guide_watchman",
                        "npc.yanille_watchman",
                    ),
                level = 65,
                xp = 137.5,
                lowChance = 20,
                highChance = 240,
                stunDamage = 3,
                caughtShout = "What do you think you're doing?",
                pouch = CoinPouch("obj.pickpocket_coin_pouch_watchman", 60),
                lowercaseName = true,
                pocketName = "the watchman",
            ),
            Pickpocket(
                npcs = listOf("npc.thug"),
                level = 65,
                xp = 137.5,
                lowChance = 20,
                highChance = 240,
                stunDamage = 5,
                caughtShout = "What do you think you're doing?",
                pouch = CoinPouch("obj.pickpocket_coin_pouch_menaphite", 60),
                lowercaseName = true,
                pocketName = "the Menaphite thug",
            ),
            Pickpocket(
                npcs =
                    listOf(
                        "npc.pickpocket_guide_paladin",
                        "npc.paladin",
                        "npc.paladin2",
                        "npc.ardougne_paladin2",
                        "npc.paladin_west",
                        "npc.paladin_f",
                    ),
                level = 70,
                xp = 131.8,
                lowChance = 18,
                highChance = 240,
                stunDamage = 3,
                caughtShout = "What do you think you're doing?",
                pouch = CoinPouch("obj.pickpocket_coin_pouch_paladin", 80),
                lowercaseName = true,
            ),
            Pickpocket(
                npcs =
                    listOf(
                        "npc.pickpocket_guide_gnome",
                        "npc.gnome",
                    ),
                level = 75,
                xp = 133.3,
                lowChance = 15,
                highChance = 240,
                stunDamage = 1,
                caughtShout = "What do you think you're doing?",
                pouch = CoinPouch("obj.pickpocket_coin_pouch_gnome", 300),
                lowercaseName = true,
            ),
            Pickpocket(
                npcs =
                    listOf(
                        "npc.pickpocket_guide_hero",
                        "npc.hero",
                    ),
                level = 80,
                xp = 163.3,
                lowChance = 15,
                highChance = 240,
                stunDamage = 3,
                caughtShout = "What do you think you're doing?",
                pouch = CoinPouch("obj.pickpocket_coin_pouch_hero", 200, 300),
                lowercaseName = true,
            ),
            Pickpocket(
                npcs = listOf("npc.pickpocket_guide_vyre"),
                level = 82,
                xp = 306.9,
                lowChance = 12,
                highChance = 240,
                stunDamage = 5,
                caughtShout = "What do you think you're doing?",
                pouch = CoinPouch("obj.pickpocket_coin_pouch_vyre", 230, 315),
                lowercaseName = true,
            ),
            Pickpocket(
                npcs = listOf("npc.pickpocket_guide_woodelf"),
                level = 85,
                xp = 353.3,
                lowChance = 12,
                highChance = 240,
                stunDamage = 5,
                caughtShout = "What do you think you're doing?",
                pouch = CoinPouch("obj.pickpocket_coin_pouch_elf", 280, 350),
                lowercaseName = true,
                pocketName = "the elf",
            ),
        )
}
