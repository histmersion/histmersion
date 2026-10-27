# Histmersion UI research: icons, map licences, Pleiades data

Research date: 2026-10-27. Nothing in any repo was edited or committed. Repo: this repository.

- Part A: event and site icons (this section)
- Part B: whether map licences must be displayed (written by a sub-agent, appended below)
- Part C: Pleiades place data (written by a sub-agent, appended below)

Rendered contact sheets (each icon at 48 px, 16 px, 14 px, and as a 16 px white glyph on a 20 px coloured badge, like a map marker):

- `icons/sheet-current.png`: the icons in use today
- `icons/sheet-cand-1.png`, `icons/sheet-cand-2.png`: every downloaded candidate (125)
- `icons/sheet-recommended.png`: one pick per kind, a mockup of the proposed set
- `icons/sheet-drafts.png`: rough custom glyphs I drew for concepts no free family covers

SVG files: `icons/current/` (40), `icons/candidates/` (125, named `<kind>__<family>-<icon>.svg`),
`icons/recommended/` (42, named `<kind>=<family>-<icon>.svg`), `icons/custom-drafts/` (6).

---

# Part A. Event and site icons

## A.1 How icons work today

- **Registry.** `modules/web/src/main/scala/com/histmersion/web/Glyphs.scala` holds 52 inline SVG icons. All but one
  are vendored **Lucide v0.460.0** (ISC; licence notice in
  `modules/server/src/main/resources/web/vendor/lucide/LICENSE.txt`). The exception is `alert-mark`, a hand-made
  exclamation mark for a site of unknown type. Icons are drawn with `currentColor` in a 24×24 box.
- **Map badges.** `Glyphs.badge` draws each icon's **strokes twice**: a wider dark halo, then the ink. This only works
  for stroke icons (`fill="none" stroke=…`). A filled icon (Material Symbols, MDI, Maki, Temaki, game-icons) would need
  a second code path, such as a halo made with `paint-order: stroke` on the fill path. This is the main technical reason
  to stay with a stroke family.
- **Event kinds.** `modules/api/shared/src/main/scala/com/histmersion/api/EventTaxonomy.scala` defines 26 kinds in two
  groups, natural and human. The marker is a square badge. The kind list is open: a kind outside the table gets its
  group's generic icon. The scaladoc records why some icons were chosen: a skull rather than biohazard, a plain ear of
  wheat rather than the struck-through one, and a scroll rather than a handshake, all for legibility at about 15 px.
  The contact sheets below suggest those three trade-offs no longer hold.
- **Site types.** `modules/api/shared/src/main/scala/com/histmersion/api/SiteTypes.scala` defines 14 types on a
  circle badge, mapped from Pleiades feature types, GeoNames codes and Wikidata classes.
- **Other glyphs** (`CardIcons.scala`, `PointKinds.scala`) cover source kinds (book, chapter, paper, thesis, dataset)
  and marker shapes (finding, site, event, picture, region, people). They are not map-kind icons and are outside the
  user's complaint. They look fine.

## A.2 Flaticon (the user's reference)

`https://www.flaticon.com/free-icons/tsunami` returned **HTTP 403** to automated access. Per the instructions I did not
work around it, and none of the Flaticon pages were consulted. Flaticon's licence requires attribution and does not
allow bundling, so it was never a source for the app.

As a substitute for "what people expect", I took the **convention shared across the 15 permissive families** returned
by the Iconify search API for each concept, plus the UN OCHA humanitarian hazard set (listed in A.6).

| Concept | Dominant convention across families | Evidence |
|---|---|---|
| eruption / volcano | Truncated cone with lava or ash bursting from the crater | Tabler, Material Symbols, MDI and Maki all draw it this way. A bare mountain is never used for it |
| tsunami | One large curling breaker, often over flat water lines, sometimes over a house (Font Awesome `house-tsunami`) | Material Symbols, MDI, Carbon, Bootstrap, Hugeicons, OCHA |
| drought | Water drop struck through, or cracked ground under a sun | Lucide/Tabler `droplet-off`, Carbon `drought`, OCHA (sun over cracked earth) |
| epidemic | Biohazard trefoil or a virus particle | Every family has both |
| earthquake | **House with a jagged crack**, or a seismograph trace | Remix `earthquake-line`, Carbon `earthquake` and OCHA use the cracked house. Material `earthquake` is a seismograph trace that looks the same as the current pulse |
| flood | House with its lower half under wave lines | Tabler `flood`, Material `flood`, MDI `home-flood`, Remix, Carbon |
| famine | Struck-through food: bread, wheat or plate | Tabler `bread-off`, Lucide `wheat-off`, Material `no-food`. OCHA uses an emaciated figure or empty bowl |
| revolt | Raised fist | Lucide/Phosphor `hand-fist`, game-icons `fist`/`revolt` |
| destruction | Broken or collapsed house, or house with an X | game-icons `damaged-house`, Tabler `home-x`, MDI `home-remove`, OCHA "house destroyed" |
| treaty | Handshake | All families |
| invasion | Arrow entering a house or crossing a border line | MDI `home-import-outline`. Few families have one |
| bridge | Arch or girder bridge | Lucide/MDI/Maki/Temaki `bridge`, Tabler `building-bridge-2` (an arch) |
| cave | Dark arched opening in a mound or rock face | Maki/Temaki `tunnel`, game-icons `cave-entrance` |
| shipwreck | Tilted or half-sunk ship | MDI `sail-boat-wreck`/`sail-boat-sink`, game-icons `ship-wreck`/`sinking-ship` |
| settlement | Cluster of 2–3 houses | Lucide `houses`, MDI `home-group`, Maki `village`, Temaki `row-houses` |

## A.3 Every kind: current icon, judgement, candidates

Licences: **Lucide ISC**, **Tabler MIT**, **Hugeicons (free set) MIT**, **Phosphor MIT**, **Bootstrap MIT**,
**Material Symbols / MDI / Remix / Carbon / MingCute / IconPark Apache-2.0**, **Maki / Temaki CC0**. These need at most a
single licence file in the distribution and no on-screen credit. **game-icons.net is CC BY 3.0**: it needs an author
credit per icon (one credits page satisfies it), so use it only as a fallback. **OpenMoji is CC BY-SA 4.0** and is
excluded. "S" marks a stroke icon that works with the current badge code. "F" marks a filled icon that needs a fill
halo.

Lucide note: the vendored copy is **v0.460.0**. `houses`, `bridge`, `droplet-off`, `hand-fist`, `virus`, `tombstone`
and `lighthouse` return 404 on `unpkg.com/lucide-static@0.460.0/icons/<name>.svg` and need an upgrade (the current
release is 1.62.0, still ISC). `wheat-off`, `biohazard`, `handshake`, `amphora`, `drama` and `tornado` are already in
0.460.0.

URL pattern: Lucide `https://lucide.dev/icons/<name>` · Tabler `https://tabler.io/icons/icon/<name>` · all others
`https://icon-sets.iconify.design/<prefix>/<name>/`. Sources: `https://github.com/lucide-icons/lucide`,
`https://github.com/tabler/tabler-icons`, `https://github.com/google/material-design-icons`,
`https://github.com/Templarian/MaterialDesign`, `https://github.com/rapideditor/temaki`,
`https://github.com/mapbox/maki`, `https://github.com/Remix-Design/RemixIcon`, `https://github.com/carbon-design-system/carbon`,
`https://github.com/hugeicons/hugeicons-react` (free set), `https://game-icons.net`.

### Natural events (square badge)

| kind | today | judgement | candidates, best first |
|---|---|---|---|
| eruption | `mountain` (bare peaks) | **Bad.** Reads as a mountain or a "terrain feature" glyph | 1. Tabler `volcano` (MIT, S): cone with three lava bursts, legible at 14 px. 2. Material Symbols `volcano-outline` (Apache, F). 3. MDI `volcano-outline` (Apache, F) |
| earthquake | `activity` (heart-rate pulse) | **Bad.** Reads as health or monitoring | The user wants seismic waves under a house, and no family draws that. 1. **Custom glyph**: house over a seismograph trace (rough draft `custom-drafts/earthquake__draft-house-seismic.svg`; needs a designer's pass). 2. Remix `earthquake-line` (Apache, F) or Carbon `earthquake` (Apache, F): cracked house. This is the cross-family convention, but then *destruction* needs a different glyph |
| tsunami | `waves` (three wavy lines, read as ♒ Aquarius) | **Bad** | 1. Hugeicons `tsunami` (MIT, S, 1.5 px stroke, so set it to 2): curling breaker over water. 2. Bootstrap `tsunami` (MIT, outline). 3. Material Symbols / MDI `tsunami` (Apache, F) |
| flood | `droplets` | **Weak.** Reads as rain or water | 1. Tabler `flood` (MIT, S): house with waves over its lower half, an exact match. 2. Material `flood-outline` (Apache, F). 3. MDI `home-flood` (Apache, F) |
| drought | `sun` | **Weak.** Reads as weather, and is a twin of `lightbulb`/`sparkles` at small size | 1. Lucide `droplet-off` (ISC, S, needs the upgrade). 2. Tabler `droplet-off` (MIT, S). 3. Carbon `drought` (Apache, F) |
| climate_event | `thermometer` | **OK** | Keep. Alternatives: Lucide `thermometer-sun`, `cloud-sun-rain` |
| epidemic | `skull` | **Weak.** Same glyph as death or burial; the user expects biohazard | 1. Lucide `biohazard` (ISC, S, already vendored). At 16 px the trefoil still reads, though the inner circle closes up at 14 px. 2. Tabler `biohazard` (MIT, S). 3. Tabler `virus` (MIT, S). At 14 px it is a twin of the sun, which is the reason the scaladoc gives for avoiding it |
| famine | `wheat` | **Weak.** Plain wheat reads as harvest or agriculture, the opposite meaning | 1. Lucide `wheat-off` (ISC, S, already vendored): the clearest "no food" at 14 px. 2. Tabler `bread-off` (MIT, S): the user's crossed-out bread, but the slice is a rounded square that reads poorly at 14 px. 3. A custom glyph (loaf plus slash) if bread is required |
| natural_disaster | `cloud-lightning` | **Weak.** Reads as storm | 1. Lucide `triangle-alert` (ISC, S): the generic hazard sign. It is distinct from the site badge's bare `alert-mark` only because of the triangle, so check both side by side. 2. MDI `home-alert-outline` (Apache, F). 3. Material `emergency-home-outline` (Apache, F). Consider adding **landslide** (Material/MDI `landslide`) and **storm** (Lucide `tornado`, Tabler `storm`) as sub-kinds |

### Human events (square badge)

| kind | today | judgement | candidates |
|---|---|---|---|
| battle | `swords` | **Good** | Keep |
| naval_battle | `sailboat` (triangular sail) | **Weak.** Reads as leisure sailing | 1. **Custom glyph**: an oared galley with a square sail (period-appropriate; rough draft `custom-drafts/naval_battle__draft-oared-galley.svg`). 2. Lucide `ship` once *shipwreck* moves off it. 3. Tabler `ship` (MIT, S). game-icons `trireme`/`galleon` (CC BY) carry too much detail to read at 14 px |
| siege | `castle` | **Collision**: the same icon as the *fortification* site | 1. game-icons `catapult` (CC BY 3.0, F). It is the only real siege engine, and borderline at 14 px. 2. **Custom**: castle with an arrow or ram. 3. game-icons `siege-tower` (CC BY) |
| war | `shield` | OK | Keep. Alternatives: Lucide `sword`, Tabler `sword` |
| revolt | `megaphone` | **Bad.** Reads as an announcement | 1. Lucide `hand-fist` (ISC, S, needs the upgrade). 2. Phosphor `hand-fist` (MIT, S). 3. game-icons `fist` (CC BY, F) |
| treaty | `scroll` | **Weak.** Same as a document | 1. Lucide `handshake` (ISC, S, already vendored); 16 px is fine. 2. Phosphor `handshake`. 3. Material `handshake-outline` |
| polity_formation | `crown` | OK, but **collides** with the *palace* site | Keep the crown here and change *palace* |
| collapse | `trending-down` | OK as an abstraction, but reads as a stock chart | 1. Temaki `ruins` (CC0, F): a broken colonnade, strong at 14 px. 2. Phosphor `chart-line-down`. 3. Keep |
| destruction | `flame` | **Weak.** Reads as fire; the user expects a broken house | 1. Remix `earthquake-line` or Carbon `earthquake` (Apache, F): a cracked house. This is only free if *earthquake* takes the custom house-and-trace glyph. 2. Tabler `home-x` (MIT, S). 3. game-icons `damaged-house` (CC BY, F, busy) |
| migration | `footprints` | **Good** | Keep. Carbon `migrate` is a software metaphor |
| invasion | `arrow-big-right` | **Weak.** Generic arrow | 1. MDI `home-import-outline` (Apache, F): an arrow entering a house, the user's request. 2. **Custom stroke version**: a curved arrow into a Lucide house (rough draft `custom-drafts/invasion__draft-arrow-into-house.svg`). 3. Tabler `home-move` (MIT, S), but its arrow points *out* of the house |
| technological_change | `lightbulb` | OK | Keep. Lucide `anvil` fits Bronze/Iron Age metallurgy |
| cultural_shift | `sparkles` | **Weak.** Reads as AI or "magic" in 2026 UIs | 1. Lucide `amphora` (ISC, S, vendored): archaeological cultures are defined by pottery. 2. Lucide `palette`. 3. Tabler `masks-theater` (collides with theatre) |
| era | `hourglass` | **Good** | Keep |
| construction | `hammer` | OK | Keep. Alternative: Lucide `brick-wall` |
| founding | `flag` | **Good** | Keep |
| event (generic) | `circle-dot` | OK | Keep |

### Site types (circle badge)

| type | today | judgement | candidates |
|---|---|---|---|
| settlement | `building-2` (modern office block) | **Bad.** Anachronistic | 1. Lucide `houses` (ISC, S, needs the upgrade; two houses). 2. MDI `home-group` (Apache, F; three houses, the user's request). 3. Maki `village` (CC0, F) |
| villa, house | `house` | **Good** | Keep. It must stay distinct from *settlement*: one house versus several |
| fortification | `castle` | OK (medieval look for hillforts) | Keep. Alternatives: Tabler `building-fortress`, game-icons `hill-fort` (CC BY, busy) |
| sanctuary, temple | `landmark` (a pedimented colonnade) | **Good** for a Greek temple | Keep. It must not also be used for *palace* |
| burial, tomb | `pyramid` | **Weak.** Egypt-specific; most burials in this corpus are tumuli, kurgans or cemeteries | 1. Lucide `tombstone` (ISC, S, needs the upgrade). 2. Tabler `grave-2` (MIT, S). 3. A **custom tumulus mound**. My draft (`custom-drafts/burial__draft-tumulus.svg`) reads as a dish cover and needs a designer's pass |
| mine, quarry | `pickaxe` | **Good** | Keep. Temaki `mineshaft-cage` is too modern |
| port, harbour | `anchor` | **Good** | Keep. Lucide `lighthouse` would suit a lighthouse sub-type |
| infrastructure (road, bridge, aqueduct) | `route` | **Weak.** One icon for three different things | **Split the type.** Bridge: 1. Tabler `building-bridge-2` (MIT, S; an arch, which suits the Arkadiko bridge). 2. Maki/Temaki `bridge` (CC0, F). 3. Lucide `bridge` (ISC, S; a modern girder bridge). Road: keep `route`, or use Tabler `road`. Aqueduct: game-icons `aqueduct` (CC BY, F; an arcade, excellent at 14 px) or a custom arcade |
| theatre, amphitheatre | `venetian-mask` | OK, but reads as carnival | 1. Lucide `drama` (ISC, vendored; comedy and tragedy masks). 2. Tabler `masks-theater` |
| find-spot, excavation | `shovel` | **Good** | Keep. Lucide `amphora` would fit a hoard or find |
| cave | `door-open` | **Bad.** A door | 1. Temaki `tunnel` (CC0, F): a mound with a dark arched mouth, the clearest cave at 14 px. 2. Maki `tunnel` (CC0, F). 3. A custom stroke version (draft `custom-drafts/cave__draft-mound-entrance.svg`). Tabler `building-tunnel` is a brick railway portal |
| palace | `crown` | **Collision** with polity formation | No good free icon: IconPark `palace` looks like a bank, and MingCute `palace-line` is a Chinese gate. Needs a **custom glyph**: a colonnade under a crenellated or crowned roof (rough draft `custom-drafts/palace__draft-colonnade-crown.svg`) |
| monument, megalith | `milestone` (a signpost) | **Bad.** Reads as directions | 1. Tabler `building-monument` (MIT, S; an obelisk on a plinth). 2. Temaki `obelisk` (CC0, F). 3. game-icons `menhir` or `dolmen` (CC BY, F) for megaliths |
| shipwreck | `ship` | **Weak.** An intact ship | 1. MDI `sail-boat-wreck` (Apache, F; a tilted hull and sail, legible at 14 px). 2. MDI `sail-boat-sink`. 3. game-icons `ship-wreck` (CC BY, too detailed) |
| unknown | `alert-mark` | OK | Keep, and check it against `triangle-alert` if that is adopted |

## A.4 Recommendation

1. **Stay with Lucide as the base, upgrade the vendored copy 0.460 → 1.x, and add Tabler as the second family.**
   Both are 24×24 grids with 2 px round-cap strokes and `fill="none"`. They are visually interchangeable and both work
   with the existing two-pass stroke badge. Lucide 1.x supplies `houses`, `droplet-off`, `hand-fist`, `handshake`,
   `biohazard`, `wheat-off`, `drama`, `amphora` and `tombstone`. Tabler supplies `volcano`, `flood`,
   `building-bridge-2` and `building-monument`. Licences: ISC plus MIT, each a one-file notice next to the existing
   `vendor/lucide/LICENSE.txt`.
2. **Add Hugeicons `tsunami` (MIT)** with its stroke width normalised to 2.
3. For the few glyphs only filled sets cover, use **CC0 (Temaki/Maki)** or **Apache (MDI)**: cave (Temaki `tunnel`),
   shipwreck (MDI `sail-boat-wreck`), collapse (Temaki `ruins`), invasion (MDI `home-import-outline`). Either add a
   fill-halo badge path, or redraw them as stroke icons, which is more consistent.
4. **Custom glyphs are needed** for earthquake (house over a seismic trace), naval battle (galley), palace, burial
   (tumulus), siege (unless the CC BY catapult is accepted) and possibly aqueduct. Drafts are in `icons/custom-drafts/`.
   They are rough sketches to show intent, not production artwork.
5. **Fix the collisions**: castle (siege and fortification), crown (polity formation and palace), and ship
   (shipwreck, and naval battle if Lucide `ship` is used there).
6. **Legibility at 14–18 px**: in `icons/sheet-recommended.png` the best on a badge are flood, volcano, droplet-off,
   wheat-off, hand-fist, handshake, houses, building-monument, sail-boat-wreck, ruins and tunnel. The weakest are
   biohazard at 14 px (draw markers at 16 px or more), the catapult (too detailed) and the Hugeicons tsunami (thin
   until the stroke is normalised).

## A.5 Concepts no free family covers well

Earthquake as "seismic waves over a house", a period-appropriate naval battle (galley or trireme) at marker size,
palace, tumulus/kurgan burial, siege at 14 px, and an aqueduct in a stroke style. Invasion and cave exist only as
filled icons (MDI, Temaki). The user's crossed-out bread exists only as Tabler `bread-off`, which reads poorly at
14 px.

## A.6 Other sets considered

- **UN OCHA Humanitarian Icons.** A complete hazard set: earthquake, tsunami, flood, flash flood, drought, epidemic,
  famine, volcano, landslide, conflict, house destroyed, bridge destroyed, population movement. A mirror is at
  `https://rodekruis.github.io/OCHA-Humanitarian-Icons/` (the page states no licence), and the canonical source is
  `https://brand.unocha.org`. They are widely described as public domain, but **I could not verify the licence**:
  reliefweb returned HTTP 403, and the GitHub mirror has no LICENSE file. They are filled pictograms in a different
  style. Treat them as a design reference unless OCHA's terms are confirmed.
- **Font Awesome Free** (`house-tsunami`, `house-flood-water`, `house-chimney-crack`, `volcano`, `hand-fist`) has a
  very close match for several concepts. Its icons are CC BY 4.0, so they need attribution; it is a fallback only.
- Not used: **game-icons.net** (CC BY 3.0, per-author credit; detailed silhouettes meant for 64 px), **OpenMoji**
  (CC BY-SA, coloured emoji style), **Streamline** (CC BY 4.0 on Iconify).

---

# Part B: Map and data licences. Is displaying attribution legally required?

Research date 2026-10-27. Repo: this repository (read only, nothing changed).
Not legal advice. The quotes are from the licence texts fetched today, and the URL is given for each.
Pages that refused automated access (HTTP 403) are marked as such and were not worked around.

## 0. The short answer

* **Yes, when the map is shown to anyone other than the operator.** ODbL (OpenStreetMap, Protomaps,
  Daylight, AWMC) and CC BY (GeoNames, Pleiades, Cliopatria, ESA WorldCover, the CC BY sources inside
  Mapterhorn) both require a notice once the map is used **"Publicly"** (ODbL) or **"Shared"** (CC BY).
  For a single-user `localhost` install neither licence requires anything (ODbL §4.5c: internal use
  "is not to the public"). The software is AGPL and public, though, and any operator who exposes a
  server inherits the duty. So the UI should comply by default.
* **The OSMF tile policy applies even in private use.** The online "Standard" and "Dark" maps fetch
  `tile.openstreetmap.org`, and the tile usage policy makes visible attribution a condition of using
  that server at all, public or not.
* **Where the credit may go:** in a corner of the map. It may collapse **after** the user
  interacts with the map or after 5 s, but it must be visible at first without any interaction. After
  that it must stay reachable through an (i) button or an "About" entry. A separate credits page alone
  is **not** enough for a rendered map. It **is** enough, under CC BY's "reasonable manner" clause, for
  datasets that are not drawn as the base map, as long as the map's credit line links to it.

## 1. Sources: what is used, under which licence, and what is shown today

Shorthand for the "shown today" column. **Foot** is the map's credits box at bottom-left (`BaseMaps.drawFoot`,
`.map-credits`). On a wide layout it is always visible. In the narrow layout it is **folded behind an ⓘ
button by default** (`footOpen.getOrElse(notice.exists(_._2))` is false unless the map is not
downloaded). The foot's text is each drawn MapLibre source's `attribution`, taken from
`modules/config/src/main/resources/reference.conf` `basemap.*.attribution`, or `BaseMaps.raster` for the
online maps. **Card** is the place or event hover card (`Preview.datasetOf`, the `Catalogue` section,
`creditLine`). **NOTICE** is the repo's `NOTICE` and `THIRD-PARTY.md` files (not shown in the UI). **Docs**
is `docs/user-guide/how-to/open-data.md` and `base-maps.md`.

| Source | Used for (where in code) | Licence | Attribution legally required? | Quoted clause + URL | Shown today where |
|---|---|---|---|---|---|
| **OpenStreetMap raster tiles** (`tile.openstreetmap.org`, "Standard" and "Dark" online maps) | `ui.tiles` default `https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png` (reference.conf:201); `BaseMaps.raster`; `UiState.BaseMap` | ODbL 1.0 data; tiles under the OSMF Tile Usage Policy | **Yes.** Required by ODbL §4.3 when used publicly, and by the tile policy for **any** use | Tile policy: *"Show OpenStreetMap licence attribution clearly on the map (typically bottom-right)"*. The policy also says attribution should not be hidden beneath UI, behind toggles or off-screen (WebFetch paraphrase). It also says *"Offline use is not permitted on `tile.openstreetmap.org`"*, *"Other subdomains or hostnames may be slower or withdrawn without notice"* and *"We may block access, without notice, if your usage degrades the service."* <https://operations.osmfoundation.org/policies/tiles/> | Foot: `© OpenStreetMap contributors (ODbL)`, linked to /copyright (`BaseMaps.scala:108`). Good text. **Folded on narrow screens.** |
| **OSM France "Humanitarian" tiles** ("Muted" online map) | `UiState.scala:175` `https://{s}.tile.openstreetmap.fr/hot/…` | ODbL data; OSM-FR terms | **Yes**, under OSM-FR's terms | *"directement visible dans le coin de la carte avec un lien pointant sur https://osm.org/copyright"* (the credit must be directly visible in the map's corner, linking to the OSM copyright page). Abusive use may be throttled or blocked. <https://www.openstreetmap.fr/fonds-de-carte/> | Foot: OSM line plus `tiles © Humanitarian OSM Team, OSM France`. Folded on narrow. |
| **Protomaps basemap build** (OSM + Natural Earth + Daylight landcover) as the "Readable"/"Physical" vector layer, downloaded as a filtered extract and also read on demand | `basemap.vector` (reference.conf:1600–1630), style sources `osm`, `lc` in `web/styles/{readable,physical}.json` | ODbL 1.0 (OSM). Protomaps docs footer: *"released under the BSD and ODbL licenses"* | **Yes** (ODbL §4.3 Produced Work). **Share-alike applies too**: see §4 | ODbL §4.3, quoted in §2 below. OSMF guideline: *"Attribution must be to “OpenStreetMap”. Attribution must also make it clear that the data is available under the Open Database License. This may be done by making the text “OpenStreetMap” a link to openstreetmap.org/copyright"*. <https://osmfoundation.org/wiki/Licence/Attribution_Guidelines> · <https://docs.protomaps.com/basemaps/layers> | Foot: `© OpenStreetMap (ODbL), Natural Earth, Protomaps`. Compliant text. |
| **Daylight landcover → ESA WorldCover 2020** (the `lc` source, Protomaps `landcover` layer z0–7) | `physical.json`/`readable.json` source `lc`. `MapLibreEngine.scala:621` **deliberately skips `lc`'s attribution** (`id != "lc"`) | Daylight: ODbL. Upstream ESA WorldCover: **CC BY 4.0** | **Probably yes** (CC BY 4.0 §3(a), with ESA's requested line) | Protomaps: *"Polygons from the Daylight distribution's landcover theme, for z0-z7."* Daylight says the dataset is *"derived from the European Space Agency's 2020 World Cover (10m) rasters"*. ESA asks for: *"© ESA WorldCover project [year] / Contains modified Copernicus Sentinel data ([year]) processed by ESA WorldCover consortium"*. <https://docs.protomaps.com/basemaps/layers> · <https://daylightmap.org/2023/10/11/landcover.html> · <https://esa-worldcover.org/en/data-access> · AWS registry: *"License: CC-BY 4.0"* <https://registry.opendata.aws/esa-worldcover-vito/> | **Not shown anywhere** (not in the foot, not in docs, not in NOTICE). **Gap.** |
| **Mapterhorn terrain** (Copernicus GLO-30 + ~80 national DEMs) | `basemap.terrain` (`download.mapterhorn.com/planet.pmtiles`), style source `dem` | Per source: Copernicus "full, free and open"; many sources **CC BY 4.0**, plus CC BY 3.0 / 2.5, OGL-UK, OGL-Canada, Datenlizenz DE, Licence Ouverte FR. Mapterhorn states no overall licence | **Yes**, for the Copernicus notice and each CC BY / OGL producer | Mapterhorn's `attribution.json`: glo30 is *"COPERNICUS full, free and open license"*, producer *"DLR e.V. 2010-2014, Airbus Defence and Space GmbH 2014-2018 … provided under COPERNICUS by the European Union and ESA. All rights reserved."* CC BY 4.0 entries include Geoscience Australia, BEV Austria, Kartverket, LINZ and others. <https://download.mapterhorn.com/attribution.json> · <https://mapterhorn.com/attribution> | Foot: `terrain © Mapterhorn (Copernicus DEM © DLR e.V., Airbus DS, EU/ESA, and others)`, linked to mapterhorn.com/attribution. Acceptable: CC BY 4.0 §3(a)(2) lets the credit be *"a URI or hyperlink to a resource that includes the required information"*. |
| **Mapzen/Tilezen terrain tiles → ETOPO1** (default bathymetry, "sea floor") | `basemap.bathymetry` (`s3.amazonaws.com/elevation-tiles-prod/terrarium`), style source `sea` | ETOPO1 is a US Government work (public domain). The tile set mixes sources, some of them CC BY (land areas) | ETOPO1: **courtesy only**. Sources under CC BY / OGL: required **if their pixels are drawn** (they are land-only, so probably not in a sea-only layer) | Joerd: *"Global ETOPO1 terrain data U.S. National Oceanic and Atmospheric Administration"* (public domain in the US). Also lists e.g. *"© Commonwealth of Australia (Geoscience Australia) 2017."* (CC BY 4.0) and *"© Kartverket"* (CC BY 4.0). <https://github.com/tilezen/joerd/blob/master/docs/attribution.md> | Foot: `sea floor: ETOPO1 (NOAA, public domain) via Mapzen Terrain Tiles`. Fine. Adding a link to joerd's attribution page would be a cheap safeguard. |
| **GEBCO_2026 Grid** (optional bathymetry, `source = gebco`) | `basemap.bathymetry.gebco` | Public domain, plus GEBCO's terms of use and disclaimer | **Requested ("we ask")**, so courtesy rather than a licence condition. Using the grid accepts GEBCO's terms | *"The GEBCO Grid is placed in the public domain and may be used free of charge."* *"Use of the GEBCO Grid indicates that the user accepts the conditions of use and disclaimer information."* *"If the data sets are used in a presentation or publication then we ask that you acknowledge the source."* <https://www.gebco.net/data-products/gridded-bathymetry-data> | Foot (when selected): the full GEBCO citation with DOI. |
| **Natural Earth** (physical labels layer; bundled places 01/02/04; `land` clip) | `basemap.labels`, `db/places/0{1,2,4}-*.json` | Public domain | **No** (courtesy) | *"All versions of Natural Earth raster + vector map data found on this website are in the public domain."* *"No permission is needed to use Natural Earth. Crediting the authors is unnecessary."* <https://www.naturalearthdata.com/about/terms-of-use/> | Foot: `names: Natural Earth (public domain)`. Card: place `source`. NOTICE. |
| **Pleiades** (34 k bundled ancient places; labels, points, place cards; `media-pleiades`) | `db/places/03-pleiades.json`, drawn as map labels and points | **CC BY 3.0** | **Yes** when shown publicly. CC BY 3.0 §4(b) allows it *"in any reasonable manner"* | *"Using, sharing, and remixing of the content is permitted under terms of the Creative Commons Attribution 3.0 License (cc-by)."* <https://pleiades.stoa.org/credits>. CC BY 3.0 §4(b): *"…keep intact all copyright notices for the Work and provide, reasonable to the medium or means You are utilizing: (i) the name of the Original Author … (ii) the title of the Work if supplied; (iii) to the extent reasonably practicable, the URI … The credit required by this Section 4(b) may be implemented in any reasonable manner"* <https://creativecommons.org/licenses/by/3.0/legalcode> | Card: `Pleiades (pleiades.stoa.org), CC-BY` plus a link to the Pleiades page. NOTICE, Docs. **Not in the map foot.** |
| **GeoNames** (user-built extract, 1.6 M places) | `db/gazetteer/extracts.json`, `places load geonames` | **CC BY 4.0** | **Yes** when shown publicly | GeoNames: data *"available for download free of charge under a creative commons attribution license"*, and the footer cites CC BY 4.0. <https://www.geonames.org/about.html>. CC BY 4.0 §3(a), quoted in §2 | Card (import source plus a link to the GeoNames page). Pin metadata. Docs. **Not on the map.** |
| **Getty TGN** (user-built extract) | `extracts.json` `tgn` | **ODC-By 1.0** | **Yes** when shown publicly (ODC-By §4.3 Produced Work notice, worded like ODbL's) | Getty's credit line form (search result, from Getty's Data & Services page): *"Contains information from the J. Paul Getty Trust, Getty Research Institute, [full tool name], which is made available under the ODC Attribution License."* <https://www.getty.edu/research-conservation/tools-databases/vocabularies/data-services> (the page fetch came back truncated; the wording is quoted from the search result) | Pin `attribution` (*"Contains information from the Getty Thesaurus of Geographic Names (TGN)®, which is made available under the ODC Attribution License (ODC-By 1.0)"*). Card shows the dataset. Not on the map. Getty's preferred wording also names *"J. Paul Getty Trust, Getty Research Institute"*. |
| **Wikidata** (places extract, curated coordinates, events, media lookup) | `extracts.json`, `05-curated.json`, `events import wikidata` | **CC0** | **No** | *"is released into the public domain under Creative Commons Zero"* <https://www.wikidata.org/wiki/Wikidata:Licensing> | Card, Docs, NOTICE. |
| **PeriodO** (period presets) | `db/periods/periodo-seed.json` | CC0 / public domain | **No** | *"PeriodO is a public domain gazetteer of scholarly definitions…"* <https://perio.do/en/> | NOTICE, Docs. |
| **Seshat Cliopatria** (~14 k polity polygons drawn on the map by period) | `CliopatriaImporter.scala` (fetches `github.com/Seshat-Global-History-Databank/cliopatria`) | **CC BY 4.0** per the repo's `LICENSE.md`. **Histmersion labels it "CC BY-NC-SA"**, which is wrong but errs on the strict side | **Yes** when shown publicly | `LICENSE.md`: *"This data is licensed under a Creative Commons Attribution 4.0 International License … as long as you give appropriate credit to the original author(s) and the source, provide a link to the Creative Commons license, and indicate if changes were made."* <https://github.com/Seshat-Global-History-Databank/cliopatria/blob/main/LICENSE.md>. Paper: doi:10.1038/s41597-025-04516-9 | Card: `Cliopatria (Seshat Databank), CC BY-NC-SA`. **Polygons are on the map without any credit in the foot.** |
| **AWMC regional names** (classical-world names, map labels) | `AwmcImporter.scala` | **ODbL 1.0**. The data is derived from the Barrington Atlas and *"uses AWMC modifications to OpenStreetMap"* | **Yes** (ODbL §4.3). **Share-alike may apply** (§4) | AWMC README: *"The GeoJson files are offered under the ODC Open Database License … Data is derived from the Barrington Atlas … and uses AWMC modifications to OpenStreetMap"* <https://github.com/AWMC/geodata> | Card: `Ancient World Mapping Center (UNC), ODbL`. Not on the map. |
| **OpenHistoricalMap** (21 polity outlines in `db/places/polities`) | bundled | OHM: **CC0**, *"unless otherwise noted"* per feature (`license=*` tags). The repo calls them ODbL | **No** for CC0 features (OHM requests credit). Required for any feature tagged CC BY / BY-SA / ODbL | *"OpenHistoricalMap data is dedicated to the public domain under a Creative Commons CC0 dedication."* Suggested credit: *"Map data courtesy of the OpenHistoricalMap project, in the public domain unless otherwise noted."* <https://www.openhistoricalmap.org/copyright> | NOTICE and polities README. Each feature's `source`. |
| **ChronOntology** (periods, on request) | `ChronOntologyImporter` | "open access, as published by the DAI" (not verified) | Unclear. Treat as courtesy plus citation | (not fetched) | Docs. |
| **NOAA NCEI hazards** (earthquakes, tsunamis, volcanoes as events) | `events import noaa` | US Government work (17 U.S.C. §105), public domain | **No** (citation requested) | NCEI page returned no use statement. Docs cite doi:10.7289/V5TD9V7K. <https://www.ncei.noaa.gov/products/natural-hazards/tsunamis-earthquakes-volcanoes> | Event card `Catalogue … · licence · cite:`. Docs. |
| **Smithsonian GVP** (Volcanoes of the World events) | `events import gvp` | Smithsonian Terms of Use. Repo docs: "non-commercial use with attribution" | **Yes** (a terms-of-use condition, not a CC licence) | `volcano.si.edu` and `si.edu/termsofuse` returned **HTTP 403** to automated fetch. Not worked around, so this is unverified | Event card shows licence and citation. Docs. |
| **Smithsonian Open Access** media (off by default) | `media-smithsonian` | CC0 (only CC0 is stored) | **No** | (si.edu returned 403. CC0 per the repo docs) | Picture card credit line. |
| **Europeana** media (off by default) | `media-europeana` | Per item: CC0, PDM, CC BY, CC BY-SA | **Yes, per item** for CC BY / BY-SA | pro.europeana.eu returned **403** (not worked around) | Picture card `creditLine` (credit, licence linked). |
| **Wikimedia Commons** media (`media-wikidata`, `depicts`, `pleiades`) | per file | Per file: PD, CC0, CC BY, CC BY-SA | **Yes, per file** for BY / BY-SA: author, licence, link | *"If the uploader is not the content creator, it is the content creator who must be credited"*, *"If the license requires you to link to or provide a copy of the license, do this."* <https://commons.wikimedia.org/wiki/Commons:Reusing_content_outside_Wikimedia> | Picture card: credit plus licence linked to its page. Compliant pattern. |
| Met / Cleveland / AIC / LoC / Open Context media | per record | CC0, PD, "no known restrictions", or per-item CC (Open Context) | No for CC0 and PD. **Yes** for Open Context CC BY items | (not fetched) | Picture card credit line. |
| **Lucide icons** | `Glyphs.scala` (inline SVG) | ISC (part MIT) | Notice in copies of the software. **Not a UI requirement** | (ISC: the notice is included in copies) | NOTICE + `vendor/lucide/LICENSE.txt`. |
| **Noto Sans** | `web/vendor/noto-sans` | SIL OFL 1.1 | The licence travels with the font files. **Not a UI requirement** | n/a | NOTICE + `OFL.txt`. |
| **MapLibre GL, maplibre-contour** | vendor | BSD-3 | Notice in redistributions. **Not a UI requirement** (MapLibre has no logo requirement) | n/a | NOTICE. |

These are **not used**: OpenFreeMap, OpenMapTiles, Esri, Stadia, CARTO, MapTiler, aourednik historical-basemaps, CShapes, ToposText. None of them appears anywhere in the code or the docs.

## 2. The licence texts that decide placement

**ODbL 1.0** (<https://opendatacommons.org/licenses/odbl/1-0/>)

* §4.3: *"Creating and Using a Produced Work does not require the notice in Section 4.2. However, if you
  Publicly Use a Produced Work, You must include a notice associated with the Produced Work reasonably
  calculated to make any Person that uses, views, accesses, interacts with, or is otherwise exposed to the
  Produced Work aware that Content was obtained from the Database … and that it is available under this
  License."* Example notice: *"Contains information from DATABASE NAME, which is made available here under
  the Open Database License (ODbL)."*
* §4.2 (conveying the database itself): *"Include a copy of this License … or its Uniform Resource
  Identifier (URI) with the Database or Derivative Database … and in any relevant documentation; … d. If
  it is not possible to put the required notices in a particular file … You must include the notices in a
  location (such as a relevant directory) where users would be likely to look for it."*
* "Publicly": *"means to Persons other than You or under Your control…"*. §4.5c: *"Use of a Derivative
  Database internally within an organisation is not to the public."* "Convey" *"does not include
  interaction with a user through a computer network, or creating and Using a Produced Work"*.
* §4.4: *"a. Any Derivative Database that You Publicly Use must be only under the terms of: i. This
  License … c. … A Derivative Database is Publicly Used and so must comply with Section 4.4. if a Produced
  Work created from the Derivative Database is Publicly Used."*
* §4.6: *"If You Publicly Use a Derivative Database or a Produced Work from a Derivative Database, You must
  also offer to recipients … a copy in a machine readable form of: a. The entire Derivative Database; or
  b. A file containing all of the alterations made to the Database or the method of making the alterations
  to the Database (such as an algorithm) … free of charge if distributed over the internet."*
* §4.5a: Collective Databases need not be ODbL, *"but this License still applies to this Database or a
  Derivative Database as a part of the Collective Database"*.

**OSMF Attribution Guidelines** (adopted 2021-06-25, <https://osmfoundation.org/wiki/Licence/Attribution_Guidelines>), safe harbour

* *"The attribution format should not require individuals to interact with the map or produced work to
  see the attribution. Attribution must be placed in the vicinity of the produced work or in a location
  where customarily attribution would be expected … must be legible … The text may appear at the same time
  as, or next to, other attributions. There needs to be a way to access more information, including origin
  and licence of the data … (for example by making the text a clickable link)."*
* Text: *"The historical forms of attribution “© OpenStreetMap contributors” or “© OpenStreetMap” are
  acceptable."* You may qualify it, e.g. *"Map data from OpenStreetMap."*
* Interactive maps: *"the credit should typically appear in a corner of the map. While the lower right
  corner is traditional, any corner of the map is acceptable. Alternatively, the attribution may be placed
  adjacent to the map or on a splash screen or pop-up shown when a user starts the app … You may use a
  mechanism to fade/collapse the attribution under certain conditions: immediately with a dismiss
  interaction, for example clicking an ‘x’ … automatically on map interaction such as panning, clicking,
  or zooming … automatically after five seconds … If the attribution has been collapsed, the user must
  still be able to find the licence information if they look for it, for example from an ‘(i)’ button in
  the corner of the map or an ‘About’ option in a menu."* *"If attribution is presented to the user upon
  application startup, it does not need to be presented … every time."*
* The guideline does not cover hover-only or mobile cases specifically. Hover-only is **not** in the safe
  harbour, because it requires interaction to see the credit.
* Databases: *"You must include attribution to OpenStreetMap and either the text of the ODbL or a link to
  it … in a location (such as a relevant directory) where users would be likely to look for it, such as a
  readme file, or within the data or metadata."*
* Geocoding: *"Applications that incorporate such a geocoder must credit OpenStreetMap"*.
* The guidelines *"are not a substitute for the legal text"*. They are a safe harbour, not the
  legal minimum.

**openstreetmap.org/copyright**: *"Provide credit to OpenStreetMap by displaying our attribution notice"*
and *"Make clear that the data is available under the Open Database License."* Placement varies
*"depending on whether you have created a browsable map, a printed map or a static image"*.

**CC BY 4.0 §3(a)** (<https://creativecommons.org/licenses/by/4.0/legalcode>; CC BY-NC-SA 4.0 has the
same text). *"If You Share the Licensed Material (including in modified form), You must: retain … identification
of the creator(s) … a copyright notice; a notice that refers to this Public License; a notice that refers to
the disclaimer of warranties; a URI or hyperlink to the Licensed Material to the extent reasonably
practicable; indicate if You modified the Licensed Material …; and indicate the Licensed Material is licensed
under this Public License, and include the text of, or the URI or hyperlink to, this Public License. You may
satisfy the conditions in Section 3(a)(1) in any reasonable manner based on the medium, means, and context in
which You Share the Licensed Material. For example, it may be reasonable to satisfy the conditions by
providing a URI or hyperlink to a resource that includes the required information."*

**CC0**: a waiver, so no attribution is required (<https://creativecommons.org/publicdomain/zero/1.0/legalcode>).

## 3. Placement rules (what may go where)

| What | Legally required where | Minimum |
|---|---|---|
| OSM (online tiles, Protomaps vector, Daylight) | **On the map, in a corner, visible without interaction**. It may collapse after the first map interaction or after 5 s, then stay reachable through an (i) button or About | `© OpenStreetMap` linked to `https://www.openstreetmap.org/copyright` |
| OSM-FR HOT tiles | *"directement visible dans le coin de la carte"*: no collapse is mentioned, so keep it visible | Same, plus `tiles © OSM France / HOT` |
| tile.openstreetmap.org | Policy: on the map and not hidden behind toggles. Stricter than the OSMF guideline, so **keep it visible for the online Standard and Dark maps** | Same |
| Mapterhorn / Copernicus / CC BY DEMs, ESA WorldCover | CC BY "reasonable manner based on the medium": a short credit in the map's credit line that **links** to a page listing everything (Mapterhorn's attribution page, or Histmersion's own credits page) | `terrain: Mapterhorn` (linked) · `land cover: ESA WorldCover` (linked) |
| Pleiades, GeoNames, Cliopatria (CC BY); TGN (ODC-By); AWMC (ODbL) | Drawn as labels, points and polygons over the map, so they are Produced Work / Shared material. Under CC BY a **linked "Data sources" entry in the credit line, pointing to a credits page**, is a reasonable manner. For ODbL/ODC-By §4.3 the notice must be "associated with the Produced Work" and reasonably calculated to make viewers aware. A link labelled e.g. "Places: Pleiades, GeoNames, Getty TGN, AWMC (ODbL), Cliopatria …" next to the OSM credit meets that. A credits page with **no link from the map** is weak for ODbL/ODC-By | One extra linked item in the credit line, plus a credits page |
| The same datasets used only in search, geocoding or the database (not drawn) | No on-map notice. A credits/About page or the docs is enough (OSMF "Databases": *"a readme file, or within the data or metadata"*; the place card's per-row `source` already exceeds that) | Credits page |
| Public domain (Natural Earth, ETOPO1, NOAA, GEBCO, PeriodO, Wikidata, OHM CC0) | Nothing legally. GEBCO and USGS *ask* | Credits page (keep GEBCO in the line when it is drawn; it is cheap) |
| Pictures (Commons, Europeana, Open Context CC BY/BY-SA) | **Next to each picture** (TASL: title, author, source, licence). The current card credit line does this | Unchanged |
| Software (Lucide, Noto, MapLibre, libraries) | In the distribution (NOTICE, licence files), not in the UI. An About-window link to NOTICE is courtesy | About: "Open-source licences" link |

## 4. Minimum compliant design for the map UI

1. **An always-visible credit chip in a map corner** (bottom-left or bottom-right), short and legible
   (WCAG contrast; the current `--muted` 11–12 px on the dark panel should be checked):
   `© OpenStreetMap` (linked to /copyright) · plus the drawn layer's own short credits
   (`Protomaps`, `Mapterhorn`, `ESA WorldCover`, `ETOPO1`/`GEBCO`) · `Data sources` (a link to the credits
   page). Build it from the drawn sources, as `MapLibreStyles.credits` already does.
2. **Collapse is allowed only after it has been seen.** On narrow screens, show it expanded on first
   load and fold it to the ⓘ button **on the first pan or zoom, or after 5 s**. Never start folded. Once
   folded, the ⓘ re-opens it. For the online Standard and Dark maps (`tile.openstreetmap.org`) and Muted
   (OSM-FR), keep at least `© OpenStreetMap` permanently visible, because both tile servers' own terms
   rule out hiding it.
3. **The "Data sources" credits page** (or an About-window section) lists every dataset with its licence,
   link and the credit line it asks for. Generate it from config and DB metadata so it never drifts:
   `basemap.*.attribution`, the gazetteer source table's `citation` (V071), `extracts.json`, the importers'
   `Source` strings, the events catalogues and NOTICE. It should include:
   * the ODbL text link, plus AWMC and OSM as ODbL;
   * Getty in its own preferred form;
   * ESA WorldCover's line;
   * the Copernicus DEM line, with a link to Mapterhorn's attribution JSON;
   * GEBCO's citation;
   * Pleiades CC BY 3.0, GeoNames CC BY 4.0, Cliopatria CC BY 4.0 with its paper DOI;
   * the OHM courtesy line;
   * Natural Earth, NOAA and PeriodO as courtesy.
   Also link NOTICE and THIRD-PARTY for the software.
4. **Leave the per-place and per-picture credits as they are.** Card `source` and `creditLine` already go
   beyond what is required.
5. In the **docs and screenshots**, keep the credit box visible in map screenshots. The OSMF guideline treats
   static images like interactive maps, with one credit per document.

## 5. Compliance risks found today

1. **Narrow layout hides all credits behind ⓘ from the start**
   (`BaseMaps.drawFoot`: `open = footOpen.getOrElse(notice.exists(_._2))`, and
   `[data-layout="narrow"] .map-foot:not(.is-open) > .map-foot-body { display:none }`).
   * This is outside the OSMF safe harbour, which says the format *"should not require individuals to
     interact … to see the attribution"*.
   * It breaches the tile policy (on-map, not behind toggles) and OSM-FR's *"directement visible"* for
     the online maps.
   * **Severity: high for any public or mobile instance**, and it breaches the tile policy even in
     private use.
2. **ESA WorldCover (via Daylight / Protomaps `landcover`) is never credited.**
   * `MapLibreEngine.resolve` drops `lc`'s attribution. The vector credit names OSM, Natural Earth and
     Protomaps only.
   * The CC BY 4.0 credit that ESA asks for (*"© ESA WorldCover project 2020 / Contains modified Copernicus
     Sentinel data (2020) processed by ESA WorldCover consortium"*) is missing.
   * Daylight relicenses the layer as ODbL, so the strict legal need is debatable.
   * **Severity: medium.** The fix is cheap.
3. **Gazetteer and polity layers drawn on the map have no on-map credit.** This covers Pleiades CC BY 3.0,
   GeoNames CC BY 4.0, Cliopatria CC BY 4.0, AWMC ODbL and Getty ODC-By.
   * They are credited only per place, on hover cards.
   * For CC BY that is arguably "reasonable". For ODbL/ODC-By §4.3 (AWMC, TGN) a viewer of the map is
     not made aware.
   * **Severity: medium for public instances.** Fix it with the linked "Data sources" item.
4. **ODbL share-alike on derivative databases.**
   * (a) The thinned Protomaps extract (`basemap.vector.drop`) and on-demand cache is a Derivative
     Database of OSM. Rendering it publicly makes it "Publicly Used" (§4.4c), so it must be under ODbL,
     and §4.6 requires offering the database or *"the method of making the alterations … (such as an
     algorithm)"*. The AGPL code, the public `drop` config and the upstream build key satisfy §4.6(b).
     The fix is to **state this on the credits page**: "the base-map extract is an ODbL derivative of
     OpenStreetMap via Protomaps build X, filtered by `basemap.vector.drop = …`".
   * (b) AWMC (ODbL) rows imported into the `places` table and joined to Pleiades/Wikidata by id. If a
     public instance exposes the gazetteer as data, the AWMC-derived part must stay ODbL. It must not be
     relicensed, e.g. as CC0 in an LPF export or a bundle, and an OSMF-style notice must ship with it.
     Keep AWMC rows marked, so the public database is a Collective Database (§4.5a) and only those rows
     carry ODbL.
   * (c) `db/places/polities` says its 21 OHM outlines are "ODbL". OHM is CC0 unless a feature says
     otherwise. Check each feature's `license` tag; over-labelling is harmless.
5. **Licence mislabels** (they err strict, so they are not violations, but they are wrong data):
   * Cliopatria is **CC BY 4.0**, not "CC BY-NC-SA" (`CliopatriaImporter.scala:12,23,26`,
     `docs/user-guide/how-to/open-data.md`). The NC label may be making the app or users avoid
     legitimate commercial reuse.
   * The Getty credit omits "J. Paul Getty Trust, Getty Research Institute" from Getty's own wording.
6. **OSM tile policy, other points.**
   * The default `ui.tiles` uses the `{s}.` (a/b/c) subdomains. The policy says *"Other subdomains or
     hostnames may be slower or withdrawn without notice"*, and the wiki lists `https://tile.openstreetmap.org/{z}/{x}/{y}.png`.
   * The policy recommends making the URL configurable. It is: `ui.tiles` / `HISTMERSION_TILES`.
   * The browser sends the tile requests, so Referer and User-Agent are the browser's own, and that is
     fine. The policy forbids a `Referrer-Policy` that strips Referer, and no such policy was found in
     the server code.
   * The online maps must never be cached or prefetched server-side for offline use, because the policy
     says *"Offline use is not permitted"*. Today only the local Protomaps/Mapterhorn extracts are cached,
     so this is fine.
7. **Unverified:** Smithsonian GVP terms (si.edu and volcano.si.edu returned 403) and Europeana's rights
   page (403). The repo's own description is "non-commercial use with attribution" for GVP. The event
   card shows the citation, so GVP is probably fine. A public commercial deployment would breach the
   non-commercial term.

---

# Part C: Pleiades, what it publishes, what Histmersion keeps, and what to show

Research only. Nothing was edited or written to the database. I fetched the per-place JSON and the CSV dumps with plain `curl` (default user agent, no challenge). Live DB checks used only `histmersion query "select …"` and `histmersion schema`.

**Licence:** Pleiades data is CC BY 3.0 ("Sharing and remixing permitted under terms of the Creative Commons Attribution 3.0 License (cc-by)", the `rights` field of every place JSON).

---

## 1. Pleiades export formats

### 1a. Per-place JSON (`https://pleiades.stoa.org/places/{id}/json`)

This is a GeoJSON `FeatureCollection` with Pleiades fields added. It was checked on 770053487 (Arkadiko Bridge) and 570740 (Tiryns).

**Place level**

| field | example (770053487 unless noted) |
|---|---|
| `id`, `uri`, `@type: Place` | `770053487`, `https://pleiades.stoa.org/places/770053487` |
| `title` | "Arkadiko Bridge" |
| `description` (one-sentence abstract) | "An arched Bronze Age bridge near the Mycenaean center of Tiryns. The bridge is located along an ancient highway connecting Tiryns to Epidauros." |
| `details` (HTML, longer prose; often empty) | `""` (Tiryns has a full paragraph) |
| `placeTypes` / `placeTypeURIs` | `["bridge"]` / `…/vocabularies/place-types/bridge` |
| `subject` (keywords/tags) | `["polygonal masonry"]`. Tiryns: `polygonal masonry, UWHS, extant remains, dare:feature=settlement, dare:ancient=0` |
| `provenance` | "Pleiades" (or "Barrington Atlas: BAtlas 58 D2 Tiryns") |
| `reprPoint` `[lon,lat]` and `bbox` `[minx,miny,maxx,maxy]` | `[22.939167, 37.595556]` |
| `features[]` | GeoJSON features for the locations, each with `snippet` ("Bridge, ; 2000 BC - 1000 BC"), `location_precision`, `link`, `description` |
| `connections[]` (outgoing, full objects) | each has `id, title, uri, connectionType` (e.g. `part_of_regional`, `connection`, `part_of_analytical`, `abuts`, `dependent`…), `connectionTypeURI`, `connectsTo` (place URI), `associationCertainty`, `start`/`end`, `attestations[]`, `description`, `details`, `references[]`, `provenance`, `creators`, `contributors`, `history`, `review_state` |
| `connectsWith[]` | URIs of connected places, **both directions**. The bridge's list is `[…/570740]` and Tiryns' list includes `…/770053487`. The bridge's `connections[]` came back empty in the JSON, but the CSV records the bridge→Tiryns link (see 1b), so the typed object is only on one side or unpublished. |
| `references[]` | each has `shortTitle`, `formattedCitation` (HTML CSL), `citationDetail`, `citationTypeURI`/`type` (CiTO: `seeFurther`, `citesAsRelated`, `citesAsEvidence`, `citesAsDataSource`), `accessURI`, `alternateURI`, `bibliographicURI` (Zotero), `otherIdentifier` |
| `names[]`, `locations[]` | see below. The bridge has **no names**, only its title. |
| `creators[]`, `contributors[]` (`username, name, uri`), `created`, `history[]` (`modified, modifiedBy, comment/action`), `review_state` ("published"), `rights` | |
| dates | Not at place level in the JSON. They live on locations and names as `start`/`end` and `attestations[]`. The CSV derives place `minDate`/`maxDate`. |

The bridge's references are: **Wikipedia (English)** `en.wikipedia.org/wiki/Arkadiko_Bridge` (seeFurther), **Odysseus** (Greek Ministry of Culture) `odysseus.culture.gr/…obj_id=1710`, and **Wikidata Q254087** (citesAsRelated). Tiryns has 24 references, including BAtlas 58 D2, New Pauly DOI, PECS/Smith (Perseus), Pausanias passage URN (citesAsEvidence), Zangger 1994 (JSTOR), Hansen 2004 Inventory, **ToposText** `topostext.org/place/376228PTir`, **Trismegistos** `trismegistos.org/place/37671`, **GeoNames**, **Wikidata Q217379**, **MycAtlas** `helladic.info …place=C842`, DARMC, Arachne ×3, nomisma, MANTO, CFL/AGO, UNESCO WHL 941. External IDs are expressed as `accessURI` plus `shortTitle`. `otherIdentifier` is usually blank.

**Names** (`names[]`): `id, uri, attested` (original script, e.g. Τίρυνθα), `romanized` (comma-list of forms), `language` (BCP-47-ish: `grc`, `la`, `el`, `en`, `akk`…), `nameType` (`geographic`/`ethnic`…), `attestations[]` = `{timePeriod, timePeriodURI, confidence (confident|less-certain|…)}`, `start`/`end` (signed years derived from the periods), `associationCertainty`, `transcriptionAccuracy`, `transcriptionCompleteness`, `description` ("Conventional modern name for the archaeological site."), `details`, `provenance`, `references[]` (the evidence for the name, e.g. GeoNames), `creators`/`contributors`, `review_state`.

**Locations** (`locations[]`): `id, uri, title` ("Imagery location of Arkadiko Bridge"), `geometry` (Point / LineString / Polygon), `start`/`end`, `attestations[]` (timePeriod + confidence), `featureType[]`/`featureTypeURI[]`, `locationType[]` (`representative`, …), **`accuracy`** (URI of an accuracy assessment, e.g. `…/features/metadata/google-earth-and-geoeye-imagery-2012`) and **`accuracy_value`** (metres, **5.0** for the bridge), `archaeologicalRemains` (`substantive`, `unknown`, …), `associationCertainty`, `description` ("Coordinates after GeoHack."), `details`, `provenance`, `references[]`, `creators`, `history`, `review_state`.

### 1b. Daily CSV dumps (`https://atlantides.org/downloads/pleiades/dumps/`)

These are `pleiades-{places,names,locations}-{YYYYMMDD|latest}.csv.gz`. The `pid` column of names and locations joins to places `id`. The CSVs (unlike the JSON) carry `minDate`/`maxDate`.

- **places** (42 512 rows on 2026-10-27): `authors, bbox, connectsWith, created, creators, currentVersion, description, extent (GeoJSON), featureTypes, geoContext, hasConnectionsWith, id, locationPrecision (precise 30 302 | unlocated 7 605 | rough 3 369 | related 1 236), maxDate, minDate, modified, path, reprLat, reprLatLong, reprLong, tags, timePeriods (letter codes), timePeriodsKeys, timePeriodsRange, title, uid`.
  - `connectsWith` = this place's **outgoing** connections (bridge: `570740`). `hasConnectionsWith` = **incoming** (Tiryns: `770053487`). The connection **type is not in this CSV**.
  - `description` is filled on 42 496 of 42 512 places. `connectsWith` is non-empty on 11 518 and `tags` on 22 328. 2 807 places have no `minDate`.
- **names** (adds): `nameAttested, nameLanguage, nameTransliterated, pid` plus `minDate/maxDate/timePeriodsKeys` per name, `description`, `tags`. It has no certainty or accuracy columns. Those are only in the JSON or the GitHub `names.csv`.
- **locations** (adds): `featureType, geometry (GeoJSON), locationType, pid` plus dates per location. It has **no accuracy column**. Accuracy is only in the JSON or the GitHub files.

### 1c. Bulk JSON (`https://atlantides.org/downloads/pleiades/json/pleiades-places-YYYYMMDD.json.gz`)

This is the whole gazetteer as an array of the per-place JSON above. It is the only bulk file with everything: typed connections, references, name and location attestations, and accuracy.

### 1d. GitHub `isawnyu/pleiades.datasets`, `data/gis/` (normalised CSVs) and `data/indexes/`

- `places.csv`: `created, description, details, provenance, title, uri, id, representative_latitude, representative_longitude, bounding_box_wkt, location_precision`
- `places_place_types.csv`: `place_id, place_type`. `place_types.csv` is the vocabulary.
- `connections.csv`: `…, id, place_id, connection_type, connects_to, association_certainty, year_after_which, year_before_which`. `connection_types.csv`: `key, term, definition, same_as, uri`.
- `names.csv`: `…, place_id, name_type, language_tag, attested_form, romanized_form_1..3, association_certainty, transcription_accuracy, transcription_completeness, year_after_which, year_before_which`
- `location_points.csv` / `location_linestrings.csv` / `location_polygons.csv`: `…, place_id, accuracy_assessment_uri, accuracy_radius (m), archaeological_remains, association_certainty, geometry_wkt, year_after_which, year_before_which, location_precision`
- `places_accuracy.csv`: `place_id, title, uri, location_precision, accuracy_hull (WKT), max_accuracy_meters, min_accuracy_meters, accuracy_bases, location_types`
- Vocabularies: `time_periods.csv`, `languages_and_scripts.csv`, `name_types.csv`, `association_certainty.csv`, `transcription_accuracy.csv`, `transcription_completeness.csv`, `archaeological_remains.csv`.
- `data/indexes/*.json` are ready-made alignment tables: `wikidata.json` (`wd:Q… → {alignments:[pleiades uri], citation_detail}`), `topostext.json` (`pleiades uri → {alignments:[topostext uri], place_title, place_types, lat, lon}`), `tm.json` (Trismegistos), `manto.json`, `cfl_ago.json`, `aio.json`, `paus-spiro-perseus.json`, `tp-talbert-cup.json` (Peutinger), and `name_index.json`. These are the cheapest way to import external links without parsing every place's references.

---

## 2. How Histmersion imports Pleiades today

**Pipeline:** `db/places/fetch.sh:14,29-34` downloads `pleiades-places-latest.csv.gz` and `pleiades-names-latest.csv.gz` (CSV only: not the locations dump, not the JSON, not GitHub). `db/places/build.py pleiades` writes the committed LPF file `db/places/03-pleiades.json` (34 397 features), which `histmersion places seed` loads. The live store has 33 758 `pleiades_*` rows, after 7 `place_removals` and merges.

**What the seed keeps** (`db/places/build.py:180-216`):
- **Id** `pleiades_<id>` and `properties.pleiades_uri` (l.210-213). `place_links (pleiades, <id>, exact_match)` is derived from it (`modules/db/src/main/scala/com/histmersion/db/GazetteerRepo.scala:35-48`, `V071__gazetteer_sources.sql:65-73`).
- **Point** from `reprLong`/`reprLat`, rounded to 5 decimals (l.185, 212).
- **Period** `[minDate, maxDate]` from the CSV (l.199-203). There is no PeriodO lookup, and `maxDate = 2100` means "still extant".
- **Title** plus `/`-split alternates (l.205-207), and **names** from the names dump: `nameTransliterated`, `title`, `nameAttested` with `nameLanguage` (`pleiades_names`, l.160-178). They are imported **without** their date range, deliberately (docstring l.165-168, README l.87-91).
- **`properties.feature_types`** (raw `featureTypes`) and **`properties.time_periods`** (`timePeriodsKeys`) (l.214-215).
- **`kind`** from `feature_types` via `PL_KIND` / `pleiades_kind` (l.117-154): site / region / polity / people / feature. `bridge` is not in `PL_KIND`, so it defaults to `site`.
- **`source`** = "Pleiades (pleiades.stoa.org), CC-BY" (l.116).
- **Site type** (icon) is computed at read time from `feature_types`: `modules/api/shared/src/main/scala/com/histmersion/api/SiteTypes.scala:63` (`pleiadesTypes`), **l.78 `"bridge" -> Infrastructure`** ("road, bridge, aqueduct", route icon), `pleiades()` l.100, `of()` l.180-187. The `places.site_type` column stays NULL for all Pleiades rows, which is expected: it only stores outside refinements.
- **Grid-cell precision** is derived later from the coordinates (`modules/values/src/main/scala/com/histmersion/values/PlaceVocabulary.scala:62-69`), not from Pleiades' `locationPrecision`.
- The **per-place JSON** is read only for media: `MediaCollections.scala:595-650` (`PleiadesLinks`) reads `references[].accessURI/alternateURI` solely to find Wikimedia Commons picture files. Every other reference is discarded.

**What it drops:**

| Pleiades field | status |
|---|---|
| `description` (filled on 99.96% of places) | **dropped**. `place_descriptions` for Pleiades places come only from Wikidata (7 783) or TGN (160) via links. The bridge has none. |
| `details` (HTML) | dropped (not in CSV) |
| `placeTypes` as a list for display | kept only as the raw string in `properties.feature_types`. `place_types` table: 0 rows for the bridge. |
| `tags`/`subject` ("polygonal masonry", "UWHS", "extant remains") | dropped |
| `connectsWith` / `hasConnectionsWith` (CSV), typed `connections` (JSON) | **dropped**. `place_relations` has 0 rows with a Pleiades child. |
| `references` (Wikipedia, Wikidata, ToposText, TM, BAtlas, New Pauly, Perseus…) | **dropped** (except Commons files for media). A Pleiades→Wikidata link exists only when the *Wikidata* import found P1584: 8 751 Pleiades places have a wikidata link. The bridge has none. |
| name date ranges / attestations / certainty / transcription quality / nameType / language-specific attested vs romanised | ranges dropped on purpose. Language is kept. The rest is not in the CSV. |
| locations dump (multiple geometries, LineString/Polygon extents, `locationType`, `archaeologicalRemains`) | not downloaded. The CSV `extent` is also ignored, so only the representative point is kept. |
| accuracy (`accuracy_value` m, assessment URI, `places_accuracy.csv` hull) | **dropped**. `place_footprints.accuracy_m` is NULL (bridge footprint: `certainty = approximate`, `accuracy_m = NULL`). |
| `locationPrecision` (`rough`/`related`) | used only to drop `unlocated`. `rough` is not stored. |
| creators/authors, created/modified, `currentVersion` | dropped (no attribution beyond the source line) |

**Filters that exclude a place** (`build.py:184-198`):
1. No numeric `reprLong`/`reprLat` (l.184-187).
2. Outside `BBOX`. Since 2026-10-27 this is the whole world (l.24), so nothing is excluded.
3. `locationPrecision == "unlocated"` (l.190-191). This removes about 7 600 places.
4. No `featureTypes`, or **all** types in `PL_DROP = {label, unlocated, numbered feature, undefined}` (l.124, 192-194).
5. Empty title, or "Withdrawn (duplicate): …" (l.196-197).

**Not filtered:** undated places (`when=None` is allowed, l.199-203), and no feature type is excluded except the four in `PL_DROP`. A type absent from `PL_KIND` (bridge, aqueduct, tomb…) becomes `site`.

---

## 3. Live DB check (read-only)

| id | label | kind | period | precision | links | names |
|---|---|---|---|---|---|---|
| `pleiades_770053487` | **Arkadiko Bridge**, present | site (site type at read time: infrastructure) | `[-2000,-999)` | stated (NULL) | pleiades only | 1 ("Arkadiko Bridge") |
| `pleiades_682104977` | **Cyclopean Bridge of Agios Ioannis** (Mycenae, connectsWith 570491), present | site | `[-2000,2100)` | — | pleiades only | 2 |
| `pleiades_201914850` | Girsu Bridge (−3000), present | site | `[-3000,2100)` | — | — | — |
| `pleiades_570740` | Tiryns, present | site | `[-2000,2101)` | — | pleiades, wikidata Q217379, geonames, commons | — |
| `pleiades_570491` | Mycenae | site | `[-750,2101)` | — | — | — |

Those three are the only Pleiades bridges dated before 1000 BCE.

Nearby (within 3 km of the bridge, excluding polities and regions):
- `wikidata_q17493612` **Acropolis of Kazarma** (289 m, undated)
- `wikidata_q20183065` **Kazarma Tholos Tomb** (370 m, undated)
- `pleiades_570426` Lessa (523 m, −550…300)
- `wikidata_q12874015` Arkadiko village (1.6 km)

`wikidata_q16322745` "Kazarma" is a different village in Messenia (21.85 E).

**Conclusion:** the Arkadiko ("Kazarma") bridge **is present**, with the right point, the right Bronze-Age period and the right icon class. What is missing is everything that makes it informative:
- Its **description** ("arched Bronze Age bridge … along an ancient highway connecting Tiryns to Epidauros") is not stored.
- Its **connection to Tiryns** is not stored.
- Its **Wikipedia/Wikidata links** are missing. Wikidata **Q254087 is not in the DB at all**: the Wikidata import kept only "archaeological site / human settlement" classes, so the bridge item never came in, and there is no P1584 link back.
- Its 5 m **accuracy** is missing.
- Its alternative name "Kazarma Bridge" is missing. Pleiades has no names for it, and nothing else supplies one, so searching "Kazarma bridge" finds only the Wikidata acropolis and tholos, not the bridge.

---

## 4. Recommendations

### What to import (in priority order; all CC BY 3.0, attribution already carried)

1. **`description`**: from the places CSV column already downloaded, a one-line change in `build.py:209-215`. Put it in `place_descriptions (lang 'en', import_source 'pleiades', import_ref <id>)`. This gives about 42 k one-sentence abstracts and is the biggest gain for the least effort.
2. **Connections**: from GitHub `data/gis/connections.csv` (typed, with certainty and years) or, more simply, CSV `connectsWith`/`hasConnectionsWith` (untyped). Store them as `place_relations (child, parent, kind=<connection_type>, period)`, mapping `part_of_*` to `gvp:broaderPartitive` (which feeds the "part of" line) and other types (`connection`, `abuts`, `dependent`, `port_of`, `on_road`…) as non-hierarchical links. This gives about 11.5 k places with a connection.
3. **External links**: from `data/indexes/wikidata.json`, `topostext.json`, `tm.json`, `manto.json` (cheap, already aligned), or from `references[]` in the bulk JSON for Wikipedia and the rest. Store them as `place_links (scheme, external_id, relation 'close_match'|'see_also', import_source 'pleiades')`. Add schemes `wikipedia`, `topostext`, `trismegistos`, `manto`, `mycatlas`, `odysseus`, `perseus`, `doi` to `PlaceLink.url`. A Pleiades→Wikidata link also lets the Wikidata enrichment (descriptions, Commons media, site-type refinement) reach places like the bridge whose Wikidata class was filtered out.
4. **Location accuracy**: from `data/gis/places_accuracy.csv` (`max_accuracy_meters`, `accuracy_hull`) or `location_points.csv` `accuracy_radius`. Put it in `place_footprints.accuracy_m` on the extent footprint, and also store `location_precision = 'rough'` alongside `grid-cell`.
5. **Names with their periods and quality**: from GitHub `names.csv`. Keep the current undated match row, but store the attested period and certainty for display: `place_names.period` is a match gate, so add `attested_period`/`certainty` columns or a citation string rather than gating on it.
6. **Place types and tags as data**: the `place_types` table already exists (`place_id, type_label, period`). Fill it from `places_place_types.csv`, and keep `tags` (`polygonal masonry`, `extant remains`, `UWHS`) as chips.
7. Optional, later: **extra geometries** from `location_linestrings`/`location_polygons` (Tiryns has an OSM outline), as additional footprints with their periods. Also `archaeologicalRemains`.

The simplest switch overall is from the two CSVs to **GitHub `data/gis/*.csv` plus `data/indexes/*.json`**. That covers 1–6 with no per-place HTTP fetches. The bulk JSON (`json/pleiades-places-*.json.gz`) is the alternative single-file source.

### How to show it

**Hover card** (`Preview.card(d: PlaceDetail)`, `modules/web/src/main/scala/com/histmersion/web/Preview.scala:111-138`). This needs no new card structure:
- `summary`: the Pleiades description when Wikidata has none (today the bridge's card has no summary at all). Example: *"An arched Bronze Age bridge near the Mycenaean center of Tiryns…"*
- byline: `Site · bridge · 2000–1000 BCE`. Show the Pleiades place types as words instead of only the icon.
- facts: `connects with: Tiryns` (clickable), `accuracy ±5 m (imagery 2012)`, `remains: substantive`, tags as chips (`polygonal masonry`, `extant remains`).
- links (today capped at 4, l.120): order them Wikipedia, Wikidata, Pleiades, ToposText, then "+N more" in the panel.

**Place panel** (click-pinned card or side panel), with sections:
- **Names**: a table of name, script/language, period ("Τίρυνς · grc · Archaic–Hellenistic, confident"), type and certainty, and transcription quality as a muted note.
- **Connections**: grouped by type ("part of: Argolis, Argive Plain", "connected to: Arkadiko Bridge"). Each opens that place's card. On hover, highlight the connected places on the map.
- **On the map**: draw the connections as thin dashed **lines** between the representative points when the place is selected (Arkadiko Bridge → Tiryns is about 12.5 km; better, the bridge to Tiryns and on to Epidauros). Draw them only for the selected place, so the map is not cluttered. Respect each connection's period under the time filter.
- **Location accuracy**: draw a translucent **uncertainty circle** of radius `accuracy_m` (or the `accuracy_hull` polygon) around the point when selected. For grid-cell and `rough` points (about 2.4 k plus 3.4 k), show the cell or a large circle and the word "approximate". This also makes the existing `grid-cell` marking visible.
- **References**: a list from `references[]` grouped by CiTO type ("Evidence: Paus. 9.36.5", "Further reading: New Pauly, Hansen 2004 no. 356, Zangger 1994"), with `formattedCitation` text and the link. Mark items that already exist in the corpus by DOI as source links.
- **Credits** line: "Pleiades place 770053487 · J. Becker · CC BY 3.0 · modified 2026-03-31".

### Small fixes the investigation surfaced

- A search for "Kazarma bridge" misses the bridge. Importing Wikidata Q254087's labels, via the link in (3), or an `en` alias, would fix that.
- The Wikidata Kazarma acropolis and tholos sit 300–400 m from the bridge, undated and unlinked. Pleiades connections plus links would let the panel show them as "nearby".
