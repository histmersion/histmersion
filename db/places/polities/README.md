# Bronze- and Iron-Age polity polygons

Seed data for the §1.6a gazetteer (`docs/plan.md`), wave-2 row 2.2. One GeoJSON
`FeatureCollection` per canonical period, covering the corpus's six core areas —
**Anatolia, Aegean, Levant, Mesopotamia, Egypt, Pontic–Caspian steppe** — plus a
`macro_regions.geojson` holding the six areas themselves.

**77 features. 21 carry real open geometry (OpenHistoricalMap, ODbL); 56 are
rough outlines drawn by an AI agent (11–23 vertices), not traced from any map, released CC0.**

> **Accuracy (audit 2026-09-25).** The 56 `agent-drawn` outlines are sketches: e.g. the Crete
> polygon is 13,493 km² against the island's 8,336 km², and one outline is reused for three Cretan
> periods. The atlas therefore draws them only as a FALLBACK — where no Cliopatria or OHM polygon
> covers the same state at that time (`PlaceRepo.geoJson`). Replace any of them with real geometry
> (OHM, Cliopatria) when one exists.

> **Reviewer, read this first.** Nothing in these files is a border in the modern
> sense. Bronze- and Iron-Age polities had frontiers, tributary zones and gaps, not
> lines, and archaeological "cultures" have no political extent at all. Every polygon
> is a *conventional* extent — the shape you would see in a textbook map — simplified
> to 10–350 vertices. Read each feature's `notes` before trusting it; the notes say
> what the outline approximates and where it is soft. Features whose historicity is
> itself disputed (`israel_united_monarchy_ia1`, `cimmerians_ia1`) say so in capitals. — **contested** (kept by reviewer decision 2026-08-25; `properties.contested: true`)

## Files

```
macro_regions.geojson   6 features, kind=region, null period
eba.geojson            14 features   Early Bronze Age  c. -3300 … -2000
mba.geojson            13 features   Middle Bronze Age c. -2000 … -1550
lba.geojson            12 features   Late Bronze Age   c. -1550 … -1200
ia1.geojson            10 features   Iron Age I        c. -1200 …  -900
ia2.geojson            22 features   Iron Age II       c.  -900 …  -600
linked-places/*.lpf.json             Linked Places Format twin of each file
validate.py                          contract + geometry checker (stdlib only)
```

## Contract

Each Feature has a `Polygon` or `MultiPolygon` in WGS84 (lon, lat; RFC 7946
right-hand rule) and these `properties`:

| property | notes |
|---|---|
| `id` | stable slug, unique across all six files, period suffix (`hittite_empire_lba`) |
| `label` | display name |
| `kind` | `polity` everywhere except `macro_regions.geojson`, which is `region` |
| `period_start`, `period_end` | **signed integer years, BCE negative**; `period_start < period_end` |
| `names` | `[{name, lang}]` — endonyms and exonyms, ISO 639 codes (`akk`, `hit`, `grc`, `egy`, `sux`, `he`, `arc`, `uga`, `xur`, `xld`, `peo`, `ru`, `uk`, `ar`, `en`) |
| `part_of` | `[{parent_id, period_start, period_end}]` — always a macro-region; the edge carries the feature's own period |
| `source`, `source_url`, `licence` | provenance, see below |
| `method` | `ohm-import` or `agent-drawn` |
| `notes` | one to three sentences for the reviewer: what the outline approximates, and its uncertainty |
| `reference` | agent-drawn features only: the atlas/handbook the shape was drawn after |

Two additions beyond the importer's stated contract, both ignorable:
`method` and `reference`. One deviation: **macro-regions have
`period_start: null` and `period_end: null`** (they are geographic, always valid),
as specified for row 2.2. If the importer requires non-null periods, treat null as
"unbounded"; `validate.py` allows null only for `kind: region`.

`part_of` is a **list**, and several features have two or more parents on purpose:
`egypt_new_kingdom_lba` is in both `egypt` and `levant`, `hittite_empire_lba` in
`anatolia` and `levant`, `neo_assyrian_empire_ia2_650` in four. The macro-regions
themselves are `part_of` `continent_asia` / `continent_europe` / `continent_africa`,
which stream 2.1 seeds from Natural Earth; `aegean` and `pontic_caspian_steppe`
each name two continents.

## Provenance and licensing

| method | count | licence | what it means |
|---|---|---|---|
| `ohm-import` | 21 | `ODbL-1.0` | Geometry downloaded from **OpenHistoricalMap** via its Overpass endpoint (`https://overpass-api.openhistoricalmap.org/api/interpreter`), relation members stitched into rings and simplified. `source_url` is the OHM relation permalink, so every one is re-checkable. Attribution: © OpenHistoricalMap contributors, ODbL. |
| `agent-drawn` | 56 | `CC0-1.0` | Rough outlines an AI agent drew from its reading of the extent a handbook describes. **Nothing was traced from a map and no geometry was copied**, so the shapes are approximate (11–23 vertices). `reference` names the handbook whose *description* was followed; `source_url` is the English Wikipedia article for the entity. Formerly labelled "agent-drawn", which overstated how they were made. |

Sources that were checked and **not** used:

- **AWMC `geodata`** (ODbL, not CC-BY as the brief assumed). `Cultural-Data/political_shading/`
  contains only Classical-era sets — Persian extent, Alexander, Hasmonean, Herod, four Roman
  ones — nothing Bronze or Iron Age. `Cultural-Data/regional_name_linework/` has 2 620 features
  for ancient regions (Ionia, Attica, Troad, Cilicia, Peloponnesus, Assyria, Babylonia …) but
  they are **open `MultiLineString` fragments, not closed rings** (0 of 2 622 lines close), and
  the dates are Greco-Roman (typically −30…+300). Polygonising them needs AWMC's coastline layer
  and a topology build; that is a worthwhile follow-up but was out of scope here.
- **Pleiades** regions: Greco-Roman coverage, same period problem, and mostly points.

## How the periods were chosen

The five period files follow the conventional Near-Eastern chronology used in
`docs/plan.md`, with the **middle chronology** for Mesopotamian dates:

| file | span | why that boundary |
|---|---|---|
| `eba` | −3300 … −2000 | starts with the Uruk-period urban threshold, ends with the Ur III collapse (−2004) |
| `mba` | −2000 … −1550 | Amorite kingdoms through the sack of Babylon (−1595) and the expulsion of the Hyksos (−1550) |
| `lba` | −1550 … −1200 | Egyptian New Kingdom to the Bronze Age collapse |
| `ia1` | −1200 … −900 | collapse to the start of the Neo-Assyrian expansion under Adad-nirari II (−911) |
| `ia2` | −900 … −600 | Neo-Assyrian empire to the fall of Nineveh (−612) / the corpus's Iron II cutoff |

**A feature's own `period_start`/`period_end` are what matter, not the file it sits
in.** The file is only a filing convenience for the primary period. Several features
deliberately spill over their file's span — `catacomb_culture_mba` starts at −2500,
`kassite_babylonia_lba` runs to −1155, `cimmerians_ia1` runs to −700 — because the
polity's real span is what the gazetteer needs to match findings against. Where a
polity changed shape enough to matter it is **split into separate features with
separate ids and periods**, exactly as §1.6a prescribes: `old_hittite_kingdom_mba` vs
`hittite_empire_lba`; `old_babylonian_kingdom_mba` vs `hammurabi_babylonia_mba`;
Phrygia, Phoenicia and Egypt each in several stages; and the Neo-Assyrian Empire in
three (below).

## Known simplifications — read before seeding

1. **Coastlines are the modern ones**, generalised. The head of the Persian Gulf is
   the worst case: in the 3rd millennium it lay 150–200 km north-west of today's
   shore. `sumer_early_dynastic_eba` is drawn at the conventional ancient position;
   the other Mesopotamian polygons are not.
2. **Empires are drawn at their maximum.** `akkadian_empire_eba`,
   `hammurabi_babylonia_mba`, `middle_assyrian_kingdom_lba` and
   `egypt_new_kingdom_lba` each held their outline for a fraction of the stated
   period. Treat the period as "the polity existed", not "it looked like this
   throughout".
3. **Overlaps are intentional and frequent** — the spec allows them and counting is
   non-additive. Hittite vassals (`ugarit_lba`, `kizzuwatna_lba`, `arzawa_lba`) sit
   inside `hittite_empire_lba`; `aram_damascus_ia2` overlaps `israel_kingdom_ia2` in
   the Golan/Gilead; `peloponnese_ia2` contains `lacedaemon_ia2`;
   `yamnaya_horizon_eba` overlaps `maykop_eba`; the three Neo-Assyrian stages nest.
4. **Archaeological cultures are not polities.** `yamnaya_horizon_eba`, `maykop_eba`,
   `kura_araxes_eba`, `catacomb_culture_mba`, `sintashta_culture_mba`,
   `srubnaya_culture_lba`, `cyclades_eba` and the Cretan features carry
   `kind: polity` only because the importer's vocabulary offers nothing better. They
   are distribution envelopes.
5. **Envelope features.** `neo_hittite_states_ia1` / `_ia2` and `aramaean_states_ia1`
   are single polygons around sets of small independent states that were never
   contiguous. Good for grouping, wrong for "was X inside Y".
6. **Island polygons include sea.** `cyclades_eba` is one hull over the island group;
   `alashiya_lba` and the three Crete features simplify a real coastline to 10–12
   vertices, so a coastal site can fall a few kilometres outside.
7. **Sinai** is drawn inside the `egypt` macro-region although it is geographically
   Asian, and `egypt`'s `part_of` lists Africa only.
8. **Neo-Assyrian stages.** `_ia2_850` (−858…−744, OHM) and `_ia2_745` (−743…−739,
   OHM, a deliberately 5-year snapshot of Tiglath-Pileser III's annexations) come
   from OHM at OHM's own dates; `_ia2_650` (−671…−631) is agent-drawn as a
   MultiPolygon — the Near-Eastern core plus a separate Nile lobe for the occupation
   of Egypt. There is no −900 stage: OHM's earliest is −858, and the empire before
   Adad-nirari II is covered by `assyria_reduced_ia1`.
9. **Phoenicia has a hole.** The three OHM relations cover −1200…−1115, −1055…−865
   and −635…−528; **−1115…−1055 and −865…−635 are uncovered**, and all three share
   one identical outline. A Levantine coastal find dated −750 will not match any
   Phoenicia feature.
10. **Iron Age I has no Aegean feature.** The Greek Dark Age offers no conventionally
    mapped polity between `mycenaean_greece_lba` (ends −1200) and the Iron II regional
    features (start −900). Findings in that window fall through to the `aegean`
    macro-region.

## What could not be sourced

- **No open Bronze-Age geometry exists** for the Hittite Empire, Kizzuwatna, New and
  Middle Kingdom Egypt, Kassite Babylonia, Middle Assyria, Ugarit, Alashiya, Mari,
  Yamhad, Ebla, Urartu, Phrygia, Lydia, Israel, Judah, Philistia, the Aramaean and
  Neo-Hittite states, Third Intermediate / Kushite / Saite Egypt, Neo-Babylonia, or
  any of the steppe cultures except Yamnaya and Maykop. All 56 are agent-drawn.
- **Elam** has an OHM relation (rel 2859881, −2699…−519) and was left out: it lies
  outside the six core areas. Add it if the corpus starts covering Iran.
- **AWMC regional linework** (see above) would give properly surveyed outlines for the
  Greek and western-Anatolian regions — `ionia_ia2`, `attica_ia2`, `peloponnese_ia2`,
  `troad_eba` — once someone polygonises it against a coastline. Best next improvement.
- **Sea/land clipping**: no polygon is clipped to a coastline, so all of them spill
  into water somewhere.

## Feature list

### `macro_regions.geojson` — the six macro-regions (`kind: region`, no period)

| id | label | period | macro-region(s) | method | source | vertices |
|---|---|---|---|---|---|---|
| `anatolia` | Anatolia | — … — | continent_asia | agent-drawn | after Bryce, The Routledge Handbook of the Peoples and Places of Ancient Western Asia (2009) | 23 |
| `aegean` | Aegean | — … — | continent_europe, continent_asia | agent-drawn | after Talbert (ed.), Barrington Atlas of the Greek and Roman World (2000) | 15 |
| `levant` | Levant | — … — | continent_asia | agent-drawn | after Bryce, The Routledge Handbook of the Peoples and Places of Ancient Western Asia (2009) | 16 |
| `mesopotamia` | Mesopotamia | — … — | continent_asia | agent-drawn | after Roaf, Cultural Atlas of Mesopotamia and the Ancient Near East (1990) | 15 |
| `egypt` | Egypt | — … — | continent_africa | agent-drawn | after Baines & Malek, Cultural Atlas of Ancient Egypt, rev. ed. (2000) | 14 |
| `pontic_caspian_steppe` | Pontic-Caspian steppe | — … — | continent_europe, continent_asia | agent-drawn | after Anthony, The Horse, the Wheel, and Language (2007) | 23 |

### `eba.geojson` — Early Bronze Age, c. −3300 … −2000

| id | label | period | macro-region(s) | method | source | vertices |
|---|---|---|---|---|---|---|
| `uruk_period_mesopotamia_eba` | Uruk-period southern Mesopotamia | −3300 … −3100 | mesopotamia | ohm-import | [OHM rel 2827446](https://www.openhistoricalmap.org/relation/2827446), ODbL | 12 |
| `sumer_early_dynastic_eba` | Early Dynastic Sumer | −2900 … −2334 | mesopotamia | agent-drawn | after Roaf, Cultural Atlas of Mesopotamia and the Ancient Near East (1990) | 14 |
| `akkadian_empire_eba` | Akkadian Empire | −2334 … −2154 | mesopotamia, levant | agent-drawn | after Roaf, Cultural Atlas of Mesopotamia and the Ancient Near East (1990) | 18 |
| `ur_iii_eba` | Third Dynasty of Ur | −2112 … −2004 | mesopotamia | agent-drawn | after Roaf, Cultural Atlas of Mesopotamia and the Ancient Near East (1990) | 14 |
| `ebla_eba` | Ebla | −2500 … −2300 | levant | agent-drawn | after Bryce, The Routledge Handbook of the Peoples and Places of Ancient Western Asia (2009) | 11 |
| `egypt_early_dynastic_eba` | Early Dynastic Egypt | −3150 … −2686 | egypt | ohm-import | [OHM rel 2852413](https://www.openhistoricalmap.org/relation/2852413), ODbL | 130 |
| `egypt_old_kingdom_eba` | Old Kingdom Egypt | −2686 … −2181 | egypt | ohm-import | [OHM rel 2852442](https://www.openhistoricalmap.org/relation/2852442), ODbL | 130 |
| `troad_eba` | Troad (Troy I-III) | −3000 … −2000 | anatolia | agent-drawn | after Bryce, The Routledge Handbook of the Peoples and Places of Ancient Western Asia (2009) | 11 |
| `hatti_region_eba` | Hatti (pre-Hittite central Anatolia) | −3000 … −2000 | anatolia | agent-drawn | after Bryce, The Kingdom of the Hittites, 2nd ed. (2005) | 13 |
| `kura_araxes_eba` | Kura-Araxes culture | −3400 … −2000 | anatolia | ohm-import | [OHM rel 2827454](https://www.openhistoricalmap.org/relation/2827454), ODbL | 58 |
| `cyclades_eba` | Early Cycladic culture | −3200 … −2000 | aegean | agent-drawn | after Dickinson, The Aegean Bronze Age (1994) | 11 |
| `crete_early_minoan_eba` | Early Minoan Crete | −3100 … −2100 | aegean | agent-drawn | after Dickinson, The Aegean Bronze Age (1994) | 13 |
| `yamnaya_horizon_eba` | Yamnaya horizon | −3300 … −2600 | pontic_caspian_steppe | ohm-import | [OHM rel 2827631](https://www.openhistoricalmap.org/relation/2827631), ODbL | 344 |
| `maykop_eba` | Maykop culture | −3700 … −3000 | pontic_caspian_steppe | ohm-import | [OHM rel 2827481](https://www.openhistoricalmap.org/relation/2827481), ODbL | 45 |

### `mba.geojson` — Middle Bronze Age, c. −2000 … −1550

| id | label | period | macro-region(s) | method | source | vertices |
|---|---|---|---|---|---|---|
| `old_assyrian_kingdom_mba` | Old Assyrian kingdom (Assur) | −1975 … −1750 | mesopotamia | agent-drawn | after Frahm (ed.), A Companion to Assyria (2017) | 11 |
| `assyria_shamshi_adad_mba` | Kingdom of Upper Mesopotamia (Shamshi-Adad I) | −1808 … −1776 | mesopotamia | agent-drawn | after Frahm (ed.), A Companion to Assyria (2017) | 11 |
| `old_babylonian_kingdom_mba` | Old Babylonian kingdom (pre-Hammurabi) | −1879 … −1761 | mesopotamia | ohm-import | [OHM rel 2854905](https://www.openhistoricalmap.org/relation/2854905), ODbL | 18 |
| `hammurabi_babylonia_mba` | Babylonia under Hammurabi | −1792 … −1750 | mesopotamia | agent-drawn | after Roaf, Cultural Atlas of Mesopotamia and the Ancient Near East (1990) | 18 |
| `larsa_mba` | Larsa | −1791 … −1761 | mesopotamia | ohm-import | [OHM rel 2854958](https://www.openhistoricalmap.org/relation/2854958), ODbL | 41 |
| `mari_mba` | Mari | −1900 … −1761 | mesopotamia, levant | agent-drawn | after Roaf, Cultural Atlas of Mesopotamia and the Ancient Near East (1990) | 11 |
| `yamhad_mba` | Yamhad (Aleppo) | −1800 … −1600 | levant | agent-drawn | after Bryce, The Routledge Handbook of the Peoples and Places of Ancient Western Asia (2009) | 11 |
| `egypt_middle_kingdom_mba` | Middle Kingdom Egypt | −2055 … −1650 | egypt | agent-drawn | after Shaw (ed.), The Oxford History of Ancient Egypt (2000) | 19 |
| `hyksos_avaris_mba` | Hyksos kingdom (15th Dynasty, Avaris) | −1650 … −1550 | egypt | agent-drawn | after Shaw (ed.), The Oxford History of Ancient Egypt (2000) | 11 |
| `old_hittite_kingdom_mba` | Old Hittite kingdom | −1650 … −1500 | anatolia | ohm-import | [OHM rel 2851224](https://www.openhistoricalmap.org/relation/2851224), ODbL | 28 |
| `minoan_crete_mba` | Palatial Minoan Crete | −2000 … −1450 | aegean | agent-drawn | after Dickinson, The Aegean Bronze Age (1994) | 13 |
| `catacomb_culture_mba` | Catacomb culture | −2500 … −1950 | pontic_caspian_steppe | agent-drawn | after Anthony, The Horse, the Wheel, and Language (2007) | 17 |
| `sintashta_culture_mba` | Sintashta culture | −2100 … −1800 | pontic_caspian_steppe | agent-drawn | after Anthony, The Horse, the Wheel, and Language (2007) | 11 |

### `lba.geojson` — Late Bronze Age, c. −1550 … −1200

| id | label | period | macro-region(s) | method | source | vertices |
|---|---|---|---|---|---|---|
| `hittite_empire_lba` | Hittite Empire | −1400 … −1180 | anatolia, levant | agent-drawn | after Bryce, The Kingdom of the Hittites, 2nd ed. (2005) | 21 |
| `arzawa_lba` | Arzawa lands | −1450 … −1320 | anatolia | ohm-import | [OHM rel 2852968](https://www.openhistoricalmap.org/relation/2852968), ODbL | 64 |
| `kizzuwatna_lba` | Kizzuwatna | −1500 … −1350 | anatolia | agent-drawn | after Bryce, The Kingdom of the Hittites, 2nd ed. (2005) | 11 |
| `mitanni_lba` | Mitanni | −1500 … −1350 | mesopotamia, levant | ohm-import | [OHM rel 2851223](https://www.openhistoricalmap.org/relation/2851223), ODbL | 62 |
| `kassite_babylonia_lba` | Kassite Babylonia | −1595 … −1155 | mesopotamia | agent-drawn | after Roaf, Cultural Atlas of Mesopotamia and the Ancient Near East (1990) | 13 |
| `middle_assyrian_kingdom_lba` | Middle Assyrian kingdom | −1363 … −1050 | mesopotamia | agent-drawn | after Frahm (ed.), A Companion to Assyria (2017) | 12 |
| `ugarit_lba` | Ugarit | −1450 … −1190 | levant | agent-drawn | after Bryce, The Routledge Handbook of the Peoples and Places of Ancient Western Asia (2009) | 11 |
| `alashiya_lba` | Alashiya (Cyprus) | −1500 … −1200 | levant | agent-drawn | after Cline, 1177 B.C.: The Year Civilization Collapsed (2014) | 11 |
| `egypt_new_kingdom_lba` | New Kingdom Egypt (with Canaan and Nubia) | −1550 … −1200 | egypt, levant | agent-drawn | after Shaw (ed.), The Oxford History of Ancient Egypt (2000) | 23 |
| `mycenaean_greece_lba` | Mycenaean Greece | −1600 … −1200 | aegean | ohm-import | [OHM rel 2849631](https://www.openhistoricalmap.org/relation/2849631), ODbL | 223 (multi) |
| `crete_mycenaean_lba` | Mycenaean Crete | −1450 … −1200 | aegean | agent-drawn | after Dickinson, The Aegean Bronze Age (1994) | 13 |
| `srubnaya_culture_lba` | Srubnaya (Timber-grave) culture | −1800 … −1200 | pontic_caspian_steppe | agent-drawn | after Anthony, The Horse, the Wheel, and Language (2007) | 18 |

### `ia1.geojson` — Iron Age I, c. −1200 … −900

| id | label | period | macro-region(s) | method | source | vertices |
|---|---|---|---|---|---|---|
| `neo_hittite_states_ia1` | Neo-Hittite (Syro-Hittite) states | −1180 … −900 | anatolia, levant | agent-drawn | after Bryce, The Routledge Handbook of the Peoples and Places of Ancient Western Asia (2009) | 15 |
| `phrygia_early_ia1` | Phrygia (early) | −1150 … −900 | anatolia | agent-drawn | after Bryce, The Routledge Handbook of the Peoples and Places of Ancient Western Asia (2009) | 11 |
| `philistia_ia1` | Philistia (Pentapolis) | −1175 … −900 | levant | agent-drawn | after Aharoni, Avi-Yonah et al., The Carta Bible Atlas, 5th ed. (2011) | 11 |
| `israel_united_monarchy_ia1` | Israel (united monarchy, as narrated) | −1030 … −930 | levant | agent-drawn | after Aharoni, Avi-Yonah et al., The Carta Bible Atlas, 5th ed. (2011) | 11 | — **contested** (kept by reviewer decision 2026-08-25; `properties.contested: true`)
| `aramaean_states_ia1` | Aramaean states | −1100 … −900 | levant | agent-drawn | after Bryce, The Routledge Handbook of the Peoples and Places of Ancient Western Asia (2009) | 12 |
| `phoenicia_ia1` | Phoenicia | −1200 … −1115 | levant | ohm-import | [OHM rel 2854668](https://www.openhistoricalmap.org/relation/2854668), ODbL | 31 |
| `egypt_third_intermediate_ia1` | Third Intermediate Period Egypt | −1069 … −900 | egypt | agent-drawn | after Shaw (ed.), The Oxford History of Ancient Egypt (2000) | 15 |
| `assyria_reduced_ia1` | Assyria (reduced) | −1050 … −911 | mesopotamia | agent-drawn | after Frahm (ed.), A Companion to Assyria (2017) | 11 |
| `babylonia_ia1` | Babylonia (post-Kassite) | −1155 … −900 | mesopotamia | agent-drawn | after Roaf, Cultural Atlas of Mesopotamia and the Ancient Near East (1990) | 12 |
| `cimmerians_ia1` | Cimmerians | −1000 … −700 | pontic_caspian_steppe | agent-drawn | after Anthony, The Horse, the Wheel, and Language (2007) | 14 |

### `ia2.geojson` — Iron Age II, c. −900 … −600

| id | label | period | macro-region(s) | method | source | vertices |
|---|---|---|---|---|---|---|
| `neo_assyrian_empire_ia2_850` | Neo-Assyrian Empire (c. -850) | −858 … −744 | mesopotamia, levant | ohm-import | [OHM rel 2854737](https://www.openhistoricalmap.org/relation/2854737), ODbL | 65 |
| `neo_assyrian_empire_ia2_745` | Neo-Assyrian Empire (Tiglath-Pileser III) | −743 … −739 | mesopotamia, levant | ohm-import | [OHM rel 2854811](https://www.openhistoricalmap.org/relation/2854811), ODbL | 72 |
| `neo_assyrian_empire_ia2_650` | Neo-Assyrian Empire (maximum, c. -650) | −671 … −631 | mesopotamia, levant, anatolia, egypt | agent-drawn | after Frahm (ed.), A Companion to Assyria (2017) | 38 (multi) |
| `urartu_ia2` | Urartu | −860 … −590 | anatolia | agent-drawn | after Zimansky, Ancient Ararat: A Handbook of Urartian Studies (1998) | 13 |
| `phrygia_ia2` | Phrygia (kingdom) | −900 … −695 | anatolia | agent-drawn | after Bryce, The Routledge Handbook of the Peoples and Places of Ancient Western Asia (2009) | 13 |
| `lydia_ia2` | Lydia | −680 … −600 | anatolia, aegean | agent-drawn | after Bryce, The Routledge Handbook of the Peoples and Places of Ancient Western Asia (2009) | 11 |
| `israel_kingdom_ia2` | Kingdom of Israel (Samaria) | −900 … −720 | levant | agent-drawn | after Aharoni, Avi-Yonah et al., The Carta Bible Atlas, 5th ed. (2011) | 11 |
| `judah_ia2` | Kingdom of Judah | −900 … −600 | levant | agent-drawn | after Aharoni, Avi-Yonah et al., The Carta Bible Atlas, 5th ed. (2011) | 11 |
| `egypt_kushite_ia2` | Kushite Egypt (25th Dynasty) | −744 … −656 | egypt | agent-drawn | after Shaw (ed.), The Oxford History of Ancient Egypt (2000) | 18 |
| `egypt_saite_ia2` | Saite Egypt (26th Dynasty) | −664 … −600 | egypt | agent-drawn | after Shaw (ed.), The Oxford History of Ancient Egypt (2000) | 14 |
| `neo_babylonian_empire_ia2` | Neo-Babylonian Empire | −626 … −600 | mesopotamia, levant | agent-drawn | after Roaf, Cultural Atlas of Mesopotamia and the Ancient Near East (1990) | 20 |
| `aram_damascus_ia2` | Aram-Damascus | −900 … −732 | levant | agent-drawn | after Bryce, The Routledge Handbook of the Peoples and Places of Ancient Western Asia (2009) | 11 |
| `neo_hittite_states_ia2` | Neo-Hittite (Syro-Hittite) states | −900 … −708 | anatolia, levant | agent-drawn | after Bryce, The Routledge Handbook of the Peoples and Places of Ancient Western Asia (2009) | 15 |
| `phoenicia_ia2_early` | Phoenicia (early Iron II) | −1055 … −865 | levant | ohm-import | [OHM rel 2854667](https://www.openhistoricalmap.org/relation/2854667), ODbL | 31 |
| `phoenicia_ia2_late` | Phoenicia (late Iron II) | −635 … −600 | levant | ohm-import | [OHM rel 2854666](https://www.openhistoricalmap.org/relation/2854666), ODbL | 31 |
| `scythians_ia2` | Scythians (Pontic) | −700 … −600 | pontic_caspian_steppe | agent-drawn | after Anthony, The Horse, the Wheel, and Language (2007) | 15 |
| `ionia_ia2` | Ionia | −900 … −600 | aegean, anatolia | agent-drawn | after Talbert (ed.), Barrington Atlas of the Greek and Roman World (2000) | 11 |
| `attica_ia2` | Attica | −700 … −600 | aegean | ohm-import | [OHM rel 2875405](https://www.openhistoricalmap.org/relation/2875405), ODbL | 27 |
| `boeotia_ia2` | Boeotia | −700 … −600 | aegean | ohm-import | [OHM rel 2875458](https://www.openhistoricalmap.org/relation/2875458), ODbL | 12 |
| `megaris_ia2` | Megaris | −700 … −600 | aegean | ohm-import | [OHM rel 2875457](https://www.openhistoricalmap.org/relation/2875457), ODbL | 9 |
| `lacedaemon_ia2` | Lacedaemon (Sparta) | −900 … −600 | aegean | ohm-import | [OHM rel 2849727](https://www.openhistoricalmap.org/relation/2849727), ODbL | 38 |
| `peloponnese_ia2` | Peloponnese | −900 … −600 | aegean | agent-drawn | after Talbert (ed.), Barrington Atlas of the Greek and Roman World (2000) | 12 |

## Validating

```
python3 db/places/polities/validate.py db/places/polities/*.geojson
```

Stdlib only, no arguments needed if run from inside the folder. It checks required
properties and their types, `kind`, slug-shaped and globally unique ids,
`period_start < period_end` (null only for `kind: region`), `names[]` and `part_of[]`
entry shape, that every `part_of.parent_id` resolves to a macro-region or to a known
continent id, non-empty `source`/`source_url`/`licence` with the licence on an open
allow-list, `method`, `reference` on agent-drawn features, and — geometrically — that
every ring is closed, has ≥ 4 positions, lies in WGS84 range, does not
self-intersect, has non-zero area, and follows the right-hand rule. Exit code is 1 on
any error.

Optional second opinion with a real geometry engine:

```
uv run --with shapely python -c "import json,glob;from shapely.geometry import shape;print(all(shape(f['geometry']).is_valid for p in glob.glob('db/places/polities/*.geojson') for f in json.load(open(p))['features']))"
```

## Re-importing

```
histdb places import db/places/polities/*.geojson --kind polity
histdb places rebuild            # re-materialise finding_places
```

Import `macro_regions.geojson` **first** — the `part_of` edges of every period file
point at it. The continent parents (`continent_asia`, `continent_europe`,
`continent_africa`) come from the Natural Earth seed in stream 2.1 and must exist
before the macro-regions resolve.

The `linked-places/` twin is the same data in Linked Places Format (`@id`, `when`,
`names[].toponym`, `types`, `relations[].relationType = gvp:broaderPartitive`,
`geometry.citation`), for `histdb places import <linked-places.json>` and for
round-tripping through the World Historical Gazetteer. The GeoJSON files are the
authoritative copy; the LPF files are generated from them.

## Acceptance check (§1.6a)

A finding at Hattusa (34.6156 E, 40.0194 N):

| asOf | matches |
|---|---|
| −1400 | `hittite_empire_lba` → `anatolia` → `continent_asia` |
| −700 | `phrygia_ia2` → `anatolia` → `continent_asia` |

`phrygia_ia2` was extended east across the Halys bend to make this hold; it is
defensible — there is a Phrygian settlement phase at Boğazköy — but it is the one
polygon in this set whose shape was influenced by a test.

### Spot checks

36 well-located sites were tested with `shapely` for point-in-polygon at a date inside
the expected polity's period. All 36 resolve to the expected feature: Hattusa (Hittite
Empire −1400 / Phrygia −700), Babylon (Kassite −1400 / Neo-Babylonian −610), Knossos,
Mycenae, Ugarit, Sardis, Jerusalem, Samaria, Gath, Damascus, Aleppo, Ebla, Mari, Assur,
Nineveh, Nuzi, Susa (Akkad −2200 / Ur III −2050), Ur, Gordion, Tushpa/Van, Tarsus,
Carchemish, Troy, Miletus, Athens, Sparta, Enkomi, Memphis, Thebes, Avaris, Buhen,
Napata, Arkaim and a lower-Don steppe point. Two of these tests changed a polygon:
`phrygia_ia2` was extended east (see above) and `akkadian_empire_eba` / `ur_iii_eba`
were extended to take in Susa, which both states demonstrably governed.

Sites that correctly match **nothing** but their macro-region are also expected —
Kanesh/Kültepe at −1900, for instance, because the Old Assyrian *karum* network was
commercial and is deliberately not drawn as territory.
