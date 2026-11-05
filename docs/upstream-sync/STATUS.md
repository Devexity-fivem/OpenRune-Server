# Upstream Sync Status

Last review: 2026-11-05

## Repositories

| Role | URL | Branch |
|---|---|---|
| origin (our fork) | https://github.com/Devexity-fivem/OpenRune-Server | main |
| upstream (official) | https://github.com/OpenRune/OpenRune-Server | main |
| athoren (contributor mirror) | https://github.com/a-thoren/OpenRune-Server.git | — |

## HEADs (2026-11-05)

| Ref | Commit |
|---|---|
| our main / origin/main | `37ef4604e` |
| upstream/main | `20e9d7b47` (0 commits ahead of us) |

Divergence: 0 behind upstream, ~469 ahead (local content + merges).

## Integrated this session

| Change | Commit | Notes |
|---|---|---|
| upstream/main merge (6 PRs) | `05def00db` | Kraken #433, Motherlode gameval #446, shop qty buttons #438, atomic omnishop #431, save snapshots #410, cooking qty #426 |
| quests: implement Dragon Slayer | `03df52a8a` | prior uncommitted WIP, live-tested earlier session |
| regenerate gamevals (shop/cooking) | `9255c3558` | post-merge generated symbols |
| PR #453 Motherlode OSRS fixes | `37ef4604e` | merged `pr-453` head `54584d8ed`, author preserved |

## Open upstream PR classification (2026-11-05, 24 open)

| PR | Title | Status in our fork |
|---|---|---|
| 453 | Motherlode Mine fixes | INTEGRATED `37ef4604e` |
| 451 | Corsair Curse | present: `content/quest/.../rimmington/corsaircurse` |
| 447 | Produce sacks and baskets | missing (only plank-sack exists) |
| 440 | Farming allotments/flowers/herbs | missing (no farming skill module) |
| 439 | Archaeologist bosses | present: `crazy-archaeologist`, `deranged-archaeologist` |
| 427 | Hueycoatl | present: `content/bosses/world-bosses` Hueycoatl |
| 419 | Royal Titans | present: `content/bosses/royal-titans` |
| 418 | Celastrus fletching | partial: celastrus refs exist (reward pack, drops); verify fletching impl |
| 412 | Mad angel | present: `content/bosses/mad-angel` |
| 408 | Hunter skill | present: `content/skills/hunter` (upstream PR is draft) |
| 405 | Farming full-skill (WIP draft) | missing |
| 404 | Pest Control (WIP draft) | partial: `pestcontrol` in `content/areas/misc/lobbies` |
| 367 | Goblin Diplomacy (draft) | present: `content/quest/.../falador/goblindiplomacy` |
| 354 | Dwarf multicannon (draft) | partial: Nulodion sale + cannonballs; deployment missing |
| 342 | Tombs of Amascut | missing |
| 313 | Special attacks | present: `content/other/special-attacks` |
| 310 | Thieving | present: `content/skills/thieving` |
| 299 | Romeo & Juliet | present: `content/quest/.../varrock/romeojuliet` |
| 298 | Prince Ali Rescue | present: `content/quest/.../draynor/princealirescue` |
| 262 | Fletching | present: `content/skills/fletching` |
| 237 | Zulrah | present: `content/bosses/zulrah` |
| 228 | Construction | missing |
| 227 | Agility (draft) | present: `content/skills/agility` |
| 182 | Sailing (draft) | not applicable to rev 241 gameplay |

"present" = corresponding module/quest exists locally; deeper diff review
required to confirm the exact upstream implementation was taken vs an
independent implementation.

## Outstanding / next candidates

- #447 produce sacks/baskets — small, self-contained
- #418 celastrus fletching — verify against local fletching impl
- #354 multicannon deployment — larger; draft upstream
- #440/#405 farming — large skill; draft upstream, decide independent vs wait
- #342 ToA — very large raid; needs scoping
- #228 construction — very large; needs scoping

## Known gaps / deviations

- Nex, Vet'ion live-verification still pending (modules exist, untested in-game).
- Server boot smoke test skipped this session: dev server (pid 39408) already
  running on host ports; restarting unprompted would disrupt the environment.
