# Open data: places, periods, events, pictures

Besides your sources, Histmersion can import public datasets: places for the map, named periods for
the timeline, events, and pictures. Each comes with **its own licence and terms** — some ask to be
cited, some forbid commercial use. The tables below list them; honour them when you reuse what you
imported. Every import asks the service politely (at its pace, waiting when asked to wait) and can
run first as a **dry run** that stores nothing.

!!! note "Incomplete by nature"
    Every dataset covers some regions and centuries better than others. What is missing from them is
    not evidence of anything ([Responsible use](../responsible-use.md#5-absence-is-not-evidence)).

## Places (the gazetteer)

### What ships with Histmersion

Imported by itself on the first start (or with `histmersion init`):

| data | what | licence |
|---|---|---|
| Natural Earth | continents, subregions, countries; seas, rivers, mountains and other features; present-day cities | public domain |
| Pleiades | ~34 000 ancient places with their attested names | CC BY 3.0 (attribution kept on every place) |
| curated supplement | historical regions, valleys and sites the others lack | CC0 (Wikidata coordinates) |
| traced polities | Bronze- and Iron-Age states and macro-regions, per period | CC0 / ODbL |

### Downloaded on request

```bash
histmersion init --fetch                       # the network sources below, in order: cliopatria, awmc, land, periodo
histmersion places fetch cliopatria --dry-run  # one of them, as a dry run first
histmersion places fetch awmc
histmersion places fetch chronontology
histmersion places fetch land                  # the coastline, used to clip states to the land
```

| `places fetch …` | what | licence |
|---|---|---|
| `cliopatria` | Seshat Cliopatria: ~13 700 polity outlines, 3400 BCE to today | CC BY-NC-SA (non-commercial) |
| `awmc` | Ancient World Mapping Center: regional names of the classical world, linked to Pleiades | ODbL |
| `chronontology` | ChronOntology (DAI): ~14 000 named periods with regions | open access, as published by the DAI |
| `land` | Natural Earth land and minor islands (not places; only for clipping) | public domain |

### Large gazetteers, from your own extracts

GeoNames, Wikidata and the Getty Thesaurus of Geographic Names are too large to bundle and change
constantly, so Histmersion pins **an extract**: a file with a fixed checksum, built by
`db/gazetteer/build.py`. The extracts are not hosted anywhere: build them with that script (it writes
to the right folder by default) or copy them from another install. `histmersion places sources`
shows what is pinned, what is on disk, and the exact path to put a file at.

```bash
histmersion places sources
histmersion places load wikidata            # a dry run: reads, joins and counts
histmersion places load wikidata --apply    # the import (a job)
histmersion places load geonames --apply
histmersion places load tgn --apply
histmersion places load lpf --apply         # every Linked Places file in the drop folder
```

| `places load …` | licence |
|---|---|
| `geonames` | CC BY 4.0 |
| `wikidata` | CC0 |
| `tgn` (Getty TGN) | ODC Attribution License (ODC-By 1.0) |
| `lpf` | each file's own — World Historical Gazetteer downloads and other Linked Places Format files you put in `places.sources.lpf-dir` |

They join the existing places **by shared identifiers only** (a Wikidata id, a Pleiades id), so an
import never creates a duplicate by name.

You can also import any Linked Places or GeoJSON file of your own: `histmersion places import
<file.json>`.

## Periods (PeriodO)

The bundled presets are a curated part of PeriodO (CC0). For more:

```bash
histmersion periods fetch periodo                         # the authorities in periods.periodo-collections
histmersion periods fetch periodo --collection p0qp9rs    # one more authority
histmersion periods import my-periods.json                # a PeriodO dataset of your own
```

## Events

```bash
histmersion events catalogues                                # what this server can import, licences, how to cite
histmersion events import gvp --dry-run                      # stores nothing; prints what it would import
histmersion events import gvp --min-vei 4
histmersion events import noaa --min-magnitude 6.5 --from -3000 --to 0
histmersion events import wikidata --bbox 20,30,45,45
```

| catalogue | licence | cite as |
|---|---|---|
| `gvp` — Smithsonian Global Volcanism Program | Smithsonian Terms of Use (non-commercial use with attribution) | Global Volcanism Program, *Volcanoes of the World* (v. 5.x), Smithsonian Institution, compiled by Venzke, E. <https://doi.org/10.5479/si.GVP.VOTW5-2024.5.2> |
| `noaa` — NOAA NCEI Natural Hazards | public domain | National Geophysical Data Center / World Data Service: NCEI/WDS Global Significant Earthquake, Tsunami and Volcano Databases. NOAA National Centers for Environmental Information. <https://doi.org/10.7289/V5TD9V7K> |
| `wikidata` — battles, sieges, wars, treaties, revolts, disasters | CC0 | Wikidata (CC0), <https://www.wikidata.org> |

By default an import reads the years −12000 to 499 (`events.min-year`, `events.max-year`) and keeps
every event inside the research region `events.region` (`-25,20,90,70`: longitude −25 to 90,
latitude 20 to 70); outside it, only eruptions of VEI 4 or more (the volcanic explosivity index) and earthquakes of magnitude 6.5 or more are kept. Records of the same kind from
two catalogues close in space and time are merged into one event. An import is a server job that can
be resumed; it never overwrites an event someone compiled or corrected.

## Pictures

```bash
histmersion media providers                       # what this server can import, with licences and terms
histmersion media import wikidata --dry-run       # counts per licence; stores nothing
histmersion media import met --dry-run --limit 5  # a museum: try a few places first
histmersion media import loc --place <place-id>
```

| `media import …` | asks about | stores |
|---|---|---|
| `wikidata` | places and events with a Wikidata item | their image, locator map, plan, aerial view, route map — from Wikimedia Commons |
| `depicts` | events with a Wikidata item | works depicting the event, and Commons files that say they depict it |
| `met` · `cleveland` · `aic` | places on the map | Open Access museum objects (public domain / CC0) found at that place and dated within its period |
| `loc` | places on the map | Library of Congress historical maps: "no known restrictions" and dated up to 1928 |
| `pleiades` | places with a Pleiades id | the Commons files the Pleiades record links |
| `opencontext` | places on the map | excavation photographs and drawings, each under its own Creative Commons licence (records flagged as human remains are left out) |

The rules every provider follows:

- **documented APIs only**, at the provider's pace, with no scraping and no working around a bot
  check — a challenge stops the run;
- **no personal data** sent: the User-Agent names the project, not you;
- **the licence is read from each record**, and judged against the provider's allowlist (by default
  public domain, CC0, CC BY, CC BY-SA; non-commercial only if you add it);
- **museum objects are tied to a place by their recorded find-spot and dates**, never by
  coordinates, and the reason is stored on the link.

Details of each provider, and those not included and why: {{ repo_file("docs/media-providers.md") }}.
How to reuse a picture: [Pictures](../exploring/media.md#reusing-a-picture).
