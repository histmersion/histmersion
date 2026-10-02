#!/usr/bin/env python3
"""Build the committed period-preset seed (`periodo-seed.json`) from a PeriodO data dump.

    ./build.py <p0d.json> <out.json>

PeriodO (https://perio.do) is a gazetteer of period DEFINITIONS: each one is a label, a time span and
a spatial scope as one published authority stated them, and the whole dataset is CC0. The seed is a
plain SUBSET of that dump, in the dump's own format, so the server reads the bundled file and a live
`histmersion periods fetch periodo` with the same parser — nothing here converts a single date:

  - `authorities` keeps only the authorities named below, and each of them only the periods in
    SELECTION — every label, span and scope is PeriodO's, untouched;
  - `histmersion.parents` is the one curated thing: where a definition sits in the preset tree when
    its authority gives it no `broader` period inside the selection (the BSA's "Bronze Age" for
    Greece goes under ROAD's Eurasian "Bronze Age", say). A PeriodO `broader` link that stays inside
    the selection is kept as it is.

The periods with no parent are the timeline's presets, so SELECTION's roots are kept few and broad.
Nothing here talks to the network or the database: `fetch.sh` downloads the dump and runs this.
"""
import json, sys

# (PeriodO period id, parent id or None). None keeps the PeriodO `broader` link when it points into
# the selection, and makes the period a ROOT (a timeline preset) otherwise.
ROAD, BSA, EAMENA, UCLA, PLEIADES = "p0s2rwk", "p0ds9qj", "p0m64td", "p0qk76z", "p03wskd"
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
    ("p0s2rwks8k2", "p0s2rwkrjbs"),    # Epipaleolithic — Eurasia
    ("p0s2rwkrm8q", None),             #   Kebaran
    ("p0s2rwksjfw", None),             #   Natufian
    ("p0s2rwkgzwv", "p0s2rwkrjbs"),    # Lower Paleolithic — South Asia
    ("p0s2rwkchqc", "p0s2rwkrjbs"),    # Middle Paleolithic — South Asia
    ("p0s2rwkj4cn", "p0s2rwkrjbs"),    # Upper Paleolithic — South Asia
    ("p0m64tdp282", "p0s2rwkrjbs"),    # Palaeolithic — Levant/Mesopotamia/Arabia (EAMENA)
    ("p0m64tdgpvd", None), ("p0m64tddwrm", None), ("p0m64tddxxc", None),
    ("p0ds9qjsdpq", "p0s2rwkrjbs"),    # Paleolithic — Greece (BSA)
    ("p0ds9qjxdv4", None), ("p0ds9qj79hw", None), ("p0ds9qjj6zz", None),
    # ── Mesolithic ───────────────────────────────────────────────────────────────────────────
    ("p0s2rwk5zjw", None),             # Mesolithic — Europe (ROAD)
    ("p0ds9qj2wwg", "p0s2rwk5zjw"),    # Mesolithic — Greece (BSA)
    ("p06v8w4fpkn", "p0s2rwk5zjw"),    # Mesolithic — Ukraine (FASTI)
    # ── Neolithic ────────────────────────────────────────────────────────────────────────────
    ("p0s2rwkkkxz", None),             # Neolithic — Eurasia (ROAD)
    ("p0m64tdbthk", "p0s2rwkkkxz"),    # Neolithic — Levant/Mesopotamia (EAMENA)
    ("p0m64tdnvht", None), ("p0m64td9nsf", None), ("p0m64tdsp73", None), ("p0m64td78gg", None),
    ("p0ds9qj3x2t", "p0s2rwkkkxz"),    # Neolithic — Greece (BSA)
    ("p0ds9qjwmfv", None), ("p0ds9qjrk5c", None), ("p0ds9qjqc49", None), ("p0ds9qj3fwb", None),
    ("p0qk76zw52w", "p0s2rwkkkxz"),    # Neolithic — Egypt (UCLA Encyclopedia of Egyptology)
    ("p06v8w44jbs", "p0s2rwkkkxz"),    # Neolithic — Ukraine (FASTI)
    # ── Chalcolithic / Eneolithic ────────────────────────────────────────────────────────────
    ("p0s2rwktsx4", None),             # Chalcolithic — Eurasia (ROAD)
    ("p0m64tdz9hp", "p0s2rwktsx4"),    # Chalcolithic — Mesopotamia (EAMENA): Ubaid, Late Chalcolithic 1–5
    ("p0m64tdw4hx", None), ("p0m64tdh27m", None), ("p0m64tdswpg", None), ("p0m64tdvjrw", None),
    ("p0m64td23dg", None), ("p0m64tdczcn", None),
    ("p0m64td3bdh", "p0s2rwktsx4"),    # Chalcolithic — Levant (EAMENA)
    ("p0m64tdrx6c", None), ("p0m64tdv2r9", None),
    ("p06v8w4ncv7", "p0s2rwktsx4"),    # Eneolithic — Ukraine (FASTI)
    ("p06vx8jxh5x", "p0s2rwktsx4"),    # Early Eneolithic — Romania (INP)
    ("p06vx8jsmqk", "p0s2rwktsx4"),    # Mature Eneolithic — Romania (INP)
    ("p06vx8jczcj", None),             #   Cucuteni-Ariusd-Tripolie
    ("p0qk76zvrnc", "p0s2rwktsx4"),    # Predynastic — Egypt (UCLA)
    # ── Bronze Age ───────────────────────────────────────────────────────────────────────────
    ("p0s2rwkwbsz", None),             # Bronze Age — Eurasia (ROAD)
    # the Aegean (BSA 2022): Early/Middle/Late Bronze Age, then Helladic, Cycladic, Minoan
    ("p0ds9qjq7b2", "p0s2rwkwbsz"),    # Bronze Age — Greece
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
    ("p0m64tdfpbq", "p0s2rwkwbsz"),    # Bronze Age — Levant
    ("p0m64tdm68m", None), ("p0m64tdzzwv", None), ("p0m64td29pd", None), ("p0m64tdg8fq", None),
    ("p0m64tddkc2", None), ("p0m64tdb3v4", None), ("p0m64tdftbr", None), ("p0m64tdbnfp", None),
    ("p0m64td2tp9", "p0s2rwkwbsz"),    # Bronze Age — Southern Mesopotamia
    ("p0m64tddbc7", None), ("p0m64tdzs88", None), ("p0m64td7xqg", None), ("p0m64tdfq8d", None),
    ("p0m64td7whf", None), ("p0m64tdkfpr", None), ("p0m64td859f", None), ("p0m64td282s", None),
    ("p0m64tdpvjc", None),
    ("p0m64tdcj4g", "p0s2rwkwbsz"),    # Bronze Age — Northern Mesopotamia
    ("p0m64td4dcx", None), ("p0m64td5t47", None), ("p0m64tdw8bh", None), ("p0m64tdn3h5", None),
    ("p0m64tdmdq2", None), ("p0m64tdcjqh", None),
    # Anatolia (Pleiades; the Hittite kingdoms from "Ancient cities", 2014)
    ("p03wskdnzgc", "p0s2rwkwbsz"),    # Early Bronze Age Anatolia
    ("p03wskd82ps", "p0s2rwkwbsz"),    # Middle Bronze Age Anatolia
    ("p03wskd2qsd", "p0s2rwkwbsz"),    # Late Bronze Age Anatolia
    ("p083p5r9zvn", "p03wskd2qsd"),    #   Old Hittite Kingdom
    ("p083p5r5nk9", "p03wskd2qsd"),    #   Middle Hittite Kingdom
    ("p083p5rmv3r", "p03wskd2qsd"),    #   Hittite Empire
    # the steppe's western edge: Ukraine (FASTI) and Romania (INP)
    ("p06v8w4hzqv", "p0s2rwkwbsz"),    # Early Bronze Age — Ukraine
    ("p06v8w4bk7n", "p0s2rwkwbsz"),    # Middle Bronze Age — Ukraine
    ("p06v8w4v2r4", "p0s2rwkwbsz"),    # Late Bronze Age — Ukraine
    ("p06v8w4jrfp", "p0s2rwkwbsz"),    # Final Bronze Age — Ukraine
    ("p06vx8j82tz", "p0s2rwkwbsz"),    # Bronze Age — Romania
    ("p06vx8jwkfm", None), ("p06vx8j6r99", None), ("p06vx8j3drv", None),
    ("p06vx8jw8kg", None),             #   Cucuteni-Ariusd-Tripolia (Early Bronze Age)
    ("p06vx8jcxfv", None),             #   Yamnaya (Middle Bronze Age)
    # Central and South Asia (British Museum; "Ancient cities")
    ("p08m57hq9gh", "p0s2rwkwbsz"),    # BMAC
    ("p08m57h54j8", "p0s2rwkwbsz"),    # Indus Civilisation
    ("p083p5rwkfs", "p0s2rwkwbsz"),    # Harappan, or Mature
    # ── Dynastic Egypt (EAMENA's span, the UCLA Encyclopedia of Egyptology's divisions) ──────
    ("p0m64tdwspc", None),
    ("p0qk76zkc72", "p0m64tdwspc"), ("p0qk76z92f9", "p0m64tdwspc"), ("p0qk76zqqr2", "p0m64tdwspc"),
    ("p0qk76zkb23", "p0m64tdwspc"), ("p0qk76zxgwt", "p0m64tdwspc"), ("p0qk76zvjkw", "p0m64tdwspc"),
    ("p0qk76zpmnv", "p0m64tdwspc"), ("p0qk76zh6n2", "p0m64tdwspc"),
    # ── Iron Age ─────────────────────────────────────────────────────────────────────────────
    ("p0s2rwkrbhq", None),             # Iron Age — Eurasia (ROAD)
    ("p0ds9qjrvwn", "p0s2rwkrbhq"),    # Early Iron Age — Greece (BSA)
    ("p0ds9qjpt9w", None), ("p0ds9qjwq59", None), ("p0ds9qjdx5q", None), ("p0ds9qjkzn8", None),
    ("p0m64td6gpk", "p0s2rwkrbhq"),    # Iron Age — Levant/Mesopotamia (EAMENA)
    ("p0m64tdrvzc", None), ("p0m64tddh9w", None), ("p0m64td54gz", None), ("p0m64td9rbc", None),
    ("p0m64td6qd2", None), ("p0m64tdt67n", None), ("p0m64td573h", None),
    ("p03wskdm4tb", "p0s2rwkrbhq"),    # Early Iron Age Anatolia (Pleiades)
    ("p03wskdv8qb", "p03wskdm4tb"),    #   Neo-Hittite Northern Levant
    ("p03wskdjrcs", "p0s2rwkrbhq"),    # Middle-Late Iron Age Anatolia
    ("p03wskd29mn", "p0s2rwkrbhq"),    # Urartian Eastern Anatolia
    ("p06v8w4t56d", "p0s2rwkrbhq"),    # Iron Age — Ukraine (FASTI)
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
    ("p0m64tdf9bv", "p0ds9qjpdcd"),    # Classical/Pre-Islamic — Levant/Mesopotamia/Iran (EAMENA)
    ("p0m64tds5x4", None), ("p0m64tdp4rp", None), ("p0m64tdj7x3", None), ("p0m64td6537", None),
    ("p0qk76zn9c9", "p0ds9qjpdcd"),    # Ptolemaic — Egypt (UCLA)
    ("p0qk76zbv62", "p0ds9qjpdcd"),    # Roman — Egypt (UCLA)
    # ── the Middle Ages ──────────────────────────────────────────────────────────────────────
    ("p06c6g32z39", None),             # Middle Ages, 500-1500 — Europe (Library of Congress)
    ("p0jrrjbbvc7", "p06c6g32z39"),    # Early Middle Ages — Western Europe (EDH)
    ("p08m57hbmrr", "p06c6g32z39"),    # Late Medieval — Europe (British Museum)
    ("p0ds9qjcznw", "p06c6g32z39"),    # Byzantine — Greece (BSA)
    ("p0ds9qjd8b9", None), ("p0ds9qj4wcg", None), ("p0ds9qjh6s9", None),
    ("p0m64tdm947", "p06c6g32z39"),    # Islamic — Iraq (EAMENA)
    ("p0m64tdm3d2", None), ("p0m64tdk66s", None), ("p0m64td3mq8", None),
]


def build(dump, out):
    authorities = json.load(open(dump))["authorities"]
    by_id = {pid: (aid, a, p) for aid, a in authorities.items() for pid, p in a.get("periods", {}).items()}
    wanted = [pid for pid, _ in SELECTION]
    missing = [pid for pid in wanted if pid not in by_id]
    if missing:
        sys.exit(f"not in this PeriodO dump: {', '.join(missing)}")
    if len(set(wanted)) != len(wanted):
        sys.exit("SELECTION names a period twice")
    seed, parents = {}, {}
    for pid, parent in SELECTION:
        aid, a, p = by_id[pid]
        auth = seed.setdefault(aid, {k: v for k, v in a.items() if k != "periods"} | {"periods": {}})
        auth["periods"][pid] = p
        if parent is not None:
            if parent not in by_id or parent not in wanted:
                sys.exit(f"{pid}: parent {parent} is not in the selection")
            parents[pid] = parent
    doc = {
        "source": "PeriodO (https://perio.do), CC0 1.0 — a subset made by db/periods/build.py",
        "authorities": seed,
        "histmersion": {"parents": parents},
    }
    with open(out, "w") as f:
        json.dump(doc, f, ensure_ascii=False, indent=1, sort_keys=True)
        f.write("\n")
    roots = [pid for pid, parent in SELECTION
             if parent is None and by_id[pid][2].get("broader") not in wanted]
    print(f"{len(wanted)} periods from {len(seed)} authorities, {len(roots)} presets: "
          + ", ".join(by_id[r][2]["label"] for r in roots))


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    build(sys.argv[1], sys.argv[2])
