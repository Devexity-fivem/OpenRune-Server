import json
import time
import urllib.parse
import urllib.request

BASE = "https://oldschool.runescape.wiki/api.php"
FIELDS = [
    "name",
    "id",
    "is_aggressive",
    "attack_style",
    "combat_level",
    "size",
    "is_members_only",
    "version_anchor",
]

rows = []
offset = 0
while True:
    fields = ",".join(repr(f) for f in FIELDS)
    query = (
        f"bucket('infobox_monster').select({fields})"
        f".where('is_aggressive','!=','No').limit(500).offset({offset}).run()"
    )
    url = BASE + "?" + urllib.parse.urlencode(
        {"action": "bucket", "format": "json", "query": query}
    )
    req = urllib.request.Request(url, headers={"User-Agent": "OpenRune-Dev/1.0"})
    with urllib.request.urlopen(req, timeout=60) as r:
        data = json.load(r)
    batch = data.get("bucket", [])
    print("offset", offset, "got", len(batch))
    if not batch:
        break
    rows.extend(batch)
    if len(batch) < 500:
        break
    offset += 500
    time.sleep(0.5)

with open("wiki_aggressive_monsters.json", "w") as f:
    json.dump(rows, f)
print("total", len(rows))
