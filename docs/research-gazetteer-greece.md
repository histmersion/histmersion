# Open data sources for Greek sites and monuments: a survey for the Histmersion gazetteer

Survey date: 2026-10-28. Research only: no repo or database was changed.
Small downloads used for the counts below (Pleiades places CSV, the PeriodO dataset, query results) were kept locally and are not committed.

## 0. What Histmersion keeps today (db/gazetteer/build.py)

- Wikidata roots (P31/P279*): historical country Q3024240, historical region Q1620908, former admin. entity Q19953632, ancient city Q15661340, polis Q148837, **archaeological site Q839954**, human settlement Q486972. Coordinates P625 are required.
- Times kept: P571 / P576 / P580 / P582, with precision and rank. **P2348 (time period) is not kept, and neither is P1435 (heritage designation).**
- IDs kept: P1566 (GeoNames), P1584 (Pleiades), P1667 (TGN).
- There is no OSM loader. `db/places` holds Natural Earth, Pleiades, curated and polities.
- **This explains the Argolis gap.** All four "missing" medieval/Ottoman monuments ARE in Wikidata, with coordinates. Their classes are outside the roots:

| monument | Wikidata | P31 | P1435 | date in WD | OSM |
|---|---|---|---|---|---|
| Akronauplia | Q3555617 | fortress, citadel | protected building + archaeological site in Greece | none | relation 3735419 `historic=castle castle_type=fortress historic:civilization=venetian wikidata=Q3555617` |
| Larissa Castle, Argos | Q1012861 | castle | both | none | relation 5348846 `historic=castle historic:civilization=byzantine ruins=yes wikidata=Q1012861` (also a duplicate node 8921650972) |
| Old Monastery of Talantio (Arachnaio) | Q56398298 | monastery | protected building in Greece | P571 1400 (century) | way 1361632992 `building=monastery historic=monument name=Μονή Ταλαντίου`. It has no wikidata tag. |
| Dormition church, Bermpaka (Merbaka/Agia Triada) | Q38280392 | Eastern Orthodox church building | both | P571 1200 and 1300 | way 847130250 `amenity=place_of_worship name=Βυζαντινός ιερός ναός Κοιμήσεως Θεοτόκου`. It has **no historic=\*** tag and no wikidata tag, so a `historic=*` filter would miss it. |
| Palaiokastro, Agios Adrianos | not found by label | – | – | – | not found by name in the Agios Adrianos bbox. Nearby is the Ottoman tower node 8921556785 `historic=castle castle_type=towerhouse historic:civilization=ottoman start_date=1750 wikidata=Q56398382`. |

## 1. OpenStreetMap

- **Coverage (Greece, Geofabrik taginfo, data until 2026-10-27).**
  - `historic=*`: 14,115 objects. Main values: ruins 2,989, memorial 2,406, archaeological_site 2,302, wayside_shrine 1,151, castle 1,112, yes 632, boundary_stone 564, monument 501, citywalls 356, bridge 269, building 224, tomb 198, monastery 107, city_gate 101, church 96, aqueduct 82, manor 64, tower 46, fort 38.
  - `amenity=place_of_worship`: 28,178. `amenity=monastery`: 562. `building=church`: 4,307. Historic churches sit mostly here, untagged as historic.
  - `castle_type`: fortress 262, castle 254, tower 209, fortification 93, towerhouse 66…
  - `archaeological_site=*`: 858 (settlement 137, fortification 132, temple 82, tomb 75, city 67, necropolis 45…). `site_type`: 0, because it is deprecated in favour of `archaeological_site`.
  - `heritage=*`: only 647 (2=486, 1=57 WHC). `heritage:operator`: 57 whc, a handful for the Ministry. `heritage:ref`: 3. **There is no Ministry register id in OSM.**
- **Dating.**
  - `historic:civilization`: 2,535. Values: ancient_greek 744, byzantine 477, medieval 312, modern 202, ottoman 164, venetian 150, ancient_roman 109, mycenaean 107, minoan 60, prehistoric 48, roman 30, neolithic 13… These are coarse and partly free-text.
  - `historic:period`: 237 (classical_greece 82, archaic_greece 31, hellenistic_greece 29, roman_greece 24, byzantine 15…).
  - `start_date`: 7,330 in total, but only 772 on `historic=*` objects.
  - About 18% of `historic=*` objects (2,529) carry a `wikidata` tag; 707 carry `wikipedia`.
- **Format and access.**
  - Geofabrik `greece-latest.osm.pbf` is **342 MB** (Last-Modified 2026-10-28) and is rebuilt daily. A filter cuts it to a few MB: `osmium tags-filter greece-latest.osm.pbf nwr/historic nwr/heritage nwr/archaeological_site nwr/amenity=monastery,place_of_worship nwr/building=church,chapel,monastery,cathedral -o gr-heritage.osm.pbf`.
  - Planet-wide it is ~80 GB pbf. A world-scale run would use the planet file or the Geofabrik continents with the same filter.
  - Overpass worked for light bbox queries, but only via WebFetch. **overpass-api.de answered plain curl with HTTP 406** (it rejects curl's default User-Agent), and the kumi.systems and private.coffee instances timed out. I did not work around this. A production fetcher needs Geofabrik or planet downloads, not Overpass.
- **Licence: ODbL 1.0.** https://www.openstreetmap.org/copyright: "You are free to copy, distribute, transmit and adapt our data, as long as you credit OpenStreetMap and its contributors. If you alter or build upon our data, you may distribute the result only under the same licence."
  - Bundling is legal in an AGPL app (ODbL allows commercial use), but **share-alike applies to the derived database**. A gazetteer table that merges OSM rows with other sources is a "Derivative Database" and must be offered under ODbL.
  - The cleanest pattern is to keep OSM as a separate, attributed layer (a "Collective Database"), or to ship only OSM ids and coordinates as links. Map renderings and other "Produced Works" only need attribution.
- **Quality.**
  - Coordinates are good: real geometries (ways and relations), often better than Wikidata points.
  - Weaknesses: many duplicates (node plus way for the same tholos, e.g. Kazarma and Prosymna), many unnamed objects (wayside shrines, memorials), sub-features (Italian-labelled stoas of the Argive Heraion), and very weak dating.
- **Verdict.** OSM adds geometry and the long tail (shrines, bridges, chapels), not dates. Its useful dating is `historic:civilization` mapped to PeriodO. Use it second, joined through `wikidata=`.

## 2. Wikidata (CC0)

- **Licence.** https://www.wikidata.org/wiki/Wikidata:Licensing: "All structured data from the main, Property, Lexeme, and EntitySchema namespace is available under the Creative Commons CC0 License". It can be bundled freely.
- **Access.** SPARQL on QLever (https://qlever.dev/api/wikidata) worked from curl with no UA issues. The old host qlever.cs.uni-freiburg.de now 308-redirects there. **build.py's default endpoint should be checked for that redirect.**
- **P1435 in Greece (P17=Q41), items per designation:**

| designation | items |
|---|---|
| protected building in Greece Q29048696 | 2,599 |
| archaeological site in Greece Q29048715 | 1,968 |
| ancient monument in Greece Q115796077 | 270 |
| part of WHS | 46 |
| traditional settlement Q29048704 | 34 |
| WHS | 24 |

  - Union of the three national designations: **4,381 items**. 4,380 have coordinates, only **778 (18%) are dated** (P571/P580/P2348), 406 have Pleiades ids and 4,034 a WLM id.
  - **Only 937 of these 4,381 are reachable through the current roots.**
- **Per class in Greece (P31/P279\*).** Columns: n / with coords / P571 / P2348 / P1435.

| class | n | coords | P571 | P2348 | P1435 |
|---|---|---|---|---|---|
| archaeological site | 4,771 | 4,397 | 143 | 986 | 869 |
| fortification Q57821 | 1,028 | 998 | 75 | 55 | 371 |
| castle Q23413 | 482 | 478 | 34 | 12 | 211 |
| monastery Q44613 | 863 | 852 | 213 | 2 | 370 |
| church building Q16970 | 3,978 | 3,918 | 525 | 5 | 1,484 |
| tower Q12518 | 522 | 519 | 29 | 11 | 120 |
| bridge Q12280 | 233 | 232 | 48 | 5 | 70 |
| monument Q4989906 | 3,877 | 3,787 | 1,958 | 262 | 140 |

  - The "monument" class is mostly modern memorials; that is where most of its P571 values come from.
  - A broader set (P1435 or any of those classes, with coordinates) has **15,452 items in Greece; 4,557 are already in the roots, so about 10,900 would be new. 5,614 (36%) are dated.**
  - **P2348 (time period) is the main dating field for archaeological sites (986 items), and Histmersion drops it today.**
- **Greek register ids on Wikidata.**
  - P13741 *Archaeological Cadastre (Greece) ID*: 227 items, format `164525:Monument` / `165600:Space`.
  - P2186 *Wiki Loves Monuments ID*: 6,282 Greek items, e.g. `GR-J11-0037`.
  - P9951 *Greek Castles ID* (kastra.eu): 1,116.
  - P8218 *Archaeology in Greece Online place ID*: 150.
  - P8068 *ToposText*: 2,804.
  - P1936 *DARE*: 1,283.
  - P11729 *Kulturenvanteri* (Ottoman-era inventory): 758 on designated items.
  - OSM node/way ids (P11693/P10689): about 400.
- **World scale.** P1435 has **2.12 M items with coordinates worldwide**. Adding "P1435 any" plus the classes above is unchanged worldwide, but volume grows sharply. Bands or paging on QLever are needed, as build.py already does for settlements.

## 3. Greek Ministry of Culture

- **listedmonuments.culture.gr** (Ongoing Catalogue of Listed Sites and Monuments: >10,000 entries, >18,000 sites, designation acts since 1921): **the hostname no longer resolves (NXDOMAIN, 2026-10-28)**. Its successor is the Archaeological Cadastre.
- **Archaeological Cadastre**, https://www.arxaiologikoktimatologio.gov.gr (Directorate of the National Archive of Monuments):
  - Contents: >17,000 monuments, ~3,160–3,400 archaeological sites, 404 historic sites, ~818–844 protection zones, ~220–237 museums.
  - The Angular front end calls an undocumented JSON API: `https://ops.arxaiologikoktimatologio.gov.gr/api/portal/monument?id=…`, `/portal/space`, `/portal/protection-zone`, `/portal/museum`.
  - Record fields: name, otherNames, category (e.g. "Χερσαίος Αρχαιολογικός Χώρος"), Greek description, protectionType, protectionStatus (ministerial decision plus ΦΕΚ, e.g. `73/Β/1991`), administrative units (Kallikratis/Kapodistrias), modern/historic/ancient place names, evidence (photos), and `scientificDocumentation` with fromPeriod/toPeriod/fromCentury. Its period vocabulary has 20 terms, from Stone Age through Paleochristian, Early/Middle/Late Byzantine, Post-Byzantine and New/Modern/Contemporary.
  - **The public record has no coordinates.** Geometries sit in an ArcGIS Enterprise behind `gisserver/rest/services/{Hosted,services_ops,services_external}`, which returns "Token Required". The ArcGIS portal is private.
  - No list or bulk endpoint was found, so harvesting would mean crawling ids. That is not an offered service.
  - **Terms of use**, last updated 15-10-2025, in the site's en.json "TERMS_OF_USE":
    - "All content … the source code and databases, constitutes the exclusive property of the Directorate of the National Archive of Monuments and the Ministry of Culture".
    - "Storage or reproduction of data is permitted, provided that the source and copyright are acknowledged ("© National Archive of Monuments – Ministry of Culture") and that the data is used exclusively for personal, research, educational, informational and **strictly non-commercial purposes**, in accordance with the provisions of Article 46 of Law 4858/2021. Any other use requires the explicit written permission of the Ministry of Culture."
  - **Not bundleable in an AGPL app**: a non-commercial clause is incompatible with free redistribution. Use it only as an outbound link through P13741, or ask the Ministry for written permission.
- **nationalarchive.culture.gr**: a portal for movable heritage (an Angular SPA, no open API found). Not a site gazetteer.
- **Odysseus** (odysseus.culture.gr): the old JSP portal (EU 3rd CSF project) with descriptions of museums, monuments and sites, a map and a chronology. It answers over HTTP only; HTTPS is refused. It is ISO-8859-7 HTML with no API, export or open licence. Not worth integrating.
- **data.gov.gr / geodata.gov.gr**: no dataset of archaeological sites or monuments was found. data.gov.gr returned 404 for a culture topic, and geodata.gov.gr did not answer (connection failed).
- **Diavgeia** (https://diavgeia.gov.gr/opendata/search.json): an open, keyless API for all government decisions, including designation acts. Results are metadata plus PDFs, without structured coordinates or dates. Usable only for provenance or NLP, not as a gazetteer.

## 4. Regional portals (argolisculture.gr and similar)

- argolisculture.gr is run by the Ephorate of Antiquities of Argolida (built by Space Hellas/Radiant, co-financed by the EU). It has 93 entries with type and period facets (Palaeolithic → Modern Greek State) and a map.
- It has no licence, API or download; the footer reads "© Tour Monuments of Argolis". Other ephorates run similar but inconsistent sites.
- **They are not needed.** All Argolis test cases are covered by Wikidata (and OSM). The portals are only useful as a manual check of coverage.

## 5. Ancient and Byzantine gazetteers

- **Pleiades** (CC BY 3.0, https://pleiades.stoa.org/credits; daily dumps at atlantides.org: places CSV 6.8 MB gz, JSON 136 MB gz). The CSV has minDate/maxDate/timePeriodsKeys/reprLat/Long/locationPrecision.
  - Greece-ish bbox (34.7–41.8N, 19.3–28.3E, so it includes W. Anatolia and S. Albania): 4,913 places, 4,616 with minDate.
  - Period keys: roman 2,760, hellenistic-republican 2,538, classical 2,267, archaic 1,050, late-antique 985. **But mediaeval-byzantine only 56, early/middle/late-byzantine 24/16/21, ottoman ~31.**
  - The modern/20th/21st-century keys (≈900) mark modern *names*, not site use.
  - So Pleiades is antiquity-centred, with only a token Byzantine and Ottoman layer. Its period vocabulary is in PeriodO (p03wskd).
- **DARE** (imperium.ahlfeldt.se): a GeoJSON API (`/api/geojson.php`) and RDF. The licence is reported inconsistently (CC BY-SA 3.0/4.0 vs CC BY), so treat it as CC BY-SA. Greek items are mostly already linked through Wikidata P1936 (1,283) and Pleiades. Low added value.
- **ARIADNE portal**: a metadata catalogue (4.17 M resources) dominated by the UK, Czech Republic and Scandinavia. A free-text search for "Greece" returns 157 resources; site records with a Greek country facet are a handful.
  - An undocumented JSON API exists (`portal.ariadne-infrastructure.eu/api/search`, `/api/getSearchAggregationData`); licence is per record. **Not a Greek site source.**
- **Europeana**: has no own place gazetteer (its entity API reuses GeoNames and Wikidata). Not useful.
- **Tabula Imperii Byzantini** (Dig-TIB, https://tib.oeaw.ac.at/digtib):
  - The toponym registers of TIB 1–13, 15 are search and PDF only, under Austrian Academy copyright with no bulk licence.
  - The TIB-Balkans data in the Maps of Power OpenAtlas database is stated to be **CC BY 4.0** (https://maps-of-power.oeaw.ac.at/projects/tib_balkan). It runs 2025–2030, and OpenAtlas has an API. Greek coverage there is Macedonia/Thrace (TIB 11, 12, 16) and partial.
  - Greek volumes such as TIB 1 Hellas/Thessalia, 3 Nikopolis/Kephallenia and 10 Aigaion have no open data. **The Peloponnese has no published TIB volume.**
- **Ottoman heritage**:
  - "Ottoman Heritage in Greece: Map and Bibliography" has ~600 georeferenced monuments; licence and API are unknown.
  - Kulturenvanteri has 758 Wikidata links; its licence is unverified.
  - Both should be reached through their Wikidata links, not imported.
- **kastra.eu (Greek Castles)** is a private site under copyright. Use only its P9951 ids.

## 6. Dating sources (to date undated sites)

- **PeriodO** (CC0; https://data.perio.do/d.json is a 7.9 MB zstd-served dump with 508 authorities). 34 authorities cover Greece or the Aegean (784 periods). The relevant ones:
  - **p0ds9qj British School at Athens, archaeological periods of Greece** (165 periods). It runs from the Palaeolithic through Late Antique 306–610, Early Byzantine 324–610, Middle Byzantine 610–1204, Late Byzantine 1204–1453, Venetian 1200–1650, Medieval/Frankish 323–1453, Ottoman 1453–1821 and Modern 1821–2000.
    - **This is the best single scheme for mapping OSM and Wikidata period strings.**
  - p0gtzvd Archaeology in Greece Online / Chronique des fouilles (85): the same scheme in French. Wikidata P8218 links its places.
  - p0qp9rs Cambridge Companion to the Aegean Bronze Age (55: EM/MM/LM, EH/MH/LH, EC/MC/LC phases).
  - p0qv6m8 DEFC (50, Neolithic/EBA with Lerna/Thessalian phases).
  - p03wskd Pleiades (43).
  - p0wz8jk Klassische Archäologie (43).
  - p0339m9 Hellenic History on the Internet (53, Greek labels).
- **Mapping OSM to PeriodO.**
  - `historic:civilization` values map to BSA periods: ancient_greek → BSA Archaic+Classical+Hellenistic (or Pleiades "classical"), mycenaean → Late Helladic, minoan → Minoan, byzantine → BSA Byzantine 324–1453, venetian → Venetian 1200–1650, ottoman → Ottoman 1453–1821, modern → Modern 1821–, neolithic/prehistoric → the matching BSA periods.
  - `medieval` is ambiguous in Greece. Map it to BSA "Medieval/Frankish 323–1453" and tag it as low-confidence.
  - `historic:period` values (classical_greece, archaic_greece, hellenistic_greece, roman_greece, greek_dark_ages) map one-to-one to BSA periods.
  - About 50 junk values (dates, typos) need a small alias table.
- **Mapping Wikidata to PeriodO.**
  - P2348 values are Wikidata period items (e.g. Mycenaean Greece, Byzantine period). Many PeriodO periods carry no Wikidata link, so a curated QID→PeriodO table of ~50–100 Greek periods is needed.
  - P571 with century precision is already handled by build.py.
- **Cadastre period vocabulary (20 terms)** is a useful checklist even though its data cannot be bundled.

## 7. Recommendation

**Minimal set for Greece:** Wikidata (extended) plus OSM (heritage filter) plus PeriodO BSA mapping. Pleiades and GeoNames stay as they are. Ministry and regional portals are links only.

1. **Extend the Wikidata extract first.** It is cheap, CC0, and closes the Argolis gap by itself.
   - Add roots: castle Q23413, fortification Q57821, monastery Q44613, church building Q16970, tower Q12518, bridge Q12280, aqueduct, city walls/gate, mosque and other religious buildings. For "monument/memorial", add only with P1435 or a date, to avoid modern memorials flooding in.
   - **Also select any item with P1435** whatever its class.
   - Keep P1435 (with P580 designation date as a qualifier) and **P2348**.
   - Keep ids P13741, P2186, P9951, P8218, P8068, P1936, P11693/P10689 (OSM).
   - Effect: about +10,900 Greek items, 36% dated.
   - Scales worldwide unchanged (2.1 M P1435 items worldwide), with paging.
2. **Add OSM as a separate, attributed ODbL layer.**
   - Take Geofabrik per-country or continent extracts, filtered with osmium.
   - Join to Wikidata through `wikidata=`, and keep OSM-only objects that have a name and a `historic=*` or `heritage=*` tag.
   - Include churches and monasteries only if they have a historic/heritage tag, a Wikidata match, or a name match. Otherwise 28k modern parish churches would swamp the layer. The Bermpaka case shows that the Wikidata join is how to catch these.
   - Scales worldwide unchanged (same filter on the planet or continents).
3. **Add a PeriodO alias table**: OSM `historic:civilization`/`historic:period` and Wikidata P2348 → BSA p0ds9qj (Greece) and Pleiades p03wskd (generic). Later, add per-region schemes from PeriodO for other countries.
4. **Per-country plugins, only where an open national register exists.** For Greece, none exists: the Cadastre is non-commercial and has no coordinates. Elsewhere this will vary (some national heritage registers are open). Design the plugin SPI so a national register can add designation and dating to existing Wikidata/OSM places by id rather than creating new places.

**Risks.**
- **Licence:**
  - The ODbL share-alike must not leak into the CC0 or CC BY tables, so keep OSM data separate or as links.
  - The Cadastre (non-commercial), argolisculture (©), kastra.eu (©) and TIB registers (©) cannot be bundled.
  - DARE's licence is unclear; treat it as CC BY-SA.
  - Pleiades is CC BY and needs attribution.
- **Size:**
  - Greece is trivial: the OSM extract is 342 MB raw and a few MB after filtering; Wikidata is tens of thousands of rows.
  - Worldwide, Wikidata P1435 alone is 2.1 M items, and OSM historic is several million objects (planet ~80 GB). Both need paging or streaming, and they enlarge the "undated" share unless undated OSM-only objects are kept out of the period views.
- **Quality:**
  - Duplicates (OSM node and way pairs, Wikidata items vs Pleiades vs OSM) need merging through wikidata and pleiades ids first, then proximity plus name.
  - Dating stays sparse: Wikidata designated items are 18% dated, OSM `historic:civilization` covers ~18% of historic objects, and both are coarse (era, not phase).
  - Wikidata P571 of 1200/1300 on Bermpaka shows century-precision values that build.py must expand to ranges, as it already does.
- **Access:**
  - overpass-api.de rejects curl's default UA (406), so do not depend on Overpass.
  - QLever moved to qlever.dev.
  - listedmonuments.culture.gr is gone.
