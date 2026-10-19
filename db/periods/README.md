# `db/periods` — the period presets (#719)

The web timeline's **period presets** are the rows of the `periods` table whose `preset` names a
**region heading**, and this directory is where an install gets them from. `periodo-seed.json` is a
curated subset of [PeriodO](https://perio.do) — a gazetteer of period *definitions*, each one a label,
a time span and a spatial scope **as one published authority states them**. PeriodO's data is
**CC0 1.0**.

```bash
histmersion periods seed                      # the presets bundled with the server (idempotent)
histmersion periods seed --replace            # …and delete the seed periods the bundled seed no longer has
histmersion periods search Minoan             # → ids, spans, regions, parents, preset headings
histmersion periods show p0ds9qjh4dh          # Late Minoan: authority, parent, divisions
histmersion periods fetch periodo             # the live dump: config periods.periodo-collections
histmersion periods fetch periodo --collection p0qp9rs   # one more authority (here: the Cambridge Companion to the Aegean Bronze Age)
```

A server imports this file by itself on a start that finds the `periods` table empty **or** finds the
bundled seed newer than the one it last applied (`histmersion.version` in the file against the
`seed_versions` table, V062), and `histmersion init` does the same on demand (see `docs/user-guide/getting-started/install.md`, "First-run data").
So an existing install follows a new seed on its next start; `periods seed --replace` is only needed
to also DELETE what an older seed brought in and this one no longer has.

## Why presets are regional

There is no single "Bronze Age": PeriodO's authorities date it from 3600 BCE in the Levant (EAMENA)
to 1800 BCE in Norway (ARIADNE), and the Neolithic, Chalcolithic and Iron Age likewise. So the menu
offers each age **as one authority dates it for one region**, grouped under a region heading, and
every row says WHERE it applies (PeriodO's spatial coverage, e.g. "Greece", "England",
"Germany, Switzerland, Czech Republic, Liechtenstein") and WHO defines it (the authority and its
PeriodO id, on the row and in its tooltip). When the map mostly shows one region, that region's
heading comes first and is unfolded.

Region-first (not period-first) because a chronology is regional: under "Aegean" the presets read
as the BSA's own sequence (Palaeolithic → Neolithic → Bronze Age → … → Byzantine), and the named
periods that are not "ages" at all — the Egyptian kingdoms, the Hittite kingdoms, Shang and Zhou,
Yamnaya and Cucuteni — sit in their region without being forced under an age no source assigns them.

## What is in it

282 definitions from 20 PeriodO authorities; **149 presets under 17 headings**:

| heading | presets (authority) |
|---|---|
| Aegean | Palaeolithic … Byzantine, with Helladic / Cycladic / Minoan (the British School at Athens, 2022) |
| Anatolia | Pottery Neolithic, Chalcolithic, Early/Middle/Late Bronze Age, Iron Age I–III (Levantine Ceramics Project, 2012); the Hittite kingdoms (Ancient cities); Urartu (Pleiades) |
| Levant and Mesopotamia | Neolithic, Chalcolithic, Bronze Age (Levant · Southern · Northern Mesopotamia), Iron Age, Classical, Islamic (EAMENA, 2021); Kebaran, Natufian (ROAD) |
| Iran | Neolithic, Chalcolithic and Bronze Age by sub-region, Iron Age (EAMENA) |
| Egypt | Neolithic, Predynastic, the kingdoms and intermediate periods, Ptolemaic, Roman (UCLA Encyclopedia of Egyptology) |
| Cyprus | Neolithic, Chalcolithic, Bronze Age, Iron Age (Cypriot antiquities text panels) |
| Southeast Europe | Romania (INP 2023: Neo-Eneolithic, Cucuteni, Yamnaya, Bronze Age, Iron Age I–II), Bulgaria (ARIADNE), Serbia (Cultural Monuments in Serbia) |
| Eastern Europe | Ukraine: Mesolithic … Iron Age, Cimmerian, Scythian, Sarmatian (FASTI) |
| Central Europe | Germany/Switzerland/Czechia, Austria, Hungary, Poland (ARIADNE, 2015) |
| Italy · Iberia | FASTI (+ the British Museum's Copper Age) · Portugal (DB-HERITAGE) |
| Britain and Ireland | England (Historic England), Scotland (ScAPA), Ireland — Neolithic, Chalcolithic, Bronze Age, Iron Age |
| Scandinavia | Denmark, Norway (ARIADNE) |
| Central Asia · South Asia · China | BMAC · ROAD's South Asian Palaeolithic, Harappan, Vedic, Maurya · Yangshao, Longshan, Shang, Western/Eastern Zhou (British Museum) |
| Continent-wide definitions | ROAD's Lower–Upper Palaeolithic, Epipalaeolithic, Mesolithic; the Middle Ages (Library of Congress, EDH, British Museum) |

**Not offered, and why** (the rows stay in the store and in `periods search`):

- ROAD's Eurasia-wide Neolithic, Chalcolithic, Bronze Age and Iron Age — one authority's umbrella
  spans, which the old menu presented as THE Bronze Age (3300–1500 BCE) everywhere;
- Pleiades' Anatolian Bronze Age (it dates the *Early* Bronze Age 2000–1750 BC);
- the British Museum's "Indus Civilisation" beside "Harappan, or Mature", and EAMENA's "Dynastic
  Egypt" beside the UCLA divisions — duplicates;
- China, South Asia, Central Asia have no PeriodO "Bronze Age" or "Iron Age", and the Caucasus has
  nothing usable — they get what PeriodO does define, or nothing.

## Modelling decisions

- **Ranges are PeriodO's, untouched.** The file is the dump's own format filtered down; `build.py`
  converts nothing. The curation is the `histmersion` overlay: `presets` (which definitions the menu
  offers, under which heading — a core `PresetRegion`), `parents` and `version`.
- **"Part of" is always the authority's own.** A curated parent must be of the SAME authority and span
  its child (`build.py` refuses another authority, `PeriodsSuite` checks both): the old seed filed every
  regional definition under ROAD's Eurasian span, Islamic Iraq under the Library of Congress's
  European Middle Ages, and the Epipalaeolithic under a Palaeolithic that ends before it does.
- **Years.** PeriodO writes ISO 8601 years, which have a year 0 (`"-2999"` is 3000 BC); Histmersion's
  signed years do not (3000 BCE is `-3000`), so the reader moves years `≤ 0` down by one. A span is a
  definition's OUTER bounds (`earliestYear` of the start, `latestYear` of the stop). Some
  authorities write round BC numbers straight into the ISO field (`"-2000"` for "2000 BC"); they
  are read as ISO says, one year earlier.
- **The seed decides its own rows; a fetch never moves anything.** Re-seeding re-files the seed's
  periods where the seed says (that is how an upgrade undoes the old nesting) and makes the seed's
  presets the only presets; `periods fetch periodo` puts a fetched collection under ONE node for its
  authority, keeps every stored period where it is, and never takes a preset off the menu.
- **Labels** are the authority's own when it writes English, else its English alternative
  (FASTI labels its Ukrainian periods in Ukrainian and gives the English beside them).

## Regenerating

```bash
./fetch.sh                    # downloads the PeriodO dump, rewrites periodo-seed.json
git diff --stat db/periods    # review before committing
```

`build.py <p0d.json> <out.json>` is the whole transformation; its `SELECTION` and `PRESETS` tables are
the curation. Bump `VERSION` in it whenever they change, so running servers re-apply the seed.
