#!/usr/bin/env python3
"""Build the committed period seed (`periodo-seed.json`) from a PeriodO data dump.

    ./build.py <p0d.json> <out.json>

PeriodO (https://perio.do) is a gazetteer of period DEFINITIONS: each one is a label, a time span and
a spatial scope as one published authority stated them, and the whole dataset is CC0. The seed is a
plain SUBSET of that dump, in the dump's own format, so the server reads the bundled file and a live
`histmersion periods fetch periodo` with the same parser — nothing here converts a single date:

  - `authorities` keeps only the authorities named below, and each of them only the periods in
    SELECTION and PRESETS — every label, span and scope is PeriodO's, untouched;
  - `histmersion.parents` places a definition under another of the SAME authority when the authority
    gives it no `broader` period inside the selection (the BSA's "Bronze Age Helladic" under its
    "Bronze Age"). A definition is never filed under another authority's period: "part of" is always
    a claim its own authority makes;
  - `histmersion.presets` is what the web timeline offers, each definition under the REGION heading
    it is listed in (a key of core `PresetRegion`). Presets are regional on purpose: the Bronze Age
    starts in 3600 BCE in the Levant and in 1800 BCE in Denmark, so there is no one "Bronze Age" to
    offer, only each region's own, as one authority dates it. Where PeriodO has no definition for a
    region (no "Bronze Age" for China or the Caucasus), the region simply has none;
  - `histmersion.version` is bumped whenever the curation changes, so a running server re-applies
    the seed once (an existing install's presets follow without a manual step).

Nothing here talks to the network or the database: `fetch.sh` downloads the dump and runs this.
"""
import json, sys

# Bump when SELECTION or PRESETS change: a server whose stored seed version is older re-seeds.
VERSION = 2

# (PeriodO period id, parent id or None). The parent must be a period of the SAME authority; None
# keeps the PeriodO `broader` link when it points into the selection, and makes the period a root.
SELECTION = [
    # ── Palaeolithic ─────────────────────────────────────────────────────────────── ROAD (2020)
    ("p0s2rwkrjbs", None),             # Paleolithic — Africa, Europe and Asia
    ("p0s2rwkp552", "p0s2rwkrjbs"),    # Lower Paleolithic — Europe
    ("p0s2rwkjh5q", "p0s2rwkrjbs"),    # Middle Paleolithic — Eurasia
    ("p0s2rwkwgnt", None),             #   Mousterian
    ("p0s2rwk499p", "p0s2rwkrjbs"),    # Upper Paleolithic — Eurasia
    ("p0s2rwkn8cc", None),             #   Aurignacian
    ("p0s2rwk5ndd", None),             #   Gravettian
    ("p0s2rwkp72n", None),             #   Solutrean
    ("p0s2rwkv3zf", None),             #   Magdalenian
    ("p0s2rwks8k2", None),             # Epipaleolithic — Eurasia (runs past ROAD's Paleolithic: not under it)
    ("p0s2rwkrm8q", None),             #   Kebaran
    ("p0s2rwksjfw", None),             #   Natufian
    ("p0s2rwkgzwv", "p0s2rwkrjbs"),    # Lower Paleolithic — South Asia
    ("p0s2rwkchqc", "p0s2rwkrjbs"),    # Middle Paleolithic — South Asia
    ("p0s2rwkj4cn", None),             # Upper Paleolithic — South Asia (runs past ROAD's Paleolithic: not under it)
    ("p0m64tdp282", None),             # Palaeolithic — Levant/Mesopotamia/Arabia (EAMENA)
    ("p0m64tdgpvd", None), ("p0m64tddwrm", None), ("p0m64tddxxc", None),
    ("p0ds9qjsdpq", None),             # Paleolithic — Greece (BSA)
    ("p0ds9qjxdv4", None), ("p0ds9qj79hw", None), ("p0ds9qjj6zz", None),
    # ── Mesolithic ───────────────────────────────────────────────────────────────────────────
    ("p0s2rwk5zjw", None),             # Mesolithic — Europe (ROAD)
    ("p0ds9qj2wwg", None),             # Mesolithic — Greece (BSA)
    ("p06v8w4fpkn", None),             # Mesolithic — Ukraine (FASTI)
    # ── Neolithic ────────────────────────────────────────────────────────────────────────────
    ("p0s2rwkkkxz", None),             # Neolithic — Eurasia (ROAD)
    ("p0m64tdbthk", None),             # Neolithic — Levant/Mesopotamia (EAMENA)
    ("p0m64tdnvht", None), ("p0m64td9nsf", None), ("p0m64tdsp73", None), ("p0m64td78gg", None),
    ("p0ds9qj3x2t", None),             # Neolithic — Greece (BSA)
    ("p0ds9qjwmfv", None), ("p0ds9qjrk5c", None), ("p0ds9qjqc49", None), ("p0ds9qj3fwb", None),
    ("p0qk76zw52w", None),             # Neolithic — Egypt (UCLA Encyclopedia of Egyptology)
    ("p06v8w44jbs", None),             # Neolithic — Ukraine (FASTI)
    # ── Chalcolithic / Eneolithic ────────────────────────────────────────────────────────────
    ("p0s2rwktsx4", None),             # Chalcolithic — Eurasia (ROAD)
    ("p0m64tdz9hp", None),             # Chalcolithic — Mesopotamia (EAMENA): Ubaid, Late Chalcolithic 1–5
    ("p0m64tdw4hx", None), ("p0m64tdh27m", None), ("p0m64tdswpg", None), ("p0m64tdvjrw", None),
    ("p0m64td23dg", None), ("p0m64tdczcn", None),
    ("p0m64td3bdh", None),             # Chalcolithic — Levant (EAMENA)
    ("p0m64tdrx6c", None), ("p0m64tdv2r9", None),
    ("p06v8w4ncv7", None),             # Eneolithic — Ukraine (FASTI)
    ("p06vx8jxh5x", None),             # Early Eneolithic — Romania (INP)
    ("p06vx8jsmqk", None),             # Mature Eneolithic — Romania (INP)
    ("p06vx8jczcj", None),             #   Cucuteni-Ariusd-Tripolie
    ("p0qk76zvrnc", None),             # Predynastic — Egypt (UCLA)
    # ── Bronze Age ───────────────────────────────────────────────────────────────────────────
    ("p0s2rwkwbsz", None),             # Bronze Age — Eurasia (ROAD)
    # the Aegean (BSA 2022): Early/Middle/Late Bronze Age, then Helladic, Cycladic, Minoan
    ("p0ds9qjq7b2", None),             # Bronze Age — Greece
    ("p0ds9qjkz7p", None), ("p0ds9qjkb6q", None), ("p0ds9qj8snx", None),
    ("p0ds9qj9pc2", "p0ds9qjq7b2"),    # Bronze Age Helladic (a BSA root of its own)
    ("p0ds9qj4tbn", None), ("p0ds9qj3t4n", None), ("p0ds9qjmkt4", None), ("p0ds9qjcztd", None),
    ("p0ds9qjztzb", None), ("p0ds9qjqhhk", None), ("p0ds9qjr2pw", None), ("p0ds9qj6kgg", None),
    ("p0ds9qjn6xc", None), ("p0ds9qjv9j7", None), ("p0ds9qjq6gx", None), ("p0ds9qjwd4n", None),
    ("p0ds9qjc3v7", None), ("p0ds9qj7xjv", None), ("p0ds9qj5m2x", None),
    ("p0ds9qjhmqs", None),             # Bronze Age Cycladic
    ("p0ds9qjd2xr", None), ("p0ds9qjv6nb", None), ("p0ds9qjp52w", None),
    ("p0ds9qj9kdj", None),             # Bronze Age Minoan
    ("p0ds9qjpvn5", None), ("p0ds9qjr3jz", None), ("p0ds9qjh3bn", None), ("p0ds9qjvfn5", None),
    ("p0ds9qjm95d", None), ("p0ds9qjfhcm", None), ("p0ds9qj3txg", None), ("p0ds9qjdc5k", None),
    ("p0ds9qjh4dh", None), ("p0ds9qj8jfc", None), ("p0ds9qjkvdd", None), ("p0ds9qj56qg", None),
    ("p0ds9qjb6nz", None), ("p0ds9qjs62t", None), ("p0ds9qjkkqz", None),
    ("p0ds9qj24b2", None), ("p0ds9qjrgzd", None), ("p0ds9qj6vmq", None), ("p0ds9qj49wh", None),
    # the Levant and Mesopotamia (EAMENA 2021)
    ("p0m64tdfpbq", None),             # Bronze Age — Levant
    ("p0m64tdm68m", None), ("p0m64tdzzwv", None), ("p0m64td29pd", None), ("p0m64tdg8fq", None),
    ("p0m64tddkc2", None), ("p0m64tdb3v4", None), ("p0m64tdftbr", None), ("p0m64tdbnfp", None),
    ("p0m64td2tp9", None),             # Bronze Age — Southern Mesopotamia
    ("p0m64tddbc7", None), ("p0m64tdzs88", None), ("p0m64td7xqg", None), ("p0m64tdfq8d", None),
    ("p0m64td7whf", None), ("p0m64tdkfpr", None), ("p0m64td859f", None), ("p0m64td282s", None),
    ("p0m64tdpvjc", None),
    ("p0m64tdcj4g", None),             # Bronze Age — Northern Mesopotamia
    ("p0m64td4dcx", None), ("p0m64td5t47", None), ("p0m64tdw8bh", None), ("p0m64tdn3h5", None),
    ("p0m64tdmdq2", None), ("p0m64tdcjqh", None),
    # Anatolia (Pleiades; the Hittite kingdoms from "Ancient cities", 2014)
    ("p03wskdnzgc", None),             # Early Bronze Age Anatolia
    ("p03wskd82ps", None),             # Middle Bronze Age Anatolia
    ("p03wskd2qsd", None),             # Late Bronze Age Anatolia
    ("p083p5r9zvn", None),             #   Old Hittite Kingdom
    ("p083p5r5nk9", None),             #   Middle Hittite Kingdom
    ("p083p5rmv3r", None),             #   Hittite Empire
    # the steppe's western edge: Ukraine (FASTI) and Romania (INP)
    ("p06v8w4hzqv", None),             # Early Bronze Age — Ukraine
    ("p06v8w4bk7n", None),             # Middle Bronze Age — Ukraine
    ("p06v8w4v2r4", None),             # Late Bronze Age — Ukraine
    ("p06v8w4jrfp", None),             # Final Bronze Age — Ukraine
    ("p06vx8j82tz", None),             # Bronze Age — Romania
    ("p06vx8jwkfm", None), ("p06vx8j6r99", None), ("p06vx8j3drv", None),
    ("p06vx8jw8kg", None),             #   Cucuteni-Ariusd-Tripolia (Early Bronze Age)
    ("p06vx8jcxfv", None),             #   Yamnaya (Middle Bronze Age)
    # Central and South Asia (British Museum; "Ancient cities")
    ("p08m57hq9gh", None),             # BMAC
    ("p08m57h54j8", None),             # Indus Civilisation
    ("p083p5rwkfs", None),             # Harappan, or Mature
    # ── Dynastic Egypt (EAMENA's span, the UCLA Encyclopedia of Egyptology's divisions) ──────
    ("p0m64tdwspc", None),
    ("p0qk76zkc72", None), ("p0qk76z92f9", None), ("p0qk76zqqr2", None),
    ("p0qk76zkb23", None), ("p0qk76zxgwt", None), ("p0qk76zvjkw", None),
    ("p0qk76zpmnv", None), ("p0qk76zh6n2", None),
    # ── Iron Age ─────────────────────────────────────────────────────────────────────────────
    ("p0s2rwkrbhq", None),             # Iron Age — Eurasia (ROAD)
    ("p0ds9qjrvwn", None),             # Early Iron Age — Greece (BSA)
    ("p0ds9qjpt9w", None), ("p0ds9qjwq59", None), ("p0ds9qjdx5q", None), ("p0ds9qjkzn8", None),
    ("p0m64td6gpk", None),             # Iron Age — Levant/Mesopotamia (EAMENA)
    ("p0m64tdrvzc", None), ("p0m64tddh9w", None), ("p0m64td54gz", None), ("p0m64td9rbc", None),
    ("p0m64td6qd2", None), ("p0m64tdt67n", None), ("p0m64td573h", None),
    ("p03wskdm4tb", None),             # Early Iron Age Anatolia (Pleiades)
    ("p03wskdv8qb", "p03wskdm4tb"),    #   Neo-Hittite Northern Levant
    ("p03wskdjrcs", None),             # Middle-Late Iron Age Anatolia
    ("p03wskd29mn", None),             # Urartian Eastern Anatolia
    ("p06v8w4t56d", None),             # Iron Age — Ukraine (FASTI)
    ("p06v8w4s5zg", "p06v8w4t56d"),    #   Cimmerian
    ("p06v8w4spfw", "p06v8w4t56d"),    #   Scythian
    ("p06v8w4pndh", "p06v8w4t56d"),    #   Sarmatian
    # ── South Asia after the Indus Civilisation ("Art history") ──────────────────────────────
    ("p0z5nvhtdz8", None),             # Vedic Period
    ("p0z5nvhpsq5", None),             # Maurya Period
    # ── Classical antiquity ──────────────────────────────────────────────────────────────────
    ("p0ds9qjpdcd", None),             # Antiquity — Greece (BSA)
    ("p0ds9qjwzmx", None), ("p0ds9qj2hxw", None), ("p0ds9qjmv48", None), ("p0ds9qjwwfx", None),
    ("p0ds9qjqppc", None),
    ("p0m64tdf9bv", None),             # Classical/Pre-Islamic — Levant/Mesopotamia/Iran (EAMENA)
    ("p0m64tds5x4", None), ("p0m64tdp4rp", None), ("p0m64tdj7x3", None), ("p0m64td6537", None),
    ("p0qk76zn9c9", None),             # Ptolemaic — Egypt (UCLA)
    ("p0qk76zbv62", None),             # Roman — Egypt (UCLA)
    # ── the Middle Ages ──────────────────────────────────────────────────────────────────────
    ("p06c6g32z39", None),             # Middle Ages, 500-1500 — Europe (Library of Congress)
    ("p0jrrjbbvc7", None),             # Early Middle Ages — Western Europe (EDH)
    ("p08m57hbmrr", None),             # Late Medieval — Europe (British Museum)
    ("p0ds9qjcznw", None),             # Byzantine — Greece (BSA)
    ("p0ds9qjd8b9", None), ("p0ds9qj4wcg", None), ("p0ds9qjh6s9", None),
    ("p0m64tdm947", None),             # Islamic — Iraq (EAMENA)
    ("p0m64tdm3d2", None), ("p0m64tdk66s", None), ("p0m64td3mq8", None),
]

# The timeline presets: region heading → PeriodO ids, one authority per age and region (no two
# authorities' "Bronze Age" for the same country). The heading is navigation; WHERE a definition
# applies is PeriodO's own spatial coverage, shown on every row. Left out, and why:
#   - ROAD's Eurasia-wide Neolithic, Chalcolithic, Bronze Age and Iron Age: one authority's umbrella
#     spans, not when those ages began anywhere in particular (they stay in the store, not presets);
#   - Pleiades' Anatolian Bronze Age (it dates the EARLY Bronze Age 2000-1750 BC): the Levantine
#     Ceramics Project's sequence for Turkey is used instead;
#   - the British Museum's "Indus Civilisation" beside "Harappan, or Mature" (a duplicate);
#   - EAMENA's "Dynastic Egypt" beside the UCLA Encyclopedia's divisions (a duplicate umbrella);
#   - China, South Asia, Central Asia: PeriodO defines no "Bronze Age" or "Iron Age" there, so they
#     get the cultures and dynasties it does define; the Caucasus has nothing usable at all.
PRESETS = {
    "aegean": [  # the British School at Athens (2022)
        "p0ds9qjsdpq", "p0ds9qj2wwg", "p0ds9qj3x2t", "p0ds9qjq7b2", "p0ds9qj9pc2", "p0ds9qjhmqs",
        "p0ds9qj9kdj", "p0ds9qjrvwn", "p0ds9qjwzmx", "p0ds9qj2hxw", "p0ds9qjmv48", "p0ds9qjwwfx",
        "p0ds9qjcznw"],
    "anatolia": [  # the Levantine Ceramics Project (2012) for Turkey; the Hittite kingdoms; Urartu
        "p0m63nj6xtw", "p0m63njqxz5", "p0m63njncbv", "p0m63njngrr", "p0m63njwrjx", "p0m63nj24nd",
        "p0m63njx89q", "p0m63nj3kjh", "p083p5r9zvn", "p083p5r5nk9", "p083p5rmv3r", "p03wskd29mn"],
    "levant-mesopotamia": [  # EAMENA (2021); ROAD's Levantine Epipalaeolithic cultures
        "p0s2rwkrm8q", "p0s2rwksjfw", "p0m64tdbthk", "p0m64td3bdh", "p0m64tdz9hp", "p0m64tdfpbq",
        "p0m64td2tp9", "p0m64tdcj4g", "p0m64td6gpk", "p0m64tdf9bv", "p0m64tdm947"],
    "iran": [  # EAMENA (2021): the Chalcolithic and Bronze Age by sub-region
        "p0m64tdp3pt", "p0m64tdhc9j", "p0m64tdqdhw", "p0m64tdttb7", "p0m64tdww6z", "p0m64tdht8m",
        "p0m64tds5vh", "p0m64tdsqhp", "p0m64tdq3k6", "p0m64tdbgsc", "p0m64tdq2q4"],
    "egypt": [  # the UCLA Encyclopedia of Egyptology
        "p0qk76zw52w", "p0qk76zvrnc", "p0qk76zkc72", "p0qk76z92f9", "p0qk76zqqr2", "p0qk76zkb23",
        "p0qk76zxgwt", "p0qk76zvjkw", "p0qk76zpmnv", "p0qk76zh6n2", "p0qk76zn9c9", "p0qk76zbv62"],
    "cyprus": [  # Cypriot antiquities text panels
        "p0jtbzwxgdq", "p0jtbzwsrd3", "p0jtbzwjsrv", "p0jtbzw9jwb"],
    "southeast-europe": [  # Romania (INP 2023), Bulgaria (ARIADNE 2015), Serbia (Cultural Monuments 2022)
        "p06vx8jdsqf", "p06vx8jczcj", "p06vx8jcxfv", "p06vx8j82tz", "p06vx8j3zzs", "p06vx8jcb7p",
        "p0qhb66r7js", "p0qhb66vp76", "p0qhb6659nf", "p0qhb6644wz",
        "p0ghrgfq4wb", "p0ghrgf3ffs", "p0ghrgf2rdm", "p0ghrgf3h4n"],
    "eastern-europe": [  # FASTI: Ukraine
        "p06v8w4fpkn", "p06v8w44jbs", "p06v8w4ncv7", "p06v8w4hzqv", "p06v8w4bk7n", "p06v8w4v2r4",
        "p06v8w4jrfp", "p06v8w4t56d", "p06v8w4s5zg", "p06v8w4spfw", "p06v8w4pndh"],
    "central-europe": [  # ARIADNE (2015): Germany/Switzerland/Czechia, Austria, Hungary, Poland
        "p0qhb66d5b9", "p0qhb66f8kc", "p0qhb66b3zn", "p0qhb66c6xc",
        "p0qhb66qvmt", "p0qhb66cb4k", "p0qhb66wrsn", "p0qhb66w478",
        "p0qhb667btm", "p0qhb66dv8v", "p0qhb66gpgs", "p0qhb66hvns",
        "p0qhb66sx95", "p0qhb665rjw", "p0qhb66fwwp"],
    "italy": [  # FASTI; the British Museum's Copper Age (FASTI gives none)
        "p06v8w4bm9m", "p08m57hskv9", "p06v8w4kqqb", "p06v8w4z9mm"],
    "iberia": [  # DB-HERITAGE: Portugal
        "p0cwscsjj9h", "p0cwscs4mwd", "p0cwscs3csg", "p0cwscswhdc"],
    "britain-ireland": [  # Historic England; ScAPA (Scotland); periods for the island of Ireland
        "p0kh9dsbz2g", "p0kh9dskb4p", "p0kh9ds7q8m", "p0kh9dsj33x",
        "p0xxt6tprqq", "p0xxt6tcq9w", "p0xxt6tfv6h", "p0xxt6tvdbz",
        "p06hps84bgf", "p06hps8fk76", "p06hps8vvgz", "p06hps8h2jd"],
    "scandinavia": [  # ARIADNE (2015): Denmark, Norway
        "p0qhb664686", "p0qhb66wrbs", "p0qhb66x7jw", "p0qhb66jmxk", "p0qhb66358p", "p0qhb667g25"],
    "central-asia": ["p08m57hq9gh"],  # the British Museum: BMAC
    "south-asia": [  # ROAD's South Asian Palaeolithic; Ancient cities (Harappan); Art history
        "p0s2rwkgzwv", "p0s2rwkchqc", "p0s2rwkj4cn", "p083p5rwkfs", "p0z5nvhtdz8", "p0z5nvhpsq5"],
    "china": [  # the British Museum
        "p08m57hfj2z", "p08m57hdt2t", "p08m57h9sf6", "p08m57hts6f", "p08m57hrc6v"],
    "broad": [  # continent-wide definitions: ROAD's Palaeolithic and Mesolithic; the Middle Ages
        "p0s2rwkp552", "p0s2rwkjh5q", "p0s2rwk499p", "p0s2rwks8k2", "p0s2rwk5zjw",
        "p06c6g32z39", "p0jrrjbbvc7", "p08m57hbmrr"],
}


def region_of(p):
    """PeriodO's own statement of where a definition applies (what the server reads as `region`)."""
    d = (p.get("spatialCoverageDescription") or "").strip()
    return d or ", ".join(c["label"].strip() for c in p.get("spatialCoverage", []) if c.get("label", "").strip())


def build(dump, out):
    authorities = json.load(open(dump))["authorities"]
    by_id = {pid: (aid, a, p) for aid, a in authorities.items() for pid, p in a.get("periods", {}).items()}
    preset_of = {pid: region for region, pids in PRESETS.items() for pid in pids}
    if sum(len(v) for v in PRESETS.values()) != len(preset_of):
        sys.exit("PRESETS lists a period twice")
    selected = dict(SELECTION)
    if len(selected) != len(SELECTION):
        sys.exit("SELECTION names a period twice")
    for pid in preset_of:
        selected.setdefault(pid, None)
    missing = [pid for pid in selected if pid not in by_id]
    if missing:
        sys.exit(f"not in this PeriodO dump: {', '.join(missing)}")
    unlocated = [pid for pid in preset_of if not region_of(by_id[pid][2])]
    if unlocated:
        sys.exit(f"a preset must say where it applies; PeriodO gives no coverage for: {', '.join(unlocated)}")
    seed, parents = {}, {}
    for pid, parent in selected.items():
        aid, a, p = by_id[pid]
        auth = seed.setdefault(aid, {k: v for k, v in a.items() if k != "periods"} | {"periods": {}})
        auth["periods"][pid] = p
        if parent is not None:
            if parent not in selected:
                sys.exit(f"{pid}: parent {parent} is not in the selection")
            if by_id[parent][0] != aid:
                sys.exit(f"{pid}: parent {parent} belongs to another authority")
            parents[pid] = parent
    doc = {
        "source": "PeriodO (https://perio.do), CC0 1.0 — a subset made by db/periods/build.py",
        "authorities": seed,
        "histmersion": {"version": VERSION, "parents": parents, "presets": preset_of},
    }
    with open(out, "w") as f:
        json.dump(doc, f, ensure_ascii=False, indent=1, sort_keys=True)
        f.write("\n")
    print(f"{len(selected)} periods from {len(seed)} authorities; {len(preset_of)} presets in "
          f"{len(PRESETS)} regions: " + ", ".join(f"{r} {len(ps)}" for r, ps in PRESETS.items()))


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    build(sys.argv[1], sys.argv[2])
