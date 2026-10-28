import re, os, sys, glob, urllib.request
from collections import defaultdict

UA = {'User-Agent': 'OpenRuneAudit/1.0 (drop table verification)'}
TABLES_DIR = 'content/drops/src/main/resources/drops/tables/monsters'

# ---------- gameval binary parse (u16 length + name=id, sections \x00\xNN<table>) ----------
def parse_gamevals(path):
    data = open(path, 'rb').read()
    sections = {}
    nsec = int.from_bytes(data[:4], 'big')
    i = 4
    for _ in range(nsec):
        ln = data[i + 1]
        name = data[i + 2:i + 2 + ln].decode('ascii', 'replace')
        cnt = int.from_bytes(data[i + 2 + ln:i + 6 + ln], 'big')
        j = i + 6 + ln
        entries = {}
        for _ in range(cnt):
            eln = data[j + 1]
            e = data[j + 2:j + 2 + eln].decode('ascii', 'replace')
            k, _, v = e.partition('=')
            if v.isdigit(): entries[k] = int(v)
            j += 2 + eln
        sections[name] = entries
        i = j
    return sections

# ---------- dump.obj display-name -> internal ----------
def norm(s):
    return re.sub(r'[^a-z0-9]', '', s.lower())

def build_name_lookup(obj_syms):
    """normalized display-ish name -> internal obj name; also GE mapping API."""
    m = {}
    for sym in obj_syms:
        m.setdefault(norm(sym.replace('_', ' ')), sym)
    try:
        url = 'https://prices.runescape.wiki/api/v1/osrs/mapping'
        data = urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=20).read()
        import json
        id2sym = {i: s for s, i in obj_syms.items()}
        for e in json.loads(data):
            if e['id'] in id2sym:
                m[norm(e['name'])] = id2sym[e['id']]
    except Exception:
        pass
    return m

def fetch(page):
    url = 'https://oldschool.runescape.wiki/w/' + urllib.parse.quote(page) + '?action=raw'
    return urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=20).read().decode('utf-8')

# ---------- wiki parse ----------
def parse_infobox(wt):
    ids = {int(n or '1'): [int(x) for x in v.replace(' ', '').split(',') if x.strip().isdigit()]
           for n, v in re.findall(r'(?im)\|id(\d*)\s*=\s*([0-9,\s]+)', wt)}
    def clean(v):
        return re.sub(r'[{}]', '', v).strip()
    vers = {int(n or '1'): clean(v) for n, v in re.findall(r'(?im)\|dropversion(\d*)\s*=\s*(.+)$', wt)}
    vnames = {int(n or '1'): clean(v) for n, v in re.findall(r'(?im)\|version(\d*)\s*=\s*(.+)$', wt)}
    return ids, vers, vnames

def section_key(heading):
    h = heading.strip().lower()
    if '100%' in h or 'always' in h: return 'guaranteed'
    if 'tertiary' in h: return 'tertiary'
    return 'main'

def tpl_params(tpl):
    parts, depth, cur = [], 0, ''
    for ch in tpl:
        if ch == '{': depth += 1
        elif ch == '}': depth -= 1
        if ch == '|' and depth <= 2:
            parts.append(cur); cur = ''
        else:
            cur += ch
    parts.append(cur)
    out = {}
    for part in parts[1:]:
        if '=' in part:
            k, v = part.split('=', 1)
            out[k.strip().lower()] = v.strip()
    return out

def parse_dropslines(body):
    """returns list of (section, dict)"""
    # split on ===headings=== keeping section labels
    parts = re.split(r'(?m)^={3,4}\s*(.+?)\s*={3,4}\s*$', body)
    out = []
    sec = 'main'
    seg = parts[0]
    chunks = [(sec, seg)]
    for i in range(1, len(parts), 2):
        chunks.append((section_key(parts[i]), parts[i + 1] if i + 1 < len(parts) else ''))
    for skey, segbody in chunks:
        # split into dropversion-scoped segments via DropsTableHead markers
        heads = list(re.finditer(r'\{\{\s*DropsTableHead\s*\|[^}]*?dropversion\s*=\s*([^}|]+)', segbody))
        segments = []
        if heads:
            pos = 0
            if heads[0].start() > 0:
                segments.append(('', segbody[:heads[0].start()]))
            for i, h in enumerate(heads):
                end = heads[i + 1].start() if i + 1 < len(heads) else len(segbody)
                segments.append((h.group(1).strip(), segbody[h.start():end]))
                pos = end
        else:
            segments.append(('', segbody))
        for dv, seg2 in segments:
            for m in re.finditer(r'\{\{\s*DropsLine\b', seg2):
                depth, j = 0, m.start()
                while j < len(seg2) - 1:
                    if seg2[j:j + 2] == '{{': depth += 1; j += 2; continue
                    if seg2[j:j + 2] == '}}':
                        depth -= 1; j += 2
                        if depth == 0: break
                        continue
                    j += 1
                tpl = seg2[m.start():j]
                p = tpl_params(tpl[:-2])
                p['_sec'] = skey
                p['_dv'] = dv or p.get('dropversion', '')
                out.append(p)
            # shared subtable templates
            for m in re.finditer(r'\{\{\s*HerbDropLines\s*\|\s*([\d/]+)\s*[^}]*\}\}', seg2):
                out.append({'_sec': skey, '_shared': 'herb', 'rarity': m.group(1), '_dv': dv})
            for m in re.finditer(r'\{\{\s*(?:Main|All)RareDropTable\w*\s*\|\s*([\d/]+)', seg2):
                out.append({'_sec': skey, '_shared': 'rareDrop', 'rarity': m.group(1), '_dv': dv})
            for m in re.finditer(r'\{\{\s*GemDropTable\w*\s*\|\s*([\d/]+)', seg2):
                out.append({'_sec': skey, '_shared': 'gem', 'rarity': m.group(1), '_dv': dv})
            for m in re.finditer(r'\{\{\s*(?:Allotment|AllotmentSeed|RareAllotment|General|Herb)?SeedDropTable\w*\s*\|\s*([\d/]+)', seg2):
                out.append({'_sec': skey, '_shared': 'seed', 'rarity': m.group(1), '_dv': dv})
            for m in re.finditer(r'\{\{\s*RareSeedDropTable\w*\s*\|\s*([\d/]+)', seg2):
                out.append({'_sec': skey, '_shared': 'rareSeed', 'rarity': m.group(1), '_dv': dv})
            for m in re.finditer(r'\{\{\s*UsefulHerbDropTable\w*\s*\|\s*([\d/]+)', seg2):
                out.append({'_sec': skey, '_shared': 'usefulHerb', 'rarity': m.group(1), '_dv': dv})
            for m in re.finditer(r'\{\{\s*Mega\w*RareDropTable\w*\s*\|\s*([\d/]+)', seg2):
                out.append({'_sec': skey, '_shared': 'megaRare', 'rarity': m.group(1), '_dv': dv})
            for m in re.finditer(r'\{\{\s*RareDropTable\w*\s*\|\s*([\d/]+)', seg2):
                out.append({'_sec': skey, '_shared': 'rareDrop', 'rarity': m.group(1), '_dv': dv})
    return out

def qty(q):
    q = (q or '1').replace('(noted)', '').replace('(un-noted)', '').strip()
    q = re.sub(r'\s*\(.*?\)', '', q).strip()
    if re.fullmatch(r'\d+', q): return q
    m = re.match(r'^(\d+)\s*(?:-|–|to)\s*(\d+)$', q)
    if m: return f'"{m.group(1)}..{m.group(2)}"'
    if re.fullmatch(r'[\d;, ]+', q) and (';' in q or ',' in q):
        return '"' + ';'.join(p.strip() for p in re.split(r'[;,]', q) if p.strip()) + '"'
    m = re.match(r'^(\d+)\s*\(noted\)', q)
    if m: return m.group(1)
    return None  # unparseable

def rarity_frac(r):
    r = (r or '').strip()
    m = re.match(r'^(\d+)\s*/\s*(\d+)$', r)
    if m: return int(m.group(1)), int(m.group(2))
    return None

def emit_toml(page_display, variant, npc_syms, drops, flags):
    lines = []
    lines.append(f'id = "{page_display}{" " + variant if variant else ""} Drops"')
    lines.append('npcs = [' + ', '.join(f'"{n}"' for n in sorted(npc_syms)) + ']')
    if any((d.get('name') or '').lower() == 'brimstone key' for d in drops):
        lines.append('brimstone_key_roll = true')
    lines.append('')
    guaranteed = [d for d in drops if d['_sec'] == 'guaranteed' and '_skip' not in d]
    tertiary = [d for d in drops if d['_sec'] == 'tertiary' and '_skip' not in d]
    main = [d for d in drops if d['_sec'] == 'main' and '_skip' not in d]
    for d in guaranteed:
        obj = d.get('_obj')
        if not obj: continue
        c = qty(d.get('quantity'))
        if c is None: continue
        lines.append('[[guaranteed]]')
        lines.append(f'obj = "{obj}"')
        lines.append(f'count = {c}')
        lines.append('')
    fracs = [rarity_frac(d.get('rarity')) for d in main]
    denoms = {f[1] for f in fracs if f}
    total = 1
    for dn in denoms:
        total = total * dn // __import__('math').gcd(total, dn)
    if main:
        lines.append('[main]')
        lines.append(f'total = {total}')
        lines.append(f'name = "{page_display}{" " + variant if variant else ""} Drops"')
        lines.append('')
        wsum = 0
        for d, f in zip(main, fracs):
            if not f:
                flags.setdefault('unknown', []).append(d.get('name'))
                continue
            w = f[0] * (total // f[1])
            if '_shared' in d:
                wsum += w
                lines.append('[[main.entries]]')
                lines.append(f'weight = {w}')
                lines.append(f'shared = "{d["_shared"]}"')
                lines.append('')
                continue
            obj = d.get('_obj')
            c = qty(d.get('quantity'))
            nm_lower = (d.get('name') or '').lower()
            if nm_lower == 'nothing' or d.get('_nothing'):
                wsum += w
                lines.append('[[main.entries]]')
                lines.append(f'weight = {w}')
                lines.append('nothing = true')
                lines.append('')
                continue
            if not obj or c is None:
                flags.setdefault('unresolved', []).append(d.get('name'))
                continue
            wsum += w
            lines.append('[[main.entries]]')
            lines.append(f'weight = {w}')
            lines.append(f'obj = "{obj}"')
            lines.append(f'count = {c}')
            if d.get('_clue'):
                lines.append('clue_scroll_box = true')
            if obj == 'obj.looting_bag':
                lines.append('should_drop_looting_bag = true')
            lines.append('')
        if wsum < total:
            lines.append(f'# Main table pool padding uses a nothing roll ({total - wsum} weight)')
            lines.append('[[main.entries]]')
            lines.append(f'weight = {total - wsum}')
            lines.append('nothing = true')
            lines.append('')
        elif wsum > total:
            # rebuild header with normalized total to preserve relative probabilities
            for i, l in enumerate(lines):
                if l.startswith('total = '):
                    lines[i] = f'total = {wsum}'
                    break
    for d in tertiary:
        f = rarity_frac(d.get('rarity'))
        obj = d.get('_obj')
        c = qty(d.get('quantity'))
        if not f or not obj or c is None:
            if f and (not obj):
                flags.setdefault('unresolved', []).append(d.get('name'))
            continue
        lines.append('[[tertiary]]')
        lines.append(f'numerator = {f[0]}')
        lines.append(f'denominator = {f[1]}')
        lines.append(f'obj = "{obj}"')
        lines.append(f'count = {c}')
        if d.get('_clue'):
            lines.append('clue_scroll_box = true')
        if obj == 'obj.looting_bag':
            lines.append('should_drop_looting_bag = true')
        lines.append('')
    return '\n'.join(lines)

def find_sections(wt):
    """==X drops== / ==Drops== / ==Drop table N== → [(label, body)]"""
    pat = re.compile(r'(?m)^==\s*([^\n=]*?\bdrops?\b[^\n=]*?|Drop table \d+|Drops)\s*==\s*$', re.I)
    ms = list(pat.finditer(wt))
    out = []
    for i, m in enumerate(ms):
        if i + 1 < len(ms):
            end = ms[i + 1].start()
        else:
            nxt = re.search(r'(?m)^==[^=].*==', wt[m.end():])
            end = m.end() + nxt.start() if nxt else len(wt)
        out.append((m.group(1).strip(), wt[m.end():end]))
    return out

def variant_ids(label, vers_to_ids, vername_to_ids=None):
    """map section label → npc ids via dropversion names, then version names"""
    lbl = re.sub(r'(?i)^drops?\s*', '', re.sub(r'(?i)\s+drops?\s*$', '', label)).strip()
    lbl = lbl.strip('()').strip()
    toks = [t.strip() for t in re.split(r'(?i)\s+and\s+|,', lbl) if t.strip()]
    def tok_match(t, name):
        t, name = t.lower(), name.lower()
        if t == name: return True
        if t.isdigit():
            return name == 'level ' + t or name == t
        if len(t) >= 3 and (re.search(r'\b' + re.escape(t) + r'\b', name) or
                            re.search(r'\b' + re.escape(name) + r'\b', t)):
            return True
        return False
    hit = []
    if vername_to_ids:
        for name, ids in vername_to_ids.items():
            for t in toks:
                if tok_match(t, name):
                    hit += ids; break
    if not hit:
        for name, ids in vers_to_ids.items():
            for t in toks:
                if tok_match(t, name):
                    hit += ids; break
    return set(hit)

def run(pages):
    gv = parse_gamevals('.data/gamevals-binary/gamevals.dat')
    npc_ids = gv.get('npc', {})
    obj_ids = gv.get('obj', {})
    name2obj = build_name_lookup(obj_ids)
    id2sym = {}
    for sym, i in npc_ids.items(): id2sym.setdefault(i, sym)

    # spawned-but-unbound attackable set
    spawned = set()
    for f in glob.glob('.data/raw-cache/map/npcs/*.toml'):
        spawned.update(re.findall(r'npc\s*=\s*"(npc\.[a-z0-9_]+)"', open(f, encoding='utf8', errors='replace').read()))
    bound = set()
    for f in glob.glob(TABLES_DIR + '/*.toml'):
        if os.path.basename(f).startswith('_'): continue
        for n in re.findall(r'npcs\s*=\s*\[([^\]]*)\]', open(f, encoding='utf8', errors='replace').read()):
            bound.update(re.findall(r'"(npc\.[a-z0-9_]+)"', n))
    for f in glob.glob('content/drops/src/main/kotlin/**/*.kt', recursive=True):
        for m in re.finditer(r'npcs\s*\(\s*((?:\s*"npc\.[a-z0-9_]+"\s*,?)+)\s*\)', open(f, encoding='utf8', errors='replace').read()):
            bound.update(re.findall(r'"(npc\.[a-z0-9_]+)"', m.group(1)))
    unbound = {s: npc_ids[s.split('.', 1)[1]] for s in spawned - bound if s.split('.', 1)[1] in npc_ids}

    report = []
    for page in pages:
        try: wt = fetch(page)
        except Exception as e:
            report.append(f'{page}: FETCH FAIL {e}'); continue
        ids, vers, vnames = parse_infobox(wt)
        if not ids: report.append(f'{page}: no infobox ids'); continue
        vers_to_ids = {}
        for n, v in vers.items():
            for nm in [x.strip() for x in v.split(',')]:
                vers_to_ids.setdefault(nm, []).extend(ids.get(n, []))
        vername_to_ids = {v: ids.get(n, []) for n, v in vnames.items()}
        sections = find_sections(wt)
        if not sections:
            report.append(f'{page}: NO DROP SECTIONS'); continue
        claimed = set()
        pending = []
        for label, body in sections:
            if label.lower() == 'drops':
                # try variant split on === headings matching dropversions
                subs = re.split(r'(?m)^={3,4}\s*(.+?)\s*={3,4}\s*$', body)
                varhits = [s for i, s in enumerate(subs[1::2]) if variant_ids(s, vers_to_ids, vername_to_ids)]
                if len(varhits) >= 2:
                    for i in range(1, len(subs), 2):
                        ids_hit = variant_ids(subs[i], vers_to_ids, vername_to_ids)
                        if ids_hit:
                            pending.append((subs[i], subs[i + 1] if i + 1 < len(subs) else '', ids_hit))
                            claimed |= ids_hit
                    continue
                pending.append(('', body, None))
            else:
                ids_hit = variant_ids(label, vers_to_ids, vername_to_ids)
                if ids_hit:
                    pending.append((re.sub(r'(?i)\s+drops?\s*$', '', label).strip(), body, ids_hit))
                    claimed |= ids_hit
                else:
                    stripped = re.sub(r'(?i)\s+drops?\s*$', '', label).strip('() ') or 'standard'
                    pending.append((stripped, body, None))
        rest = set(i for grp in ids.values() for i in grp) - claimed
        outfiles = []
        seen_names = set()
        seen_sigs = {}
        merged = []
        for label, body, idhit in pending:
            drops_pre = parse_dropslines(body)
            sig = tuple(sorted((d.get('name',''), d.get('rarity',''), d.get('quantity',''), d['_sec']) for d in drops_pre))
            if sig in seen_sigs and idhit:
                seen_sigs[sig].update(idhit)
                continue
            seen_sigs[sig] = idhit if idhit else set()
            merged.append((label, body, sig))
        merged.sort(key=lambda t: 0 if 'member' in t[0].lower() and 'free' not in t[0].lower() else 1)
        CLUE_OBJ = {
            'beginner': 'trail_clue_beginner',
            'easy': 'trail_clue_easy_simple001',
            'medium': 'trail_clue_medium_sextant001',
            'hard': 'trail_clue_hard_map001',
            'elite': 'trail_elite_emote_exp1',
        }
        emit_groups = []  # [(label, udrops, use)]
        for label, body, sig in merged:
            idhit = seen_sigs[sig] or None
            use = idhit if idhit else (rest or (set() if claimed else set(i for grp in ids.values() for i in grp)))
            if not idhit:
                if not use and claimed:
                    outfiles.append((f'(skipped {page} {label}: no unclaimed ids)', 0, 0, [], []))
                    continue
                rest = set()  # consumed by first unmatched section
            claimed |= set(use)
            drops = parse_dropslines(body)
            # split into dropversion groups; common (_dv='') lines go to every group
            dvgroups = {}
            for d in drops:
                dvgroups.setdefault((d.get('_dv') or '').strip(), []).append(d)
            common = dvgroups.pop('', [])
            groups = {dv: common + ds for dv, ds in dvgroups.items()} or {'': drops}
            unres, unkn = [], []
            for d in drops:
                nm = d.get('name', '')
                if '_shared' in d: continue
                clue_m = re.match(r'(?i)clue scroll \((\w+)\)', nm)
                casket_m = re.match(r'(?i)reward casket', nm)
                if casket_m:
                    d['_skip'] = True
                    continue
                if clue_m:
                    co = CLUE_OBJ.get(clue_m.group(1).lower())
                    if co and co in obj_ids:
                        d['_obj'] = 'obj.' + co
                        d['_clue'] = True
                    else:
                        unres.append(nm)
                    continue
                key = norm(nm)
                obj = name2obj.get(key)
                if not obj and key.endswith('s'): obj = name2obj.get(key[:-1])
                ALIAS = {
                    'left skull half': 'sos_half_skull1',
                    'right skull half': 'sos_half_skull2',
                    'rat bone': 'rag_rat_bone',
                    'giant rat bone': 'rag_giant_rat_bone',
                    'zombie bone': 'rag_zombie_bone',
                    'long bone': 'dorgesh_construction_bone',
                    'curved bone': 'dorgesh_construction_bone_curved',
                    'terrorbird wing': 'terrorbird_wing',
                    'looting bag': 'looting_bag',
                    'brimstone key': '_BRIMSTONE_',
                    'tokkul': '_CURRENCY_',
                }
                if not obj:
                    obj = ALIAS.get(nm.lower())
                    if obj in ('_BRIMSTONE_', '_CURRENCY_'):
                        flags = flags if False else None  # handled below
                        d['_skip'] = obj; obj = None
                if not obj and nm.lower().endswith(' champion scroll'):
                    base = norm(nm[:-16])
                    obj = 'champions_challenge_' + base if 'champions_challenge_' + base in obj_ids else None
                if obj: d['_obj'] = 'obj.' + obj
                elif nm and nm.lower() != 'nothing': unres.append(nm)
                if not rarity_frac(d.get('rarity')) and (d.get('rarity') or '').lower() != 'always':
                    unkn.append(f"{nm}={d.get('rarity')}")
            syms = sorted('npc.' + id2sym[i] for i in use if i in id2sym and 'npc.' + id2sym[i] in unbound)
            allsyms = sorted('npc.' + id2sym[i] for i in use if i in id2sym)
            if not syms:
                outfiles.append((None, 0, len(allsyms), unres, unkn))
                continue
            # union of all dropversion groups, deduped — matches generator convention
            # (e.g. 'Standard and Catacombs of Kourend' merged into one table)
            seen_line = set()
            seen_nq = set()
            udrops = []
            for dv, gdrops in groups.items():
                for d in gdrops:
                    k = (d.get('name', ''), d.get('rarity', ''), d.get('quantity', ''), d['_sec'])
                    nq = (d.get('name', ''), d.get('quantity', ''), d['_sec'])
                    if k in seen_line or nq in seen_nq: continue
                    seen_line.add(k)
                    seen_nq.add(nq)
                    udrops.append(d)
            emit_groups.append((label, udrops, set(use), unres, unkn))
        # union-find merge: sections sharing npc symbols must become one table
        # (engine rejects duplicate npc bindings; e.g. Wilderness Slayer Cave
        # variants reuse the same npc ids as the base table)
        parent = list(range(len(emit_groups)))
        def find(x):
            while parent[x] != x:
                parent[x] = parent[parent[x]]
                x = parent[x]
            return x
        sym2grp = {}
        for gi, (label, udrops, use, unres, unkn) in enumerate(emit_groups):
            for i in use:
                if i not in id2sym: continue
                s = 'npc.' + id2sym[i]
                if s in sym2grp:
                    parent[find(gi)] = find(sym2grp[s])
                else:
                    sym2grp[s] = gi
        unions = {}
        for gi in range(len(emit_groups)):
            unions.setdefault(find(gi), []).append(gi)
        for root, gidx in unions.items():
            label = emit_groups[gidx[0]][0]
            udrops = []
            use = set()
            unres, unkn = [], []
            seen_line = set()
            seen_nq = set()
            for gi in gidx:
                use |= emit_groups[gi][2]
                unres += emit_groups[gi][3]
                unkn += emit_groups[gi][4]
                for d in emit_groups[gi][1]:
                    k = (d.get('name', ''), d.get('rarity', ''), d.get('quantity', ''), d['_sec'])
                    nq = (d.get('name', ''), d.get('quantity', ''), d['_sec'])
                    if k in seen_line or nq in seen_nq: continue
                    seen_line.add(k)
                    seen_nq.add(nq)
                    udrops.append(d)
            syms = sorted('npc.' + id2sym[i] for i in use if i in id2sym and 'npc.' + id2sym[i] in unbound)
            allsyms = sorted('npc.' + id2sym[i] for i in use if i in id2sym)
            if not syms:
                outfiles.append((None, 0, len(allsyms), unres, unkn))
                continue
            flags = {'unknown': unkn, 'unresolved': unres}
            toml = emit_toml(page.replace('_', ' '), label, syms, udrops, flags)
            base = re.sub(r'[^a-z0-9]+', '_', (page + '_' + label).lower()).strip('_')
            if base in seen_names:
                base += '_2'
            seen_names.add(base)
            fname = base + '.toml'
            open(os.path.join(TABLES_DIR, fname), 'w').write(toml)
            outfiles.append((fname, len(syms), len(allsyms), unres, unkn))
        for o in outfiles:
            report.append(f'{page}: {o[0]} binds {o[1]}/{o[2]} unbound/all syms, unresolved={o[3][:6]}{"..." if len(o[3])>6 else ""} unknownrarity={o[4][:4]}')
    print('\n'.join(report))

if __name__ == '__main__':
    run(sys.argv[1:] or ['Lesser_demon'])
