# OSRS Fidelity Remediation Ledger

Baseline: `adf67c1d2` on `dev` → remediation branch `osrs-fidelity-remediation`
Revision: 241 (game.yml / README / cache consistent; AGENTS.md was stale)

Commits (in order): `1839ba1c5` cache merge fix · `be9e1d7d0` gameval refs + deadeye varbit · `98f2e3dd0` overlay verification test rewrite · `bbf0709a1` sweep-driven refs

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
| WEAPON-001 | — | unarmed stance-3 block plays punch | **Open — unverified** | no evidence gathered this pass; carried to remaining work |
| INFO-001 | Info | revenant content absent | Backlog | ForinthrySurgeKillHook now resolves but needs `npc.wild_cave_*` spawns to fire |
| SWEEP-001 | — | Full-repo gameval sweep | **Done** | 3,714 files (kt/toml/yaml) scanned vs rev-241 binary gamevals (structural parse, 14 sections, exact byte-consumption) + `.data/gamevals/*.rscm` + plugin `gamevals.toml` + `id="t.n"` registrations. Initial 2,868 flags → dirty-parse artifacts → **13 real candidates** → 3 fixed (DATA-004, DATA-007, DATA-008) + 10 false positives (KDoc `seq.foo`/`npc.ice_block`/`obj.x`, `startsWith` prefixes `cup_guthix_rest`/`trail_elite_riddle_key`/`slayer_helm_i_`/`uncut_`/`barrows_door_`/`levelup_list_`, concat prefixes `duke_extremity_cone_`/`duke_door_eye_01_rouse_0`). **Every real symbolic reference in the codebase now resolves.** |

## Validation log
- `:or-cache:test` PackServerConfigOverlayMergeTest — 4/4 pass
- `:or-cache:buildCache` — success, rev-241 LIVE+SERVER rebuilt with merged defs
- `:api:game-process:test` ServerCacheOverlayVerificationTest — 5/5 pass vs SERVER cache (huntMode/huntRange/respawnRate/paramsRaw on callisto, bandos avatar, saltfish, lesser_demon, zamorak spiritual warrior)
- `:api:combat:combat-formulas` DefensivePrayerBonusTest — pass
- LIVE vs SERVER NpcType dump (15 npcs): identical client defs confirmed; server overlay fields verified via NpcServerType instead (osrs-mcp CacheTool can't see them — that path was wrong, replaced)
- NOTE: earlier "passed" osrs-mcp runs were silent `assumeTrue` skips (test workingDir ≠ repo root); replaced by api/game-process test which has game-cache-test-conventions workingDir

## Remaining
- [x] NPC anim audit — spirituals verified cache-faithful; fabricated fallbacks fixed
- [ ] Weapons/equipment runtime verification (unarmed stance-3)
- [ ] Drop tables / shops / quests / interfaces coverage (engine semantics verified earlier; per-row wiki compare of ~556 tables not feasible — documented)
- [ ] Engine deep review
- [ ] MCP live verification of changed npcs (MCP tool listing flaky this session)
- [ ] Revenant content (missing feature — backlog)
- [ ] Full `gradlew test` sweep + push branch
