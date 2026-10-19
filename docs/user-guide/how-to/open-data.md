# Open data: places, periods, events, pictures, the terrain map

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

## The terrain base map

The web page's [terrain map](../exploring/web-ui.md#the-terrain-map) — relief, coast, water,
rivers, land cover and the physical names, without modern roads, towns or borders — is drawn from
three files the server downloads once and then serves itself. Nothing is downloaded until you ask:

```bash
histmersion basemap fetch --dry-run     # reads only the providers' indexes; prints the sizes
histmersion basemap fetch               # the download, as a job (it can be stopped and resumed)
histmersion basemap status              # what is there, where, and how much space is left
histmersion basemap rm                  # remove it again (--layer vector|terrain|labels)
histmersion basemap fetch --layer labels   # only the physical names (about 20 MB read, 1.4 MB kept)
```

| layer | what | source | licence and credit |
|---|---|---|---|
| `vector` | coastlines, seas, lakes, rivers and streams with their names; land cover and land use; natural points (peaks, springs) | a [Protomaps](https://protomaps.com) daily build of OpenStreetMap and Natural Earth | © OpenStreetMap contributors, [ODbL](https://www.openstreetmap.org/copyright); Natural Earth public domain |
| `labels` | the names of mountain ranges, deserts, plateaus, peninsulas, island groups, large lakes, major rivers and peaks | [Natural Earth](https://www.naturalearthdata.com) 5.1.2's label layers | public domain (credited on the map all the same) |
| `terrain` | elevation, shaded into relief by the page | [Mapterhorn](https://mapterhorn.com), mostly the Copernicus GLO-30 elevation model | © Mapterhorn; Copernicus DEM © DLR e.V. 2010–2014 and © Airbus Defence and Space GmbH 2014–2018, provided under COPERNICUS by the European Union and ESA; further open sources are listed on [Mapterhorn's attribution page](https://mapterhorn.com/attribution) |

The credits are shown on the map whenever it is on. Both providers publish one large file for the
whole world (139 GB and 356 GB) and document downloading **a region of it** with HTTP range requests,
which is what the fetch does: it reads their index, then only the tiles of your region, in a few
dozen large requests a quarter of a second apart. Roads, transit, buildings, place names, borders and
modern points of interest are dropped before it is stored (`basemap.vector.drop`); **everything else
is kept** — land use, every water feature and its names in every language, natural points — and the
[style](terrain-styles.md) decides what is drawn, so changing the look never needs another download.
`--keep-all` stores the vector layer unfiltered. `histmersion basemap status` says what the stored
extract kept, and recommends one more fetch for one made before 2026-10-19 (which kept only coast,
water and land cover).

**Sizes** for the default region (`basemap.bbox`, `-12,20,90,62`: Atlantic Europe to the Indus,
the Sahel to southern Scandinavia), measured on 2026-10-19:

| `--max-zoom` (vector) | downloaded | stored (default) | stored (`--keep-all`) |
|---|---|---|---|
| 8 | 0.22 GB | 0.18 GB | 0.21 GB |
| 9 | 0.63 GB | 0.54 GB | 0.61 GB |
| **10** (default) | **1.5 GB** | **≈ 1.3 GB** | ≈ 1.5 GB |

Nine tenths of it is OpenStreetMap's land use (woods, fields, towns, mapped in fine detail where
people mapped it), which only the *Terrain + vegetation* style draws; the zoom-10 figures are
estimated from a measured part of the region. To keep the download small, drop it:
`basemap.vector.drop = "roads; transit; buildings; boundaries; places; landuse; pois +peak +volcano"`
(then close to 16, 56 and 138 MB, as before 2026-10-19). The web page asks the server only for the layers its style draws,
so a large extract costs the page nothing it does not show.

| `--terrain-max-zoom` (512-pixel tiles) | downloaded = stored |
|---|---|
| 6 | 0.05 GB |
| 7 | 0.16 GB |
| **8** (default) | **0.49 GB** |
| 9 | 1.6 GB |
| 10 | 5.0 GB |

So the default fetch downloads about 2 GB and keeps about 1.8 GB. A larger region costs less than
its area suggests (the sea is one tile repeated): `-25,5,95,66` is 1.9 GB downloaded at vector zoom
10, 0.67 GB of terrain at zoom 8. Each zoom deeper roughly triples the terrain.

**Disk space.** The fetch first adds up what it will download and store, and refuses — before
downloading anything — when that would leave less than `basemap.min-free-gb` (5 GB) free where the
files go; it checks again before each request. The downloaded parts are deleted once the files are
written.

**Where the files are.** In the cache directory, `$XDG_CACHE_HOME/histmersion/basemap` (by default
`~/.cache/histmersion/basemap`, on macOS too): they can always be fetched again. Set `basemap.dir`
(`HISTMERSION_BASEMAP_DIR`) to put them elsewhere, an external disk for instance.

**Interrupted?** Run the same command again: the parts already downloaded are kept, and the fetch
continues with the same build. A fetch with another region or zoom starts afresh. The fetch checks
that every part comes from the same version of the provider's file (its size and ETag); when a new
build replaces it mid-download, the fetch stops and says so — run it again.

**Updating.** The landscape barely changes; there is no need to fetch again. `basemap.vector.build`
pins a Protomaps build (`"20261019.pmtiles"`) instead of the newest.

