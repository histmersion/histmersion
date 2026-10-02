# `db/periods` — the period presets (#719)

The web timeline's **period presets** are the top-level rows of the `periods` table, and this
directory is where a new install gets them from. `periodo-seed.json` is a curated subset of
[PeriodO](https://perio.do) — a gazetteer of period *definitions*, each one a label, a time span and a
spatial scope **as one published authority states them**. PeriodO's data is **CC0 1.0**.

```bash
histmersion periods seed                      # the presets bundled with the server (idempotent)
histmersion periods search Minoan             # → ids, spans, regions, parents
histmersion periods show p0ds9qjh4dh          # Late Minoan: authority, parent, divisions
histmersion periods fetch periodo             # the live dump: config periods.periodo-collections
histmersion periods fetch periodo --collection p0qp9rs   # one more authority (here: the Cambridge Companion to the Aegean Bronze Age)
```

A server imports this file by itself on its first start when the `periods` table is empty, and
`histmersion init` does the same on demand (see the main README).

## What is in it

202 definitions from 12 PeriodO authorities, under 11 presets: **Paleolithic, Mesolithic, Neolithic,
Chalcolithic, Bronze Age, Dynastic Egypt, Iron Age, Vedic Period, Maurya Period, Antiquity, Middle
Ages** — Europe, the Aegean, Anatolia, the Levant and Mesopotamia, Egypt, the western steppe
(Ukraine, Romania) and South/Central Asia. The Bronze Age, for instance, goes down to the phases:

```
Bronze Age (ROAD, Eurasia)
├─ Bronze Age (BSA, Greece) ─ Early/Middle/Late Bronze Age
│  ├─ Bronze Age Helladic ─ Early/Middle/Late Helladic ─ EH I–III … LH IIIA–C
│  ├─ Bronze Age Cycladic ─ Early/Middle/Late Cycladic
│  └─ Bronze Age Minoan  ─ Early/Middle/Late Minoan ─ EM I–III … LM IIIA–C, the palatial periods
├─ Bronze Age (EAMENA: Levant · Southern Mesopotamia · Northern Mesopotamia) ─ EB 1 … LB 2, ED I … Kassite
├─ Early/Middle/Late Bronze Age Anatolia (Pleiades) ─ the Hittite kingdoms
├─ Early … Final Bronze Age (FASTI, Ukraine) · Bronze Age (INP, Romania) ─ Yamnaya, Cucuteni
└─ BMAC · Indus Civilisation · Harappan
```

Every row's `authority` names the PeriodO id and the publication that states its range
(`PeriodO p0ds9qjh4dh · The British School at Athens: archaeological periods of Greece (2022)`), so
two authorities' "Late Bronze Age" stay two rows with two citations; nothing is averaged or merged.

| authority (PeriodO id) | used for |
|---|---|
| ROAD — ROCEEH Out of Africa Database (`p0s2rwk`) | the broad Eurasian presets; Palaeolithic industries |
| The British School at Athens (`p0ds9qj`) | the Aegean, Neolithic to Byzantine |
| EAMENA (`p0m64td`) | the Levant, Mesopotamia, Dynastic Egypt, the Islamic periods |
| UCLA Encyclopedia of Egyptology (`p0qk76z`) | Egypt's kingdoms and intermediate periods |
| Pleiades (`p03wskd`) | Anatolia |
| Ancient cities (`p083p5r`) | the Hittite kingdoms; Harappan |
| FASTI (`p06v8w4`) · Institutul Național al Patrimoniului (`p06vx8j`) | Ukraine; Romania (Yamnaya, Cucuteni) |
| British Museum (`p08m57h`) · Art history (`p0z5nvh`) | BMAC, Indus Civilisation, Late Medieval; Vedic and Maurya |
| Library of Congress (`p06c6g3`) · EDH (`p0jrrjb`) | the Middle Ages |

## Modelling decisions

- **Ranges are PeriodO's, untouched.** The file is the dump's own format filtered down; `build.py`
  converts nothing. The one curated thing is `histmersion.parents`: where a definition sits in the
  preset tree when its authority gives it no `broader` period inside the selection.
- **Years.** PeriodO writes ISO 8601 years, which have a year 0 (`"-2999"` is 3000 BC); Histmersion's
  signed years do not (3000 BCE is `-3000`), so the reader moves years `≤ 0` down by one. A span is a
  definition's OUTER bounds (`earliestYear` of the start, `latestYear` of the stop). Some
  authorities write round BC numbers straight into the ISO field (`"-2000"` for "2000 BC"); they
  are read as ISO says, one year earlier.
- **A preset is never buried.** `periods fetch periodo` puts a fetched collection under ONE node
  for its authority, but a period already in the store keeps its place (and a top-level one stays
  top-level), so re-fetching never moves what the seed or a person placed.
- **Labels** are the authority's own when it writes English, else its English alternative
  (FASTI labels its Ukrainian periods in Ukrainian and gives the English beside them).

## Regenerating

```bash
./fetch.sh                    # downloads the PeriodO dump, rewrites periodo-seed.json
git diff --stat db/periods    # review before committing
```

`build.py <p0d.json> <out.json>` is the whole transformation; its `SELECTION` table is the curation.
