# Open data: places, periods, events, pictures, the base maps

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
| `cliopatria` | Seshat Cliopatria: ~13 700 polity outlines, 3400 BCE to today | CC BY 4.0 |
| `awmc` | Ancient World Mapping Center: regional names of the classical world, linked to Pleiades | ODbL |
| `chronontology` | ChronOntology (DAI): ~14 000 named periods with regions | open access, as published by the DAI |
| `land` | Natural Earth land and minor islands (not places; only for clipping) | public domain |

`cliopatria`, `awmc` and `chronontology` are built-in plugins (`gazetteer-cliopatria`, `gazetteer-awmc`,
`gazetteer-chronontology`); `histmersion.plugins.gazetteer-<name>.enabled = false` (or
`HISTMERSION_PLUGIN_GAZETTEER_<NAME>_ENABLED=false`) takes one out of `places fetch` and `init --fetch`, and
the places it brought stay on the map.

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

The extracts cover **the whole world, with names in every language** their source gives: what is left
out is left out by kind (a bare hamlet known only by a dot, a hotel, a school), never by where it is.
A smaller install can build regional extracts instead: `build.py --bbox minLon,minLat,maxLon,maxLat`
(or `--bbox window`, Europe to Central Asia, which the pinned extracts were cut to until 2026-10-27)
and `--langs en,fr,…`; pin them with `build.py pin`. The bundled places (Natural Earth, Pleiades)
cover the whole world too.

### How long it takes, and how much room

Measured on 2026-10-27 on a laptop (Apple silicon, an empty corpus), for the whole world in every
language:

| step | time | room it needs |
|---|---:|---|
| first start: the bundled places and period presets (automatic) | under 1 min | the database grows by ~0.2 GB (46 000 places) |
| build the GeoNames extract: download the two dumps (627 MB), then `build.py geonames` | a few minutes + ~1.5 min | 6.3 GB of memory while it runs; 70 MB extract |
| build the Wikidata extract: `build.py wikidata-fetch` (15 queries to the public QLever service), then `build.py wikidata` | **2–4 hours** + 3 min | 2.8 GB of query results on disk, 9.5 GB of memory to build; 121 MB extract |
| build the TGN extract: download Getty's export (1.3 GB) and AAT's (140 MB), then `build.py tgn` | **about 1.5 hours** (Getty sends ~14 MB a minute) + 2.5 min | 7.2 GB of memory to build; 3 MB extract |
| `places load geonames --apply` | ~9 min | the database grows by ~3.4 GB |
| `places load wikidata --apply` | ~27 min | the database grows by ~6.4 GB |
| `places load tgn --apply` | ~35 s | ~0.1 GB |

So with all three the gazetteer holds 1.8 M places, 17.5 M names and 2.6 M links, and the database
grows by **about 9.7 GB** (6.2 GB of it the names, with their search indexes); the imports take
**about 40 minutes**. Building the extracts yourself takes most of an afternoon, nearly all of it
waiting on the two public services; copying them from another install takes none. The Wikidata fetch
asks one query at a time and waits whenever the service asks it to (or its gateway fails), so its
time depends on how busy QLever is; it can be stopped and run again, and the queries it finished are
kept. A re-import of the same extract changes nothing and takes a fraction of the time.

A smaller install keeps a region and fewer languages instead: `--bbox -25,5,100,72 --langs window`
(Europe to Central Asia) gave about 4.9 GB and 20 minutes of imports.

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
every event it reads, wherever on Earth it happened (`events.region` is the whole world,
`-180,-90,180,90`). A smaller install can set a narrower `events.region` (for example `-25,20,90,70`,
Europe to Central Asia): every event inside it is kept, and outside it only eruptions of VEI 4 or more
(the volcanic explosivity index) and earthquakes of magnitude 6.5 or more. Records of the same kind from
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
| `smithsonian` | places on the map | Smithsonian Open Access objects whose image is CC0 — **off by default**: needs your own free api.data.gov key ([Credentials](credentials.md#where-you-need-credentials)) |
| `europeana` | places on the map | Europeana's openly licensed images (CC0, public domain, CC BY, CC BY-SA) — **off by default**: needs your own free Europeana personal key ([Credentials](credentials.md#where-you-need-credentials)) |

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

## The base map's data

The web page's two local [base maps](base-maps.md), **Readable** and **Physical** — relief, coast,
water, rivers, land cover, the sea floor and the physical names, and for Readable also today's towns,
main roads and borders — are drawn from files the server downloads once and then serves itself, so the
map works without the internet ([Working offline](offline.md)). Nothing is downloaded until you ask:

```bash
histmersion basemap fetch --dry-run     # reads only the providers' indexes; prints the sizes
histmersion basemap fetch               # the download, as a job (it can be stopped and resumed)
histmersion basemap fetch --keep-all    # the same, keeping roads, places and borders: what Readable draws
histmersion basemap status              # what is there, where, and how much space is left
histmersion basemap rm                  # remove it again (--layer vector|terrain|labels|bathymetry, --focus, --cache)
histmersion basemap fetch --layer labels   # only the physical names (about 20 MB read, 1.4 MB kept)
histmersion basemap fetch --layer bathymetry   # only the sea floor (about 80 MB)
histmersion basemap fetch --layer terrain --focus aegean   # the Aegean's relief to zoom 12, kept for good
```

| layer | what | source | licence and credit |
|---|---|---|---|
| `vector` | coastlines, seas, lakes, rivers and streams with their names; land cover and land use; natural points (peaks, springs); with `--keep-all` also roads, places and borders | a [Protomaps](https://protomaps.com) daily build of OpenStreetMap and Natural Earth | © OpenStreetMap contributors, [ODbL](https://www.openstreetmap.org/copyright); Natural Earth public domain |
| `labels` | the names of mountain ranges, deserts, plateaus, peninsulas, island groups, large lakes, major rivers and peaks | [Natural Earth](https://www.naturalearthdata.com) 5.1.2's label layers | public domain (credited on the map all the same) |
| `terrain` | elevation, shaded into relief by the page; deeper than any downloaded file, the server cuts a tile from the nearest coarser one | [Mapterhorn](https://mapterhorn.com), mostly the Copernicus GLO-30 elevation model | © Mapterhorn; Copernicus DEM © DLR e.V. 2010–2014 and © Airbus Defence and Space GmbH 2014–2018, provided under COPERNICUS by the European Union and ESA; further open sources are listed on [Mapterhorn's attribution page](https://mapterhorn.com/attribution) |
| `bathymetry` | the sea floor's depth, coloured and shaded by the page (Mapterhorn's sea is flat at 0) | [Mapzen's terrain tiles](https://registry.opendata.aws/terrain-tiles/) on AWS Open Data, whose sea is NOAA's [ETOPO1](https://www.ncei.noaa.gov/products/etopo-global-relief-model) (1 arc-minute, about 1.8 km) | ETOPO1: U.S. Government work, public domain, *not to be used for navigation*; credited as "ETOPO1 (NOAA) via Mapzen Terrain Tiles" ([attribution](https://github.com/tilezen/joerd/blob/master/docs/attribution.md)) |
| `bathymetry` with `basemap.bathymetry.source = gebco` | the same, four times sharper (15 arc-seconds, about 460 m); outside the downloaded region the sea floor stays ETOPO1's | the [GEBCO_2026 Grid](https://www.gebco.net/data-products/gridded-bathymetry-data) (GEBCO, under IHO and IOC-UNESCO), its GeoTIFFs at CEDA read by range | public domain, free of charge; using it accepts GEBCO's terms and disclaimer (*not to be used for navigation*); credited as "GEBCO Bathymetric Compilation Group 2026 (2026). The GEBCO_2026 Grid", [doi:10.5285/4f68d5c7-45eb-f999-e063-7086abc036fa](https://doi.org/10.5285/4f68d5c7-45eb-f999-e063-7086abc036fa) |

The map's label font, Noto Sans — Latin, Greek and Cyrillic, with Noto Sans Arabic, Armenian, Georgian
and Hebrew for local names in those scripts — is part of Histmersion itself (SIL Open Font License 1.1,
its text beside the files in `web/vendor/noto-sans/`), so the names show in their own scripts with no
font service.

The credits are shown on the map whenever it is on. Both providers publish one large file for the
whole world (139 GB and 356 GB) and document downloading **a region of it** with HTTP range requests,
which is what the fetch does: it reads their index, then only the tiles of your region, in a few
dozen large requests a quarter of a second apart. Roads, transit, buildings, place names, borders and
modern points of interest are dropped before it is stored (`basemap.vector.drop`); **everything else
is kept** — land use, every water feature and its names in every language, natural points — and the
base map decides what is drawn. `--keep-all` stores the vector layer unfiltered: the roads, places and
borders **Readable** draws (about 0.2 GB more). `histmersion basemap status` says what the stored
extract kept, which base map lacks something in it and the command that adds it, and recommends one
more fetch for one made before 2026-10-19 (which kept only coast, water and land cover).

**Sizes.** A fetch downloads the whole world by default (`basemap.bbox = world`): about **3.8 GB**
for the vector layer to zoom 10, **3.2 GB** for the relief to zoom 8 and **1.8 GB** for the sea floor
to zoom 7 (21 845 tiles) — about 8.8 GB in all (a dry run of Protomaps' build of 2026-10-27; zoom by
zoom, the vector layer is 0.37 GB to 8 and 1.0 GB more for 9). A smaller install downloads a region
instead (`basemap.bbox` or `--bbox`), and the map outside it is still drawn, read on demand. For
`-12,20,90,62` (Atlantic Europe to the Indus, the Sahel to southern Scandinavia, the default until
2026-10-27), measured on one complete zoom-10 download of that region (2026-10-20; zooms 8 and 9 are
its first zooms):

| `--max-zoom` (vector) | downloaded | stored (default) | stored (`--keep-all`) |
|---|---|---|---|
| 8 | 0.22 GB | 0.18 GB | 0.21 GB |
| 9 | 0.63 GB | 0.53 GB | 0.61 GB |
| **10** (default) | **1.54 GB** | **1.30 GB** | **1.52 GB** |

Nine tenths of it is OpenStreetMap's land use (woods, fields, towns, mapped in fine detail where
people mapped it), which the base maps draw over the coarser satellite land cover wherever it is mapped. To keep the download
small, drop it:
`basemap.vector.drop = "roads; transit; buildings; boundaries; places; landuse; pois +peak +volcano"`
(then close to 16, 56 and 138 MB, as before 2026-10-19). The web page asks the server only for the layers its style draws,
so a large extract costs the page nothing it does not show. (Readable then lacks its roads, towns
and borders.)

The relief, for the same region (the world: 0.67 GB to zoom 7, 3.2 GB to 8):

| `--terrain-max-zoom` (512-pixel tiles) | downloaded = stored |
|---|---|
| 6 | 0.05 GB |
| 7 | 0.16 GB |
| **8** (default) | **0.49 GB** |
| 9 | 1.6 GB |
| 10 | 5.0 GB |

**The relief deeper than the extract** (zooms 9 to 12 — 12 is about the 30 m of the Copernicus model)
is not downloaded for the whole region: when you look at an area that closely, the server reads just
the tiles you see from Mapterhorn's planet file (by range request, the access the provider documents
for extracts; at most `basemap.terrain.on-demand.concurrency` requests at once and
`requests-per-second` a second) and keeps them in a cache beside the extract (`terrain-cache/`, at
most `on-demand.cache-gb`, 3 GB, the least recently seen going first). An area seen once shows again
offline. Offline elsewhere, the map shows the coarser relief. `on-demand.enabled = false` switches it
off. To keep a region's detail for good — before a trip without internet, say — fetch it as a
**focus region** (`--focus <name>`: one of `basemap.terrain.focus-regions`, or a name of your own
with `--bbox`; stored in `terrain-focus/`, never evicted). Measured on 2026-10-20, zooms 9–12:

| focus region | box | tiles | downloaded = stored |
|---|---|---|---|
| `aegean` | 21.5,35,28.5,41.5 | 10 299 | 0.43 GB |
| `anatolia` | 26,36,45,42.5 | 28 313 | 2.0 GB |
| `levant` | 33.5,29.5,37.5,37.5 | 6 837 | 0.36 GB |
| `mesopotamia` | 38,29,49,38 | 20 873 | 1.3 GB |
| `nile-delta` | 29,30,32.5,31.7 | 1 296 | 0.05 GB |
| `argolid` | 22.5,37.4,23.2,37.9 | 92 | 7 MB |

`histmersion basemap fetch --layer terrain --focus aegean --dry-run` prints the figure for any box
first. A `--layer terrain --bbox …` fetch deeper than `basemap.terrain.max-zoom` becomes a focus
region of its own (named after its box), so it never replaces the broad extract.

**The sea floor** (`bathymetry`, zooms 0–7 of the region, 256-pixel tiles): about 1 200 tiles, 80 MB
for the default region, one request each a tenth of a second apart.

So the default fetch downloads about 2 GB and keeps about 1.8 GB (2.0 GB with `--keep-all`). A larger region costs less than
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

