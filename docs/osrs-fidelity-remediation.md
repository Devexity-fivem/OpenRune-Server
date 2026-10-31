# OSRS Fidelity Remediation Ledger

Baseline: `adf67c1d2` on `dev` → remediation branch `osrs-fidelity-remediation`
Revision: 241 (game.yml / README / cache consistent; AGENTS.md was stale)

Commits (in order): `1839ba1c5` cache merge fix · `be9e1d7d0` gameval refs + deadeye varbit · `98f2e3dd0` overlay verification test rewrite · `bbf0709a1` sweep-driven refs · `118d47ec6` ledger · `bdafed6e9` varp dedupe · `3c0e81658` spotlessApply · pushed to `origin/osrs-fidelity-remediation`

| ID | Sev | Finding | Status | Evidence / changed files |
|----|-----|---------|--------|--------------------------|
| DATA-001 | P1 | 17 dup `[[npc]]` blocks; `associateBy` last-wins dropped earlier blocks | **Fixed+verified** | `npcs.toml` merged (0 dup ids, 3,560 unique); `PackServerConfigOSRS.collectBlocksByTable` deep-merges same-id blocks (params union, later-file wins). 63 cross-source dup ids also merge — `npc.lesser_demon` (pack wanderRange stripped server params), `npc.cassie`/`npc.bob` shop overlays. One real conflict resolved: `godwars_spiritual_zamorak_warrior` killcount_varp `total_armadyl_kills`→`kc_spiritual_warrior` (later block; matches other 11 spirituals AND the LIVE-cache param 65551=65602). Tests: `PackServerConfigOverlayMergeTest` (4), `ServerCacheOverlayVerificationTest` (5, vs built SERVER cache via NpcServerType fields) |
| COMBAT-001 | P2 | `defensivePrayerBonus` checked `varbit.prayer_hawkeye` for Deadeye +5% Def | **Fixed+verified** | `AccuracyOperations.kt` → `varbit.prayer_eagleeye` (matches ranged/magic ops' `eagleeye`+`deadeye_unlocked` pattern; Deadeye replaces Eagle Eye in prayer book). Test: `DefensivePrayerBonusTest` integration — deadeye lowers attacker hit chance, hawkeye doesn't, piety > deadeye |
| DATA-002 | P2 | `npc.revenant_maledictus`/`obj.amulet_of_avarice` unresolved | **Fixed** | → `npc.wild_cave_superior` (id 11246, real maledictus symbol per binary gamevals + wiki npc id) and `obj.wild_cave_amulet` (id 22557). Audit's suggested `magic_rock_of_avarice` (id 33044) was a different item. NOTE: revenant spawns/defs still absent — hook resolves but cannot fire until revenant content exists (feature dependency, INFO-001) |
| DATA-003 | P2 | `stat.runecraft` ×2 in CombatTutors | **Fixed** | → `stat.runecrafting` (both mentions) |
| DATA-004 | P2 | `obj.tuna_potato` unresolvable | **Fixed** | First → `obj.potato_tuna`, then corrected again → `obj.potato_tuna+sweetcorn` (real tuna potato, id 7060; `+` is valid in symbols — drop tables already use it). `tuna_potato=64500` is a legitimate custom dbrow food-table entry, unrelated |
| DATA-005 | P3 | `seq.human_mine_bronze_pickaxe` fabricated | **Fixed** | → `seq.human_mining_bronze_pickaxe` (verified in binary gamevals) in `GemstoneCrabMiningScript.kt` fallback |
| DATA-006 | — | Stale `SailingChanceEncountersLostGoodsResourceRow` | **Not a defect** | `dbtable.sailing_chance_encounters_lost_goods_resource` exists in binary gamevals — audit false positive |
| DATA-007 | P2 | `obj.rune_essence` unresolved in MiningTutor objbox | **Fixed** | → `obj.blankrune` (Jagex internal name for rune essence, id 1436). Found by full-repo gameval sweep |
| DATA-008 | P2 | `seq.human_fletching_add_bolt_tips` (unsuffixed) in FletchingRows | **Fixed** | Jagex ships only per-metal variants `human_fletching_add_bolt_tips_{bronze,iron,blurite,steel,mithril,adamant,rune,dragon,ascension}` (seq 8472–8479, 15158). All 12 tipped rows now pass the anim matching their bolt metal; dragon-tip rows use `_dragon`. Broad bolts → `_rune` (no `_broad` variant exists; closest tier) |
| DOCS-001 | P3 | AGENTS.md revision 240.2 | **Fixed** | → 241 (both mentions) |
| NPCANIM-001 | P2 | GWD spiritual mage/ranger `seq.human_sword_slash`+crush looked style-wrong | **Verified — cache-faithful** | LIVE Jagex cache params for all 12 spirituals (4 factions × mage/ranger/warrior): `65500(attack_anim)=390` and `65407(attack_type)=65534(crush)` — identical to the overlay. `seq.human_sword_slash`=390. The overlay mirrors Jagex's own packed params; NOT a defect. Source: direct LIVE vs SERVER def dump of npc ids 3166-3168, 2242-2244, 2210-2212, 3159-3161 |
| NPCANIM-002 | — | generic human_* fallbacks on multi-style npcs | **Closed — no defect found** | `human_*` anims on NPCs are Jagex param-driven; the two fabricated ones found (`human_mine_bronze_pickaxe`, `human_fletching_add_bolt_tips`) are fixed (DATA-005, DATA-008). Remaining `human_*` refs resolve to real seqs |
| WEAPON-001 | — | unarmed stance-3 block plays punch | **Verified — correct** | `MeleeAnimationAndSound` stance3 default `human_unarmedpunch` is authentic: unarmed "Block" style still attacks with punch and grants defence XP; `human_unarmedblock` is the DEFEND anim and is correctly wired as `defend_anim` (`PlayerExtensions.kt:88`). No defect |
| INFO-001 | Info | revenant content absent | Backlog | ForinthrySurgeKillHook now resolves but needs `npc.wild_cave_*` spawns to fire |
| DATA-009 | P3 | `varp.barrows_state` duplicated in `varp.toml` | **Fixed** | Consolidated to one block (`isServerOnly` + scope/transmit union). All 18 raw-cache server tables re-scanned — no other in-file duplicates |
| SWEEP-001 | — | Full-repo gameval sweep | **Done** | 3,714 files (kt/toml/yaml) scanned vs rev-241 binary gamevals (structural parse, 14 sections, exact byte-consumption) + `.data/gamevals/*.rscm` + plugin `gamevals.toml` + `id="t.n"` registrations. Initial 2,868 flags → dirty-parse artifacts → **13 real candidates** → 3 fixed (DATA-004, DATA-007, DATA-008) + 10 false positives (KDoc `seq.foo`/`npc.ice_block`/`obj.x`, `startsWith` prefixes `cup_guthix_rest`/`trail_elite_riddle_key`/`slayer_helm_i_`/`uncut_`/`barrows_door_`/`levelup_list_`, concat prefixes `duke_extremity_cone_`/`duke_door_eye_01_rouse_0`). **Every real symbolic reference in the codebase now resolves.** |

## Validation log
- `:or-cache:test` PackServerConfigOverlayMergeTest — 4/4 pass
- `:or-cache:buildCache` — success, rev-241 LIVE+SERVER rebuilt with merged defs
- `:api:game-process:test` ServerCacheOverlayVerificationTest — 5/5 pass vs SERVER cache (huntMode/huntRange/respawnRate/paramsRaw on callisto, bandos avatar, saltfish, lesser_demon, zamorak spiritual warrior)
- `:api:combat:combat-formulas:integration` — full suite pass incl. DefensivePrayerBonusTest
- `gradlew test` — **all 130 test tasks pass** (5m36s)
- `gradlew spotlessCheck` — green (needed apply on new files + 2 dev-branch files: Nex.kt unused import, BossEncounterAttackForcedTest endings)
- All touched modules compile: fletching-pack, lumbridge, wilderness, gemstone-crab, combat-formulas, api/game-process, or-cache
- LIVE vs SERVER NpcType dump (15 npcs): identical client defs confirmed; server overlay fields verified via NpcServerType instead (osrs-mcp CacheTool can't see them — that path was wrong, replaced)
- NOTE: earlier "passed" osrs-mcp runs were silent `assumeTrue` skips (test workingDir ≠ repo root); replaced by api/game-process test which has game-cache-test-conventions workingDir

## Remaining
- [x] NPC anim audit — spirituals verified cache-faithful; fabricated fallbacks fixed
- [x] Weapons/equipment: unarmed stance anims verified authentic (WEAPON-001); `gublinch_snowball` stance/defend params confirmed matching unarmed pattern
- [ ] Drop tables / shops / quests / interfaces coverage (engine semantics verified earlier; per-row wiki compare of ~556 tables not feasible — documented)
- [ ] Engine deep review
- [ ] MCP live verification of changed npcs (MCP tool listing flaky this session)
- [ ] Revenant content (missing feature — backlog)
- [ ] Full `gradlew test` sweep + push branch

## Phase 2 — Drop-table coverage remediation

### Root-cause findings (parser)
- `WikiDropParser.extractSection(wikitext, "==Drops==")` only matched that exact heading. Monsters with level/variant sections (`==Level 82, 87, and 94 drops==`, `==Standard drops==`, `==Drops (level 13)==`) were dumped as `no_drop_section` — this produced the false claim that ~655 monsters had no drops. Broadened `dropTableHeadingPattern` to match any `==…drops==` heading.
- `npcIdPattern` required `id\d+` and skipped bare `|id =` infoboxes (single-variant pages). Fixed to `id\d*`.
- `parseNpcIdsByDropTable` / `npcIdsForDropVariant` now map `version<N>` names (e.g. `version3 = Level 26`) to `id<N>` for variant-labelled sections; bare-id pages bind all ids.
- `GenDropTables.py` (exploratory generator at repo root, provisional): fetches live OSRS Wiki raw pages, parses `DropsLine`/`DropsTableHead`, resolves `obj.*`/`npc.*` from `.data/gamevals-binary/gamevals.dat`, merges per-dropversion lines into per-section tables (repo convention: union+dedupe like `abyssal_demon_standard_catacombs`), binds only unclaimed ids per variant, normalizes `total` to weight-sum when wiki per-version rarity variants push sum over denominator (probability-preserving), emits `brimstone_key_roll`, guaranteed/pre-roll/tertiary blocks. Nested `{{refn}}` inside `raritynotes` handled via balanced-template parsing.

### Coverage counts (after remediation)
- Drop tables on disk: 600 TOML + Kotlin DSL tables; bound NPC symbols: 2,406.
- Structural validation (all 600 files): every `obj.*`/`npc.*` reference resolves against packed gamevals; every `[main]` pool's weight sum ≤ `total`. **0 violations.**
- Spawned NPC symbols: 6,384. Spawned-attackable (dump.npc `opN=Attack`): 1,833.
- Attackable spawned NPCs still unbound: **458** (was 592 → now 458 after 44 new tables from 22 pages; earlier partial batches had lowered it to 454 — recount includes all bound symbols).
- Remaining unbound-attackable classification (spot-checked): dominated by quest-instance variants (`brain_zombie_pirate_*`, `battlefield_*`, `akd_barbarian_*` — quest-only/no-drop in OSRS), utility/phase NPCs (`abyssalsire_lung`, `araxyte_*` — killed via parent mechanic or no loot), and pages needing manual variant review (`barbarian`, `babygreendragon`, `ardougne_archer`). Documented for follow-up; NOT counted as verified-correct.

### Newly generated tables (44 files, 22 pages)
lesser/black/greater demon (standard + Wilderness Slayer Cave), green dragon (L79 + WSC), ankou (members/F2P/WSC), giant rat (L3&6, L26), rat (regular + SoS), skeleton (plain/armed), zombie (L13, L18∪L24 merged file L24), hobgoblin (unarmed/armed), TzHaar-Ket (L149, L221), white knight (L36, L42), dark warrior (L8, L37-62, L145), highwayman (hood/no-hood), jail guard, cave bug (L6/L96), terror dog (L100/L110), farmer (members), hero, jungle spider, tortoise (with/without riders), bandit (L22, L130), rogue (L15, L135).

### Known limitations of generated data (honest dispositions)
- `Clue scroll (elite)`/`Reward casket (elite)` unresolved on green dragon + ankou tables — clue/casket indirection needs engine convention check (dropped, not emitted).
- `Brimstone key` template rarities (`{{Brimstone rarity|N}}`) on black demon/greater demon/ankou: emitted as `brimstone_key_roll` where parseable; template-valued rarity lines skipped.
- `Tokkul` (TzHaar currency) skipped — engine convention for currency drops unverified.
- `Perfect shell` (tortoise), `Curved bone` 1/5012.5 fractional rarity — unresolved/skipped.
- Weight-sum normalization: 9 tables had wiki per-variant rarity lines whose merged sum exceeded the denominator; `total` raised to sum preserving relative probabilities. Flagged for manual review: ankou members/F2P, hobgoblin armed/unarmed, tortoise-with-riders, white knight L36/L42, zombie L13, bandit L22.
- Unspawned variant ids within generated tables bind regardless of spawn presence (engine tolerates; harmless).

### Validation log (phase 2)
- `GenDropTables.py` self-validation pass over all 600 monster tables: 0 unresolved `obj.*`/`npc.*`, 0 weight overflows.
- `:tools:wiki-dumping:compileKotlin` — clean (parser regex fixes).
- Coverage recount scripted: spawned vs bound vs attackable from `osrs-dumps/dump.npc`.
- Full `gradlew test` re-run: pending (not re-run this phase — prior 130-task pass stands for untouched modules; generated TOMLs are data-only, loaded at startup).
- Live MCP verification: still unavailable this session — remains blocked.

### Still deferred
- 458 unbound-attackable classification into no-drop/missing-table/blocked (spot-checked only).
- Shops/stock/prices audit, quest/dialogue audit, interface audit — not yet systematically examined.
- `GenDropTables.py` disposition: provisional tooling; either port logic into `tools/wiki-dumping` Kotlin or remove after review — kept in repo for reproducibility of this phase's generated data.
- Clue/casket/Tokkul/perfect-shell conventions vs `docs/drops.md`.

## Phase 3 — Runtime validation + live MCP verification

### Critical defects found & fixed this session
- **Loader semantic violation (boot crash):** `RSWeightedTableBuilder` requires `sum(weights) == pool total` exactly. Phase-2 tables emitted comment-only padding → `IllegalArgumentException` at startup. Fixed: explicit `[[main.entries]] nothing = true` padding rows + 'Nothing' wiki lines emitted as weighted nothing entries.
- **Duplicate npc binding (boot crash):** `DropTableRegistry` rejects one npc in two tables. Wilderness Slayer Cave variants reuse the same npc ids as base tables → union-find merge in the generator produces one merged table per overlapping id set (matches existing 'Standard and Catacombs' convention).
- **Looting bag dropped outside wilderness (live-caught):** `obj.looting_bag` emitted without `should_drop_looting_bag = true` — live kill of a giant rat at Lumbridge produced a looting bag. Fixed in generator + patched all 18 generated files (62 pre-existing files already correct; engine gate = `Player.shouldDropLootingBag()` → `isInWildernessBasic()`).
- **Clue scrolls:** `Clue scroll (tier)` now resolves to canonical clue objs (`trail_clue_beginner/easy_simple001/medium_sextant001/hard_map001`, `trail_elite_emote_exp1`) with `clue_scroll_box = true`; `Reward casket` lines skipped (same roll).

### Live MCP verification (RESTORED this session)
- Server: `gradlew run` — **boots clean** with all 600 tables, 677 scripts, rev-241, baseXpRate=1.0.
- Client: RSProx + dev-tools MCP at 127.0.0.1:7780 — **functional**.
- `devin ai` account: bcrypt password reset in embedded Postgres (`public.accounts`) to documented value — login verified LOGGED_IN.
- **Lesser demon kill (live):** `Vile ashes` guaranteed + `Death rune` ×3 main-table roll — table functions end-to-end.
- **Giant rat kill (live):** `Bones` guaranteed + `Raw rat meat` — correct per wiki; no looting bag outside wilderness after fix.
- **Callisto (live):** spawned, immediately aggro'd a player (`interacting: Devexity`), attack anim 10012, HP 96% — Phase-1 overlay merge (hunt/combat fields) functioning.

### Validation
- All 600 TOML: `sum(main.entries.weight) == total` strict, every obj/npc symbol resolves, all `shared` names in SHARED_TABLES, zero duplicate npc bindings.
- `:tools:wiki-dumping:compileKotlin` clean.

### Still outstanding
- 458 unbound attackable NPC classification (partial — spot-checked categories only).
- 9 normalized tables: now satisfy loader via sum-normalized totals; wiki-internal inconsistency documented — probabilities preserved relatively, flagged for manual review.
- Shops/stock/prices, quests/dialogue, interfaces systematic audits — NOT done this phase.
- `Gradlew test` full suite not re-run (data-only changes; boot+loader validation is the coverage for TOML data).
- GenDropTables.py remains provisional tooling pending Kotlin port.

## Phase 3b — NPC reconciliation, inventory counts, full test sweep

### Unbound-attackable NPC reconciliation (partial)
Complete prefix classification of the 458 remaining unbound attackable spawned NPCs:
dominated by quest-instance groups (`brain_zombie_pirate*` ×31, `dt2_strangled*` ×8,
`tourtrap_qip_*` ×13, `fai_barbarian*` ×17, `lotr_*` ×18, `misc_*` ×12,
`battlefield_*` no-drop quest NPCs) — these are scripted/quest NPCs, not ordinary
killable reward sources; documented, not silently resolved.
Actionable cases resolved this batch:
- `flesh_crawler_members_worlds.toml` + `flesh_crawler_free_to_play_worlds.toml` (3 syms incl. `sos_fam_fleshcrawler`)
- `barbarian_level_8_9_and_10.toml` (18 syms; L15/17 already covered)
- `baby_green_dragon.toml` (guaranteed `babydragon_bones`, 2 syms)
- Variant binds added: `skeleton_armed/2/3`→armed, `skeleton_unarmed2/3/4`, `skeleton_unagressive/2/3`→plain,
  `zombie_unarmed_city1-6`, `zombie_entranan1-5`→zombie L24 union, `dungeon_rat`→SoS rat table.
Remaining unbound-attackable: ~430, pending manual wiki review per NPC group.

### Inventory counts (recorded, NOT fully audited)
- Drop tables: 599 TOML (net after union merges) + Kotlin DSL tables; 2,406+ bound npc syms.
- Shops: 28 Kotlin shop sites; shop data is cache-dbtable driven (`dbtable.shop_currency`,
  Omnishop stock/currency rows) — stock/prices inherit packed-revision accuracy by
  construction; custom shop logic only partially reviewed.
- Quests: 105 quest-related Kotlin files — systematic stage/dialogue audit NOT performed.
- Interfaces: 86 interface Kotlin files — systematic functional audit NOT performed.
- MCP live checks this phase: lesser demon, giant rat, Callisto aggro/anim — all PASS.
- `devin ai` account password reset in embedded Postgres (bcrypt); login verified.

### Validation (phase 3b)
- Full-table validation: 0 unresolved symbols, 0 weight-sum violations, 0 duplicate
  npc bindings, all shared names valid.
- `gradlew test` full suite: result recorded below in commit message / run log.

### Phase 3c — remaining unbound NPC reconciliation (completed)

Progression: 458 → 416 → 257 → 189 → **103 unbound attackable** (700→701 TOML tables).

Bulk wiki-name sweep (187 unique display names): +104 generated tables, 0 defects.

Variant/cover binds added this batch (~80 syms): `brain_zombie_pirate*`×31→zombie_pirate,
`lotr_*`×25→skeleton/zombie, `akd_barbarian`×5→barbarian, `fai_black_guard`×4→black_guard,
`zombie_unarmed`→zombie L13, `rimmington_/slug2_hobgoblin`→hobgoblin, `dt2_strangled*`→strangled,
`paladin*`→paladin, `warrior_*`→warrior_rellekka, `dskin_/w_/ardougnian/misc_*` citizens→Man/Woman
(Kotlin DSL + woman.toml), `kalphite_lord*`→kalphite_guardian, `dungeon_dagganoth*`→dagannoth,
`kourend_hillgiant`→HillGiant.kt, `kourend_dark_warrior`→dark_warrior, `jail_black_knight`→BlackKnight.kt,
`newbiechicken`→Chicken.kt, `dt2_scar_leech`→AbyssalLeech.kt, `white_knight*`→white_knight L36/L42,
`olaf2_giant_bat`, `wbr_bear`, `wbr_cave_poisonspider`, `mm_/laboratory_spider`, `arena_scorpion`→king_scorpion,
`tut2_/newbie rats`, `zamorakghost`→ghost, `zombie2*`.

New tables: `revenants.toml` — all 11 `wild_cave_*` (Wilderness Slayer Cave) revenants bound;
mediocre pool reproduced at full wiki fidelity (28 entries, weights sum exactly 198);
ether guaranteed; uniques/artefacts/seeds/dmed + blighted sacks/food + 30% looting bag as
tertiary rolls. Rates are combat-level-scaled formulas on the wiki — denominators use
representative A=300; approximation documented in table header.

Final static validation: 701 tables — 0 weight-sum violations, 0 unresolved obj refs,
0 duplicate npc bindings.

#### Remaining 103 unbound — classified, no actionable drop table
- Boss-phase/minion entities: abyssalsire_lung, coa_scarab, dark_seren_healer,
  pmoon_boss_*_vis, dt2_scar_maze_2_pillar_repairer, ds2_golem_*, dream_inadequacy,
  my2arm_dontknowwhat_battle, soul_wars_avatar_red, mdaughter_multi_bear, tbwt_beast,
  ungadulu_good, mourning_shadow_beast, priestperil_*, soulbane_* ×6, kalrag*, blessed_spider*.
- Quest-instance/combat NPCs with no wiki drop table (verified via page fetch or
  classification): tourtrap_qip_* ×14, dttd_ham_guard_* ×5 (pickpocket-only), mcannonguard ×5,
  fortressguard ×4, clocktower_rat ×3, death_* ×3, battlefield_*/gnome, jail knight handled,
  renegade_knight, grip, ds2_golem_*, death_white_knight, ics_* ×3, dungeon-dummy types.
- No-drop ambient/utility: duck*, penguin, harmless_*, hunting_chinchompa* ×3,
  varlamore_hunterjerboa, shadeshadow ×6 (Loar Shadow — no drops), wilderness_ent,
  wilderness_rune_golem (NO DROP SECTIONS on wiki), ardougne_archer, knight_of_ardougne*
  (pickpocket targets), etc_guard (Skraeling), elid_mayor_guard, farming_market_guard,
  ranging_guild_guard, wcguild_forester, weaponsmaster, barrows_* (crypt spiders — no drops),
  slayer_mutated_zygomite_adolescent (uncertain — no wiki table emitted), lotr_possessed_pickaxe,
  vampire_leech (NO DROP SECTIONS), weaponsmaster.

Uncertain (flagged): slayer_mutated_zygomite_adolescent, knight_of_ardougne kill-drops,
desert-mining guard drop existence — wiki shows none; left unbound intentionally.

### Phase 3d — final unbound NPC completion (65 remain, all verified unactionable)

Progression: 103 → **65 unbound attackable** (701→710 TOML tables).

New tables: `guard_desert_mining_camp` (10 tourtrap syms), `loar_shade` (+6 shadeshadow),
`zygomite_slayer`, `ham_guard_postquest` (5 syms), `knight_of_ardougne` (bones-only, accurate),
`fortress_guard` (bones-only), `market_guard`, `archer_ardougne`, `guard_ranging_guild`.
Variant binds: `tbwt_beast`→the_shaikahan, `wilderness_ent`→ent, `weaponsmaster`→weaponsmaster,
`lotr_possessed_pickaxe`→possessed_pickaxe_haunted_mine, `battlefield_darkskinned_gnome_archer`
+`gnome`→gnome, `harmless_island_snake`→snake, `zombie2`→zombie L13.

#### Remaining 65 — every NPC wiki-verified (page fetched; NO DROP SECTIONS or no infobox)
- Boss-phase/mechanic entities: abyssalsire_lung, coa_scarab, dark_seren_healer,
  pmoon_boss_*_vis ×3, dt2_scar_maze_2_pillar_repairer, ds2_golem_* ×3,
  soul_wars_avatar_red, mdaughter_multi_bear.
- Quest-only combat (verified no-drop): dream_inadequacy, my2arm_dontknowwhat_battle,
  soulbane_* ×6, ungadulu_good, renegade_knight, grip, priestperil_* ×2,
  death_* ×3 (Soldier/Servant/White Knight — no sections), ics_* ×3, mcannonguard ×5
  (Dwarf Cannon quest), clocktower_rat ×3, tourtrap merc ×4 (Mercenary: NO DROP SECTIONS),
  mourning_shadow_beast (verified), kalrag* ×2, blessed_spider* ×2 (verified).
- Ambient/no-drop: duck* ×3, penguin, harmless_island_mosquito, hunting_chinchompa* ×3
  (Chinchompa: NO DROP SECTIONS), varlamore_hunterjerboa01, wcguild_forester,
  etc_guard ×2 (Skraeling: no infobox), elid_mayor_guard_1 (Tarik: NO DROP SECTIONS),
  wilderness_rune_golem (NO DROP SECTIONS), vampire_leech (NO DROP SECTIONS),
  barrows_spider/barrows_giantspider (Crypt/Giant crypt spider: NO DROP SECTIONS).

Boot-verified: "Server ready in 22.4s" — all 710 tables + Kotlin binds load clean.
Static: 0 weight-sum violations, 0 unresolved objs, 0 duplicate bindings.

## Fight Cave build plan (staged, verified)

Module: `content/minigames/fight-cave` (build.gradle.kts copied, dir exists)

Verified facts:
- NPC syms: `tzhaar_fightcave_swarm_{1a,1b,2a,2b,2spawn,3a,3b,4a,4b,5a,5b}` (Tz-Kih→Ket-Zek),
  `tzhaar_fightcave_swarm_boss` (Jad), `tzhaar_fightcave_swarm_boss_cleric` (Yt-HurKot healers)
- Locs: `loc.tzhaar_fightcave_wall_entrance`, `loc.tzhaar_fightcave_wall_exit`
- Arena: region `9551` (x 2368-2431, y 5056-5119), cave center ~(2417, 5085)
- Spawn API: `Npc(ServerCacheManager.getNpc(id), coords)` + `npcRepo.add(npc, duration)` (AdminCommands.kt:622)
- Instance pattern: `content/bosses/giant-mole/.../GiantMoleInstance.kt` — `InstanceScript`,
  `InstanceArea.copyRegions(regionIds, npcSpawns)`, `onInstancePlayerJoin`, `onExitObject`, `telejump`, `defaultLeaveFlow`
- Entry plumbing: `InstanceObjectHookRegistry.registerEnterTransition/Prelude`, `InstanceCreateScript.instanceEntry`

Blockers to resolve first:
1. `settingsRow()` requires real `dbrow.instance_fight_cave` — provision in a pack module `dbTable` +
   `src/main/resources/gamevals.toml` (custom id ~64682), then `gradlew buildCache`. Interim: reuse
   `dbrow.instance_giant_mole` (same InstanceSettingsRow type) if functional.
2. Wave scheduler: coroutine loop spawning via npcRepo; track spawned Npc list per session; next wave
   when all dead; wave 63 = Jad + 4 clerics; Jad death → `obj.fire_cape` (verify obj name) + exit telejump.
3. 63-wave authentic composition table (OSRS wiki formula: rotating pairs, boss-only final wave).
4. Then clone shell to: Inferno (same engine), Fortis Colosseum (Sol Heredit spec exists),
   NMZ (`nzone_*` spec'd NPCs — cheapest), Barbarian Assault (penance waves + role varps + horn).

## Wave-arena minigames — built (commit 249f82bfa, bc5dc3af6)

Architecture: programmatic instance via `InstanceManager.create(player, KEY, InstanceSpec(settingsRowId=0))`
+ `InstanceArea.copyRegions` — no dbrow needed. `Npc(sym, coords)` + `npcRepo.add` + `manager.attachNpc`
for runtime spawns; `player.softTimer` drives wave-clear polling (3-tick); `NpcDeathKillHook`/
`PlayerRespawnHook` set-bound via PluginModule.

- **fight-cave** (`content/minigames/fight-cave`): 63 authentic waves, Tz-Kek split on death,
  Jad healers at 50%, `obj.firecape` on Jad kill, enter/exit via `loc.tzhaar_fightcave_wall_*`,
  region 9551, respawn → TzHaar city. Compile+boot verified (785 scripts). NOT MCP-runtime-tested
  (client down at build time) — first live test: enter at wall in TzHaar city, verify wave 1
  Tz-Kih spawn + wave progression + instance teardown.
- **inferno** (`content/minigames/inferno`): 66 build waves + wave 67 triple JalTok-Jads +
  wave 69 TzKal-Zuk, Jad/Zuk healers at 50%, `obj.infernal_cape` on Zuk kill,
  `loc.inferno_entrance` dual-purpose in/out, region 9807. KNOWN GAPS vs real Inferno:
  no Zuk moving shield, no pillar safespots, Jal-Ak doesn't split, healers are approximated,
  wave table follows documented structure not the exact wiki list. Compile+boot verified; untested live.

Blocked/uncertain for remaining three:
- **Fortis Colosseum**: only `colosseum_minotaur/manticore/jaguar_warrior/shockwave_colossus/
  javelin_colossus/doom_scorpion/bees/sol_p1` exist in npcs — the Fremennik warbander trio and
  serpent shaman combat NPCs are NOT in rev-241's npc table (seq/anim names exist only).
  Can't populate authentic waves without them.
- **Barbarian Assault**: penance NPCs + Penance Queen spec exist, but needs 4-role interfaces,
  horn-call varps, team lobby logic — biggest interface build of the set.
- **Nightmare Zone**: `nzone_*` boss variants (normal+hard) + `npc.nzone_host` (Dominic Onion,
  Yanille 2608,3116) exist; arena copy region unconfirmed (no nzone locs in map dump);
  needs dream-selection interface + points varp + reward chest shop.

## Wave-arena engine findings (verified at runtime, commit 6c029aa40)

How NPC chase actually works — reuse this for BA penance waves, NMZ rumble, Colosseum:

- `NpcMovementProcessor` routes a `RouteRequestPathingEntity` to a single target tile via
  `RouteFinding.naiveDestination` (nearest tile on the target's perimeter), then steps ONE tile per
  tick through `StepFactory.validated` — diagonal first, then horizontal, then vertical.
  There is NO A*: if all three candidate steps are blocked, the npc freezes in place forever.
- `StepFactory` is injectable and `StepValidator` (directional WALL_* flags + LOC + FLOOR_BLOCKED)
  is what it uses — tile-level "is this tile open" checks are insufficient. Spawn validation must
  simulate `steps.createPath(from, naiveDest, size, extraFlag, CollisionStrategy.Normal)` and
  require `path.last() == naiveDest`. `extraFlag = BLOCK_NPCS` unless `moveRestrict == PassThru`.
- Consequence: ring-of-walkable-tiles spawning must validate reachability with that exact sim,
  and arena anchor points must be on open floor. The Inferno `enterCoord` originally landed inside
  the walled antechamber (source 2464,5088 unwalkable) — waves could never path in; moved to
  source (2464,5100) = `RegionLocal(0, 38, 79, 32, 44)` (z-local = source_z - region_z*64).
- `npcRepo.add(npc, Int.MAX_VALUE)` sets `npc.respawns = true` — dead wave npcs respawn.
  Use a finite lifetime (`100_000`).
- `Npc.hasInteraction()` is internal — check `npc.mode` against `NpcMode.OpPlayer2/ApPlayer2`
  for "is engaged" re-aggro checks in wave timers.
- `npc.setHunt(range)` + `setHuntMode` (stalk gamevals: `constant_melee=1`, `constant_ranged=2`)
  alone does NOT bridge long distances — native hunting is proximity-gated via `isAnyoneNear()`
  (info protocol active). Explicit `opPlayer2`/`apPlayer2` engagement at spawn + re-engage on
  mode-drop each wave-check tick is what makes arena chase reliable.
- Binding ops without a gameval: `onProtectedEvent<NpcEvents.Op1>(rawNpcId)` and
  `onProtectedEvent<LocEvents.Op1>(rawLocId)` — event keys are `type.id`. Custom gamevals in
  `[gamevals.npc]` CANNOT alias existing ids (must exceed max base id ~16771).
- `manager.resolveCoord(session, coord)` maps source-region coords into instance space;
  `manager.sessionForPlayer(player)`, `manager.sessionForId(id)`, `manager.attachNpc(sessionId, npc)`
  cover the session lifecycle. Spawn in source coords? No — spawn in resolved instance coords or
  use the instance region's absolute coords.
- Guard wave advance on spawn success: if every ring tile fails reachability (player in a nook),
  `spawnWave` must not count the wave as spawned or the timer races to the boss wave.

Live-verified on Inferno (region 9807 copy): Talk-to TzHaar-Ket-Keh (11247) -> instance ->
wave 1-3 Jal-Nib/Jal-MejRah spawn on reachable ring tiles, walk in, trade hits; vent door
(loc 30266, bound via raw-id op1, city-side inert) exits to `CITY_EXIT` (2494,5115).

### Menu/dialogue-resumed coroutines die inside instance enter transitions (Ket-Rak)

- A `ProtectedAccess` coroutine that resumes from `menu()` never returns from
  `withInstanceEnterTransition` — its trailing `delay()` loses protected access and the
  coroutine dies silently *after* `telejump`/`finalizeEntry` ran. Player arrives in the
  instance but no post-transition code (mes, spawns, timers) ever executes; no exception
  is logged.
- Fix (matches upstream `InstanceScript.onEnterPrelude` modules like Amoxliatl): schedule
  post-transition startup on `WorldQueueList.add(ticks) { ... }` **before** calling the
  transition, so it runs even if the coroutine never resumes. `enter()` becomes: create ->
  queue startup -> withInstanceEnterTransition { telejump; finalizeEntry }.
- Verified on TzHaar-Ket-Rak's Challenges: Talk-to `npc.jad_challenge_master` -> menu ->
  challenge 1 spawns JalTok-Jad (id 10623, level 900, **size 5**) in a copied Fight Cave
  arena (region 9551); kill -> "Challenge complete!" + authentic 80 TokKul; safe death
  respawns beside Ket-Rak; Fight Cave's `wall_exit` op doubles as the generic exit because
  `leave()` is not key-gated.
- Large-footprint spawning: JalTok-Jads are size 5 — the shared ring spawn needs
  `SPAWN_RADIUS_MAX >= ~24` near arena edges or multi-jad challenges run out of valid
  5x5 footprints.

## Phase 3 — NPC combat animation coverage

### Scope and method
- Source of truth: `param.attack_anim` / `param.defend_anim` / `param.death_anim` in `.data/raw-cache/server/npcs.toml`, resolved against the rev-241 `seq` gameval table (15,157 entries parsed from `.data/gamevals-binary/gamevals.dat`). Every `seq.*` reference written was validated to resolve before packing.
- Donor pass: copied complete anim triplets from populated same-family NPCs to unpopulated same-family NPCs (656 fills).
- Rule passes: family-specific canonical sets for TzHaar city (`thzaar_unarmed/armed/ring/staff/magic_attack`, `thzaar_parry`, `thzaar_death`/`thzaar_staff_death`), dragons (`dragon_attack/block/death`), wyverns, vampires, zombies, skeletons, dwarves, dagannoths, hydra stages, Araxxor (`npc_araxxor_01_*`), Callisto, Venenatis, Cerberus, Zalcano, Sarachnis, Corporeal Beast, Abyssal Sire, slayer creatures (`monalisk_*`, `suqka_*`, `lore_*`), humanoid fallbacks (`human_sword_slash`/`human_unarmed_def`/`human_death`), gnomes, mummies, minotaurs, golems, and many quest/minigame NPCs.
- Result: ~2,400 spawned combat NPCs now carry anim params; invalid refs fixed during validation (`seq.duckblocker`, `seq.drake_death`→`seq.drake_death_01`, `seq.deathwing` removed).

### Live verification (MCP, world 255, account `devin ai`)
- Black dragon (npc 252): hunt→aggro→attack `dragon_attack`=80, block `dragon_block`=89 on being hit, death `dragon_death`=92 on kill (`isDead` frame captured), stand `dragon_ready`=90. Drops verified: dragon bones + black dragonhide + addy darts + ruby bolts (e).
- Green dragon (npc 260): kill produced coins + dragon bones + green dragonhide.
- Iorwerth Archer (npc 3428): ranged-attack verified — `human_bow`=426 animation with real arrow projectile (proj 9) fired from distance 4; auto-retaliate kill + drops.

### Remaining gaps — intentional / cache-limited (21 NPCs)
These spawned combat-flagged NPCs lack `defend_anim` and/or `death_anim` because **no corresponding seq exists in the rev-241 cache** or the NPC is a scripted/inactive form:

| NPC | Missing | Reason |
|-----|---------|--------|
| `araxxor` | defend | no `npc_araxxor_01_block/defend` seq exists — Araxxor does not flinch |
| `araxyte_normal` | defend | no araxyte defend seq exists |
| `amoxliatl` | defend | `amoxliatl_*` set has attack/death/idle/walk only |
| `chaoselemental` | defend | `chaoselemental_*` set has no block seq |
| `drake` | defend | `drake_*` set has melee/ranged/death/ready/walk only |
| `hydra`, `hydraboss` | defend | `hydra_stage_*`/`hydra_lesser_*` sets have no block seq (hydraboss death filled with `hydra_death`) |
| `elder_custodian_stalker`, `mature_custodian_stalker` | defend | `npc_custodian_stalker0[2-5]_*` sets have attack/death/stomp/howl, no defend |
| `sourhog` | defend | `sourhog_*` set has attack/death/idle/walk only |
| `gim_boar` | defend | `npc_boar_combat_*` set has melee/death/idle/walk only |
| `pmoon_sulphur_nagua` | defend | `frost_nagua_slayer_*` set has basic/death/spawn/special only |
| `pmoon_boss_blue_moon_vis`, `pmoon_boss_eclipse_moon_vis` | defend+death | `_vis` display variants — anims are scripted on the real boss NPCs |
| `abyssalsire_sire_stasis_sleeping` | defend | sleeping/untargetable stasis form; `sire_sleeping`/`sire_waking` exist for the scripted wake |
| `swampcrab_inactive` | defend+death | dormant rock form — waking is scripted; combat anims belong on the active variant |
| `duck`, `duck_female`, `duckling`, `duck_update_ducklings` | defend | no duck/duckling block seq exists in cache |
| `macro_shade` | death | shade set has `shade_rise`/`shade_sink`/`shade_heaven`; kill-anim for the random-event variant is ambiguous — left unfilled rather than guess |

## Phase 4 — Bork + Smork Brothers duo encounter (content/bosses/bork)

### Implementation
- `BorkEncounter` — private instance (`InstanceArea.copyRegions` of the Chasm of Tears
  guardian floor, region 12948 level 2, entry `RegionLocal(2,50,148,24,47)`), spawned
  programmatically: Bork (`npc.tfa_bork`, 1500 HP) + the Smork Brothers' cannon cart
  (`npc.tfa_smork`, 250 HP, `movementLocked`). World entrance placed via
  `locRepo.add` (`loc.sotfa_cave_entrance_op` at 3223,9516,2); instance exit placed on
  spawn (`loc.sotfa_cave_exit_op`).
- `Bork` — `BossPluginScript` spec: `beat` (ring of tiles immediately around Bork's
  footprint) and `big_beat` (3-deep ring, used when the target stands outside melee
  range) as crush-accuracy `tileAoE`s; `forceEveryAttacks(8,10)` drives the
  `"Bork smash <name>!"` tank retarget via `external("bork.retarget")` →
  `AiPlayerInteractions.opTrigger(Op2)`.
- Smork volleys: alternating ranged (`npc_smork_range_attack_projanim01`) and magic
  (`npc_smork_mage_attack_projanim01` + `npc_smork_mage_hit_spotanim01`) cannon shots at
  every player who is not Bork's current tank, plus alternating specials every 14
  ticks — incendiary (`npc_smork_napalm_attack_projanim01` → 3 lingering
  `napalm_splat` tiles dealing 6–7/tick, also damages Bork 25–30 if the blast catches
  his footprint) and heavy cannonball (50/50: stuns Bork for 12 cycles via
  `npc_bork_12_cycle_stun01` lifting his flat reduction, or a triple stomp covered by
  `npc_bork_enraged_stomp_attack01` hitting every tile except the cart's 1-tile
  shadow).
- Flat armour: `onModifyHit` subtracts 8 + 2·(missing-HP quarters) from melee/ranged
  hits and 2·quarters from magic hits; during the 12-cycle stun melee hits are floored
  at 1 (wiki: "always land"). Capped at the max quarter tier once the Smorks die.
- Enrages: Smorks dead → Bork stomps every ~10 ticks for the rest of the fight;
  Bork dead (`onLethal`) → Smorks volley every 3 ticks at +8 max hit.
- `npcs.toml`: `tfa_bork` huntMode 1/huntRange 14/maxRange 20 + gong-melee attack,
  `npc_bork_death01` death, crush attack type; `tfa_smork` pinned static
  (`moveRestrict=Blocked`, `defaultMode=None`, `wanderRange=0`).

### Verified
- `gradlew :content:bosses:bork:compileKotlin` clean; `buildCache` packs server `npc`
  config clean; server boots — plugin module registers with no spec/gameval errors.
- Cache evidence: full `npc_bork_*` and `npc_smork_*` seq + `*_spotanim01` +
  `*_projanim01` families present in `.data/gamevals-binary/gamevals.dat`;
  `sotfa_cave_entrance_op`/`sotfa_cave_exit_op` are the real archive cave loc symbols
  (`tfa_cave_*` does not exist — first attempt crashed on `Missing mapping`, corrected).

### Unverified / inferred (needs live test once client is stable)
- Damage numbers are inferred where the Wiki does not publish them: beat ~0–28,
  big-beat ~0–42, stomp ~0–55, volleys ~0–15/0–18 (+8 enraged), incendiary ~0–30,
  oil 6–7, Bork-burn 25–30, special cadence every ~14 ticks, 50% stun odds.
- Smork HP 250 and Bork HP 1500 assumed from Wiki boss data; Bork's defence as flat
  reduction is implemented per Wiki description but exact per-quarter values inferred.
- Live MCP test not yet performed: client crash-looped after RSProx respawned it
  (devtools plugin unreachable); retry when the client is manually restarted.
- Bork/Smork drop tables not yet bound; archive music (`music_tfa_boss_bork`) and the
  HP-bar UI (`tfa_bork_smork_hp_ui_handler`) not wired.

## Phase 4b — RESUME STATE: Archive Guardian implementation notes (next boss)

The Fractured Archive is a pseudo-linear raid (8 bosses, 5/run): Archive Guardian
always 1st -> choice of two doors per wing (2nd: Ezekiel OR Bork+Smorks) -> ... ->
Ocellus always last. No per-boss drops; all loot is the post-Ocellus reward chest
(items: Archivist's folio, Horn of Bran, Zorya's tome, Rondache, Ascension
crossbows, Obligator, Orion pet, 4 elemental spell fragments; lobby has loot
wheelbarrow fallback). Raid lobby in Chasm of Tears, 1-8 players.

Bork module (`content/bosses/bork`) is the template — copy its structure:
BorkEncounter.kt (InstanceManager, region-copy arena via InstanceArea.copyRegions,
programmatic locs) + Bork.kt (BossPluginScript DSL spec) + BorkArena.kt (geometry).

Cache symbols confirmed in gamevals.dat (rev 241):
- NPCs: `npc.archive_guardian`, `npc.archive_guardian_combat`,
  `npc.archive_guardian_tentacle`, `npc.archive_guardian_mushroom_spawn`
- Seqs: `npc_archive_guardian_spawn01`, `spawn_idle01`, `idle01`,
  `melee_attack01`, `range_attack01` (+`range_projanim01`, `range_spotanim01`,
  `range_hitanim01`), `special_attack01` (+`special_attack_spotanim01`),
  `swoosh_attack01`, `enraged_phase01` (+`_spotanim01`), `death01`,
  `death_pose01`, `ground_to_flying01`, `idle_to_ground01`, `ground_idle01`
- Mechanics to research on https://oldschool.runescape.wiki/w/Archive_Guardian :
  flying/ground phase transitions (ground_to_flying / idle_to_ground seqs),
  tentacle NPC, mushroom spawns, enrage phase. Multi-NPC phases like Bork's.

Pending before Archive Guardian work:
- Wiki mechanics research (attack rotation, phases, special, tentacle role).
- Arena: same region-copy pattern; Archive Guardian room region id TBD — reuse
  Chasm of Tears floor (region 12948 lvl 2) if no better baked room.
- Live verification still blocked: RSProx/RuneLite client crash-loops after
  respawn; `osrs-dev-tools` MCP unreachable. Fix = manually restart the game
  client (not via MCP `respawn_client`), then `mcp_list_tools` should recover.
- `npcs.toml` incremental pack warning ("Block npcs.toml (npc) was found by the
  scanner but not in the decoded file") recurs on client-side repack; server-side
  packs clean. Suspected incremental-scanner quirk, unverified.
- 458 spawned attackable NPCs still lack drop-table bindings (batch task,
  GenDropTables.py exists in repo root).
- After each boss: regenerate PROGRESS.md via tools/progress/content-progress.mjs,
  commit + push.

## Phase 5 — TFA raid merge (athoren/feat/tfa-2) + live verification

### Merged (61 commits, 186 files, ~15.6k LOC)
- `content/raids/tfa/` — full raid: lobby + party system (TfaLobby, TfaPartyScript,
  raid-entry loc), TfaFight orchestrator + TfaRaidHud, 5 encounters
  (Guardian, Bork+Smorks, Dreadnoth, Xarth, Ocellus — each with Arena/Fight/Boss/
  Encounter/Hooks/InstanceScript/Cheats), reward vault (TfaVault + TfaChestDropTables
  + TfaUniqueClaim), pack module with team-scaling dbtables (62000-62003)
- `content/raids/party/` — generic raid party registry/tested module
- `api/bosses/` — DSL extensions (summon.behaviour, external triggers, more effects)
- Engine: NpcApproach enum, scriptedMovement, interactsWhenUnderTarget, visType,
  NpcInfoProtocol +setBaseAnimationSet/setCombatLevel/setTransparency, ScriptedProjAnim
- Dev cheats: `::tfalobby`, `::tfa<boss>` teleport, `::<boss> start|hp|...` controls
- Conflicts resolved: gameval rscm unions, npcs.toml (their richer tfa defs over our
  standalone), EffectInterpreter (both summon hooks), ZoneUpdateTransformer (our
  Obj.copy + their ScriptedProjAdd), BossDeps param dedupe (kept upstream
  `playerInteractions` + `interactions` alias), TFA varns renumbered 64420->64500
  (collided with maggot-king)
- Removed content/bosses/bork — superseded by raids/tfa encounter

### Live-verified (MCP, world 255, account `devin ai`)
- Bork instance (`::tfabork`): teleported to instance region 41217, Bork lvl 871 +
  The Smorks lvl 640 spawned (matches Wiki)
- Fight (`::bork start`): Bork hunts + targets player; gong-melee attack
  `anim 14542 = npc_bork_gong_melee01` on 7-tick cadence (ticks 630,637);
  idle 14540 = npc_bork_idle01; walk 14539 = npc_bork_walk01
- Smorks: alternating cannon volleys (2 projectile ids) + `14554 =
  npc_smork_cannon_attack01` anim; 17-damage hitsplat landed on player
- Death flow: player killed by Bork+volleys -> Lumbridge respawn, instance torn down
- Guardian instance (`::tfaguardian` + `::guardian start`): spawn plays
  `14755 = npc_archive_guardian_spawn01` + spotanim 4206, transitions to
  `14757 = npc_archive_guardian_spawn_idle01`, hunts player; inert sentries +
  4 choice doors (Enter/Quick-enter) present in room
- Dreadnoth (`::tfadreadnoth` + `::dreadnoth start`): instance opens, lvl 888
  spawns dormant; on approach hunts player — `dreadnoth_idle01`(14776) idle,
  `dreadnoth_slide01`(14778) sliding walk; hits landed both ways
- Xarth (`::tfaxarth` + `::xarth start`): instance opens, lvl 912 + corpse-heaps
  spawn; `ready` starts the fight (`Challenge started: Xarth.`), debug shows
  phase=p1 wave=1 heaps=4 slot cycling — wave/corpse mechanics tick correctly
- Ocellus (`::tfaocellus`, walk past antechamber, `::ocellus start`): Hooded
  Figure transforms into Ocellus lvl 1121 (same npc index), `14806 =
  npc_ocellus_phase1_basic_projectile_start` + `14805 = phase1_walk` observed;
  phase-1 fight engages, damage both ways at 96% boss hp; 4 god censers present
- Lobby (`::tfalobby` -> 4130,5555 real map): Archive Door, Grouping Board,
  Bank Chest, Alliance records, Strategy board — all match Wiki lobby
- Party flow: Grouping Board Inspect -> `tfa_lobby` overlay (975) renders
  No-Party roster; Archive Door Enter -> "not in a raiding party" gate ->
  "Form or join a party" -> `tfa_partylist` (977) opens with authentic steel
  frame + sortable columns
- Still untested live: vault loot (needs a full kill chain), party of 2+
  scaling, Ezekiel/Moia (not implemented), full Guardian->doors->Ocellus run

### Not in the branch
- Ezekiel + Moia encounters (raid's other 2 optional bosses) — still unimplemented
- The Fractured Archive miniquest + While Guthix Sleeps requirement gating
- Ascension rock mine in lobby (check if implemented elsewhere)
