"""Generate huntMode/huntRange/attackRange/aggression-tolerance overlay data for
aggressive NPCs from OSRS Wiki infobox_monster bucket data.

Inputs:
  - wiki_aggressive_monsters.json (bucket('infobox_monster') aggressive rows)
  - tools/osrs-mcp/npc-gamevals.tsv (npc id -> npc.* symbol)
  - .data/raw-cache/map/npcs/*.toml + content/**/src/main/resources/**/*.toml ([[spawn]])
  - .data/raw-cache/map/area/wilderness*.toml (+ includes/excludes)
  - .data/raw-cache/server/npcs.toml (existing overlay, merged in place)

Output: merged npcs.toml + aggression-report.md
"""

import json
import re
import glob
import sys
import collections

REPO = "."
NPCS_TOML = ".data/raw-cache/server/npcs.toml"
GAMEVALS_TSV = "tools/osrs-mcp/npc-gamevals.tsv"
WIKI_JSON = "wiki_aggressive_monsters.json"

MELEE_HUNT_MODE = 1  # stalk.constant_melee - checkNotTooStrong=OutsideWilderness
RANGED_HUNT_MODE = 2  # stalk.constant_ranged - same check, ApPlayer2 approach
MELEE_HUNT_RANGE = 5
RANGED_HUNT_RANGE = 8
RANGED_ATTACK_RANGE = 10
MAX_RANGE = 15

RANGED_EXACT = {"magic", "dragonfire", "icy breath", "all"}
NEVER_TOLERANT_NAME_PARTS = [
    "dark beast",
    "warped terrorbird",
    "terrorbird warped",
    "desert bandit",
    "bandit",
    "vampyre",
    "vyre",
]

# ---------- gamevals: npc id -> [fullKeys] ----------
id_to_symbols = collections.defaultdict(list)
for line in open(GAMEVALS_TSV, encoding="utf-8"):
    parts = line.rstrip("\n").split("\t")
    if len(parts) != 2:
        continue
    npc_id, full_key = int(parts[0]), parts[1]
    id_to_symbols[npc_id].append(full_key)

# ---------- spawns: npc symbol -> [(plane, x, y)] ----------
spawn_re = re.compile(r'\[\[spawn\]\](.*?)(?=\[\[|\Z)', re.S)
npc_re = re.compile(r'npc\s*=\s*"(npc\.[^"]+)"')
coord_re = re.compile(r'coords\s*=\s*"(\d+)_(\d+)_(\d+)_(\d+)_(\d+)"')

spawns = collections.defaultdict(list)
spawn_files = glob.glob(".data/raw-cache/map/npcs/*.toml")
spawn_files += [
    f
    for f in glob.glob("content/**/src/main/resources/**/*.toml", recursive=True)
    if "\\build\\" not in f and "/build/" not in f
]
for f in spawn_files:
    try:
        text = open(f, encoding="utf-8", errors="replace").read()
    except OSError:
        continue
    for block in spawn_re.findall(text):
        nm = npc_re.search(block)
        if not nm:
            continue
        for cm in coord_re.finditer(block):
            plane, mx, my, lx, ly = map(int, cm.groups())
            spawns[nm.group(1)].append((plane, mx * 64 + lx, my * 64 + ly))

# ---------- wilderness polygons ----------
poly_re = re.compile(
    r"\[\[area\.polygons\]\]\s*vertices\s*=\s*\[((?:\s*\[\d+,\s*\d+\],?)*)\s*\]",
    re.S,
)
vertex_re = re.compile(r"\[(\d+),\s*(\d+)\]")


def load_polys(path):
    polys = []
    try:
        text = open(path, encoding="utf-8", errors="replace").read()
    except OSError:
        return polys
    for body in poly_re.findall(text):
        polys.append([(int(x), int(y)) for x, y in vertex_re.findall(body)])
    return polys


wild_files = [
    ".data/raw-cache/map/area/wilderness.toml",
    ".data/raw-cache/map/area/wilderness_dungeons.toml",
    ".data/raw-cache/map/area/minor_wilderness_bosses_dungeons.toml",
    ".data/raw-cache/map/area/demonic_ruins.toml",
    ".data/raw-cache/map/area/chaos_temple.toml",
]
wild_polys = [p for f in wild_files for p in load_polys(f)]
wild_exclude = load_polys(".data/raw-cache/map/area/ferox_enclave.toml")


def in_poly(x, y, poly):
    inside = False
    n = len(poly)
    j = n - 1
    for i in range(n):
        xi, yi = poly[i]
        xj, yj = poly[j]
        if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi) + xi:
            inside = not inside
        j = i
    return inside


def is_wilderness(x, y):
    if any(in_poly(x, y, p) for p in wild_exclude):
        return False
    return any(in_poly(x, y, p) for p in wild_polys)


# ---------- wiki rows ----------
rows = json.load(open(WIKI_JSON, encoding="utf-8"))

# symbol -> aggregate {ranged: bool, tolerant_blocked: bool, names: set, wild_only: bool}
apply = {}
conditional = []
unmapped = []

for row in rows:
    aggressive = (row.get("is_aggressive") or "").strip()
    name = row.get("name") or "?"
    ids = row.get("id") or []
    if isinstance(ids, str):
        ids = [ids]
    ids = [int(i) for i in ids if str(i).isdigit()]

    styles = row.get("attack_style") or []
    if isinstance(styles, str):
        styles = [styles]
    ranged = any(
        "ranged" in s.lower() or s.lower().strip() in RANGED_EXACT for s in styles
    )

    if aggressive.lower() != "yes":
        conditional.append((name, aggressive, ids))
        continue

    mapped = [s for i in ids for s in id_to_symbols.get(i, [])]
    if not mapped:
        unmapped.append((name, ids))
        continue

    never_tolerant = any(p in name.lower() for p in NEVER_TOLERANT_NAME_PARTS)
    for sym in mapped:
        entry = apply.setdefault(
            sym, {"ranged": False, "tolerant": True, "names": set()}
        )
        entry["ranged"] = entry["ranged"] or ranged
        entry["names"].add(name)
        if never_tolerant:
            entry["tolerant"] = False

# spawn-scope + wilderness-only tolerance filtering
applied = {}
skipped_no_spawn = []
skipped_huntmode = []

existing = open(NPCS_TOML, encoding="utf-8").read()
blocks = re.split(r"(?=\[\[npc\]\])", existing)
have_huntmode = set()
for b in blocks:
    if "huntMode" in b:
        m = re.search(r'id\s*=\s*"([^"\n]+)"', b)
        if m:
            have_huntmode.add(m.group(1))

for sym, info in apply.items():
    if sym in have_huntmode:
        skipped_huntmode.append(sym)
        continue
    coords = spawns.get(sym)
    if not coords:
        skipped_no_spawn.append((sym, sorted(info["names"])))
        continue
    if info["tolerant"] and all(
        is_wilderness(x, y) for _, x, y in coords
    ):
        info["tolerant"] = False
    applied[sym] = info

# ---------- merge into npcs.toml ----------
report_new = []
merged_existing = []


def fields_block(sym, info):
    lines = [
        "huntMode = %d" % (RANGED_HUNT_MODE if info["ranged"] else MELEE_HUNT_MODE),
        "huntRange = %d" % (RANGED_HUNT_RANGE if info["ranged"] else MELEE_HUNT_RANGE),
        "maxRange = %d" % MAX_RANGE,
    ]
    if info["ranged"]:
        lines.append("attackRange = %d" % RANGED_ATTACK_RANGE)
    return lines


existing_ids = set()
for b in blocks:
    m = re.search(r'id\s*=\s*"([^"\n]+)"', b)
    if m:
        existing_ids.add(m.group(1))

new_blocks_text = []
for sym, info in sorted(applied.items()):
    if sym in existing_ids:
        continue
    lines = ['[[npc]]', 'id = "%s"' % sym, 'inherit = "%s"' % sym]
    lines += fields_block(sym, info)
    if info["tolerant"]:
        lines.append("[npc.params]")
        lines.append('"param.aggression_tolerant"=1')
    new_blocks_text.append("\n".join(lines))
    report_new.append(sym)

# inject into existing blocks
out_parts = []
for b in blocks:
    m = re.search(r'id\s*=\s*"([^"\n]+)"', b)
    if not m or m.group(1) not in applied:
        out_parts.append(b)
        continue
    sym = m.group(1)
    info = applied[sym]
    merged_existing.append(sym)
    insert_lines = [
        f
        for f in fields_block(sym, info)
        if f.split(" = ")[0] not in b
    ]
    if insert_lines:
        im = re.search(r'inherit\s*=\s*"[^"\n]+"\n', b)
        pos = im.end() if im else re.search(r"id\s*=.*?\n", b).end()
        b = b[:pos] + "\n".join(insert_lines) + "\n" + b[pos:]
    if info["tolerant"]:
        if "[npc.params]" in b:
            b = b.replace(
                "[npc.params]",
                '[npc.params]\n"param.aggression_tolerant"=1',
                1,
            )
        else:
            b = b.rstrip("\n") + '\n[npc.params]\n"param.aggression_tolerant"=1\n'
    out_parts.append(b)

merged = "".join(out_parts).rstrip("\n") + "\n\n" + "\n\n".join(new_blocks_text) + "\n"
open(NPCS_TOML, "w", encoding="utf-8", newline="\n").write(merged)

# ---------- report ----------
with open("aggression-report.md", "w", encoding="utf-8") as r:
    r.write("# NPC aggression overlay generation\n\n")
    r.write(
        "- Applied huntMode to **%d** npc types (%d merged into existing blocks, %d new blocks)\n"
        % (len(applied), len(merged_existing), len(report_new))
    )
    r.write("- Skipped %d already having huntMode\n" % len(skipped_huntmode))
    r.write("- Skipped %d types with no static spawn\n" % len(skipped_no_spawn))
    r.write("- %d conditional-aggression wiki rows (NOT applied)\n" % len(conditional))
    r.write("- %d wiki rows with unmapped ids\n\n" % len(unmapped))
    r.write("## Conditional aggression (review)\n\n")
    for name, text, ids in sorted(conditional):
        r.write("- %s — `%s` (ids %s)\n" % (name, text, ids))
    r.write("\n## No static spawn (review for scripted aggro)\n\n")
    for sym, names in sorted(skipped_no_spawn):
        r.write("- %s (%s)\n" % (sym, ", ".join(names)))
    r.write("\n## Unmapped wiki ids\n\n")
    for name, ids in sorted(unmapped):
        r.write("- %s (ids %s)\n" % (name, ids))

print("applied:", len(applied))
print(" merged existing:", len(merged_existing))
print(" new blocks:", len(report_new))
print("skipped huntmode:", len(skipped_huntmode))
print("skipped no-spawn:", len(skipped_no_spawn))
print("conditional:", len(conditional))
print("unmapped:", len(unmapped))
ranged_count = sum(1 for i in applied.values() if i["ranged"])
tol_count = sum(1 for i in applied.values() if i["tolerant"])
print("ranged-mode:", ranged_count, " melee-mode:", len(applied) - ranged_count)
print("tolerant:", tol_count)
