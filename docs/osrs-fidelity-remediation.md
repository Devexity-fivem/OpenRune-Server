# OSRS Fidelity Remediation Ledger

Baseline: `adf67c1d2` on `dev` → remediation branch `osrs-fidelity-remediation`
Revision: 241 (game.yml / README / cache consistent; AGENTS.md was stale)

| ID | Sev | Finding | Status | Evidence / changed files |
|----|-----|---------|--------|--------------------------|
| DATA-001 | P1 | 17 dup `[[npc]]` blocks; `associateBy` last-wins dropped earlier blocks | **Fixed+verified** | `npcs.toml` merged (0 dup ids); `PackServerConfigOSRS.collectBlocksByTable` deep-merges same-id blocks (params union, later-file wins). NEW: 63 cross-source dup ids also merge — `npc.lesser_demon` (pack wanderRange stripped server params), `npc.cassie`/`npc.bob` shop overlays etc. One real conflict resolved: `godwars_spiritual_zamorak_warrior` killcount_varp `total_armadyl_kills`→`kc_spiritual_warrior` (later block, matches other 11 spirituals). Tests: `PackServerConfigOverlayMergeTest` (4), `ServerCacheOverlayVerificationTest` (4, vs built SERVER cache) |
| COMBAT-001 | P2 | `defensivePrayerBonus` checked `varbit.prayer_hawkeye` for Deadeye +5% Def | **Fixed+verified** | `AccuracyOperations.kt:171` → `varbit.prayer_eagleeye` (matches ranged/magic ops' `eagleeye`+`deadeye_unlocked` pattern; Deadeye replaces Eagle Eye in prayer book). Hawk Eye gives no defence bonus per wiki. Test: `DefensivePrayerBonusTest` integration |
| DATA-002 | P2 | `npc.revenant_maledictus`/`obj.amulet_of_avarice` unresolved | **Fixed** | → `npc.wild_cave_superior` (id 11246, real maledictus symbol per binary gamevals + wiki npc id) and `obj.wild_cave_amulet` (id 22557). Audit's suggested `magic_rock_of_avarice` (id 33044) was a different item. NOTE: revenant spawns/defs still absent — hook now resolves correctly but cannot fire until revenant content exists (feature dependency, documented) |
| DATA-003 | P2 | `stat.runecraft` ×2 in CombatTutors | **Fixed** | → `stat.runecrafting` (stat.rscm:21). Lines 629 (magic tutor dialogue) + 862 (SKILLS list) |
| DATA-004 | P2 | `obj.tuna_potato` unresolvable | **Fixed** | → `obj.potato_tuna` (real binary gameval). Correction to audit: `tuna_potato=64500` was a legitimate `[gamevals.dbrow]` food entry, not an unmerged obj |
| DATA-005 | P3 | `seq.human_mine_bronze_pickaxe` fabricated | **Fixed** | → `seq.human_mining_bronze_pickaxe` (verified in binary) in `GemstoneCrabMiningScript.kt` fallback |
| DATA-006 | — | Stale `SailingChanceEncountersLostGoodsResourceRow` | **Not a defect** | `dbtable.sailing_chance_encounters_lost_goods_resource` exists in binary gamevals — audit false positive |
| DOCS-001 | P3 | AGENTS.md revision 240.2 | **Fixed** | → 241 (both mentions) |
| NPCANIM-001 | P2 | GWD spiritual mage/ranger `seq.human_sword_slash`+crush | Investigating | blocks merged; need cache/wiki/live evidence for correct anims |
| NPCANIM-002 | — | generic human_* fallbacks on multi-style npcs | Pending | |
| WEAPON-001 | — | unarmed stance-3 block plays punch | Pending | |
| INFO-001 | Info | revenant content absent | Backlog | ForinthrySurgeKillHook now resolves but needs `npc.wild_cave_*` spawns to fire |

## Validation log
- `gradlew :or-cache:test` PackServerConfigOverlayMergeTest — 4/4 pass
- `gradlew :or-cache:buildCache` — success (npc packing shows merged)
- `gradlew :tools:osrs-mcp:test` ServerCacheOverlayVerificationTest — 4/4 pass vs SERVER cache
- `gradlew :api:combat:combat-formulas:integration` DefensivePrayerBonusTest — pass (deadeye lowers hit chance, hawkeye doesn't, piety < deadeye)

## Remaining
- [ ] NPC anim deep audit (spirituals, unarmed stance-3, flagged generics)
- [ ] Weapons/equipment runtime verification
- [ ] Drop tables / shops / quests / interfaces coverage
- [ ] Engine deep review
- [ ] MCP live verification of changed npcs
- [ ] Revenant content (missing feature — backlog)
