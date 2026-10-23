# Map layers: one point layer, four descriptors

The map draws five categories. Four are **points** — findings (a source's claims), sites, events and
pictures (media) — and one is **areas** (regions and polities, the boundaries layer). The four point
categories do the same things with different data, so they share ONE implementation
(`web/PointLayer.scala`), parameterised by a per-kind descriptor (`PointKind[A]`, `web/PointKinds.scala`).
Adding a fifth kind of point is one more descriptor.

## Analysis (2026-10-15, before the refactor)

Where each concern lived before, per category. **D** = duplicated code doing the same thing,
**M** = the same behaviour differing only by metadata, **G** = genuinely different.

| concern | findings | sites | events | media | verdict |
|---|---|---|---|---|---|
| feed | `App.load` → `/api/findings` (filter); `App.quickRender` (local time cut); `TimelinePlayback.frames` (per frame) → `MapView.render` | `MapView.loadSites` → `/api/places/geo?kind=site&asOf=` (cached by year) | `EventMarkers.load` → `/api/events/geo` (filter, bbox, kind, tier) | `MapView.loadMedia` → `/api/media-geo` (span, provider, licence) | **G** (four endpoints) — but each ends in the same "show these items for this span" |
| reload triggers | filter / year window (App), playback frames | span (`reloadLayers`, throttled), site-type menu | span, `moveend` (throttled), kind/tier menus | span, provider/licence menus | **M** (which signals) |
| filter predicate | server (filter) | client: `siteType` menu | server: kind, tier, bbox | server: provider, licence | **M** |
| layer switch | `UiState.sources` → `showSources` (cluster on/off) | `MapLayer.Sites` → `LayerSync` → `FadingLayer.attach` | `MapLayer.Events` → same | `MapLayer.Media` → same | **D** (two switch paths) |
| marker | `makeMarker` + `dotIcon` (a round `.hd-dot`, ring, count) | `SiteIcon` (an icon on a light disc) or `siteDot` (an SVG `circleMarker`) | `EventMarker.html` (a badge, tier classes) | `mediaMarker` (a 10 px `.hd-media-dot`) | **D** — four builders of "shape + glyph + fill"; **M** shape/glyph/size/tier |
| fill | `colorFor`/`gradientBg` (+ confidence desaturation) | `viridisAt(mid)` | `Fmt.color(rampAt)` | the media kind's palette colour | **M** (the period ramp of a date range; findings add confidence) |
| recolour on a span change | remove + re-create every kept marker | `setStyle` or `SiteIcon.recolor` | `setColor` | none (stale) | **D**, and media was a bug |
| enter/exit animation | `markerDiff`, `hd-entering`/`hd-exiting`, an exit layer | `FadingLayer` fade-in/out | `FadingLayer` | `FadingLayer` | **D** (two animation systems) |
| hover card | `PlaceHover.bindSource` (delayed, at the pointer, focus too) | `PlaceHover.bind` (delayed, at the pointer, focus) | `[data-event]` delegated `Preview` handler (immediate, under the element) | none (a `title`) | **D**, inconsistent timing and placement |
| click / Enter | popup (one finding) or the pick list (coincident) | choose the place filter + pin its card | pin its card | popup | **G** (what a click means), one dispatch |
| keyboard | Leaflet's marker button + focus card | `PlaceHover.keyboard` (tabindex, Enter pins, focus moves in) | none (`keyboard = false`) | Leaflet's default | **D**, events and media inaccessible |
| aria / title | `markerLabel`, "N findings here" | `label · type` title | title + aria on the inner span | the kind as title | **M** |
| coincident points | `buildSpots` groups by lat/lon into one marker with a count | none | none | none | **G** for findings (a point holds several claims) |
| clustering | the only cluster members (`hdItems`) | own layer group | own layer group | own layer group | **D**→ now one group, every point kind |
| playback | re-rendered every frame (all markers re-created) | reloaded per span | reloaded per span + `EventLayer` labels | reloaded per span | **M**; `EventLayer` (labels, outlines) is **G** |
| legend | ramp + confidence | `MapKey.sitesHtml` | `MapKey.eventsHtml` | none | **M** |
| toolbar toggle | `LayerToggle(Icon.Sources…)` on `UiState.sources` | `MapLayer.Sites` | `MapLayer.Events` | `MapLayer.Media` | **M** |
| filter menus | none | `LayerFilters.select` (site type) | `LayerFilters.select` (kind, tier) | `MediaFilters.select` (provider, licence, counts) | **D** (two native selects) |
| tests | `MapViewSuite`, browser "hovering a finding dot" | `EventMarkersSuite`, browser #1446/#1449 | `EventMarkersSuite`, browser #1449 | `MediaCardSuite` | per kind |

## The structure now

- **`PointKind[A]`** — what differs, as data: the `DotKind` (noun, plural), the **shape** (`Shape`:
  diamond, circle, square, photo), the marker box, the pane, and per item: key, position, glyph (or a
  count), extra classes (tier, basis, ring), fill for a span, the dated mids and confidence the cluster
  bubble reads, the accessible label and title, the card it previews (`Preview.Target`), what a click or
  Enter does, and the row it shows in a bubble's list.
- **`PointLayer[A]`** — everything the same: one marker builder (`PointMarker.html`: shape + glyph +
  fill + border + halo), add/remove in batches to the ONE cluster group, the diff (fade in, fade out where
  drawn alone, kept markers recoloured or re-iconed in place), the hover card (delayed, at the pointer)
  and the focus card, the keyboard button (tabindex, Enter/Space), the remembered content while switched
  off, and the switch itself.
- **`PointKinds`** — the four descriptors (`Findings`, `Sites`, `Events`, `Media`), and the UI metadata
  that is per kind too: the toolbar's shape icon and switch, the legend entry, the filter menus (one
  `IconSelect` dropdown for all of them).
- What stays per kind is what genuinely differs: the feed (four endpoints), the findings' grouping of
  coincident claims into one point, what a click means, and the playback labels of `EventLayer`.
- The areas (boundaries) stay their own layer — shapes, not points — and share the card target
  (`PlaceHover.bind`), the period colouring and the toolbar/legend icon grammar.

## The areas over a range: "States shown"

The boundaries layer reads the span through the toolbar's **States shown** menu (`#area-pick`,
`UiState.areaPick`, per device; `core.GeoPick`): a snapshot at the span's **midpoint** (the default,
what the map always did), its **start** or its **end** — sent as `asOf` — or **all states in the
period** with their **earliest** or **latest** shape — sent as `from`/`to`/`pick`
(`/api/places/geo`, `PlaceRepo.geoJsonAt`). The cache key is the resolved time (`GeoTime`), so a
snapshot at one year is one entry whichever choice led there. Playback always draws the playhead's
year. The sites layer takes a snapshot's year, and the midpoint for the two whole-period choices
(range sites are not built).

Over a range every state alive at any time in it is drawn — they may overlap — one shape per state:

- **Cross-tier duplicates** collapse as at a year (OHM over the datasets over the agent sketches; same
  normalised label, or > 50 % of the smaller covered), but only between shapes that **coexist**.
- **"The same state over time"** is the same kind and **normalised label** — never spatial overlap
  (the Hittites then Phrygia on one territory are two states) and never the Wikidata id, which
  Cliopatria gives to whole families of coexisting polygons (the Eighteen Kingdoms share Q5349053,
  an alliance polygon carries its senior partner's id, Han and Chauhan share one).
- Of each state the shapes alive at its **first** year in the range are kept (or its **last**): the
  state as the range first (last) shows it, so two shapes of one state alive together both stay.
- The limit comes last, largest first, as at a year — but over a range the map asks for every state
  (2 000, `places.geo-max-limit`).

Measured on the live corpus (EXPLAIN ANALYZE, 2026-10-16, limit 400): a year 2–20 ms; -2000..-1000
≈ 20 ms; 1000..1500 ≈ 85–140 ms (≈ 60 ms reading the 3 540 shapes alive then, ≈ 45 ms writing 400
large polygons as GeoJSON); the whole -3000..2000 ≈ 0.9 s.

### Making it real-time (V090, 2026-10-16)

Where the time went, on a restore of the live corpus: every expression that reads a polygon
(its area, box, dimension) decompressed it again for each of the 13 000 rows alive over the whole
range (~70 ms apiece); the small columns sat in the TOAST table — `geom` and `geom_clipped` are
`main` storage, so to fit a row with a large polygon Postgres moved its id, label and properties out of
line first (`id` of 9 182 of 15 102 polities), and every id, label and `properties->>'method'` the layer
read was a TOAST fetch; the cross-tier pairs compared each sketch with every shape alive (650 000
comparisons); and the outlines went out as clipped to Natural Earth's 1:10m coast — 30 M vertices for
13 000 polities whose own borders have 3.3 M, 22 MB for 400 states over all time.

What changed:

- **Stored shape columns** (generated, so every writer keeps them right, after the land clip and the
  override triggers): `shape_area`, `shape_box`, `shape_dim`, `shape_valid`, `label_norm` (word for word
  `normLabelSql`) — the ranking and the collapse read them, never the polygon.
- **Polygon columns `extended`**: a large shape is what moves out of line, the row keeps the rest.
- **The web outline** `web_geojson`: `coalesce(geom_clipped, geom)` simplified (topology preserved) to
  a hundredth of the shape's size, at most 0.02° (~2 km — the events layer's outline tolerance, two
  pixels at zoom 7, finer than Cliopatria's own borders), 3 decimals at that tolerance, 4 for smaller
  shapes, written once on write. The layer draws it at a year and over a range; `geom` stays the
  shape for everything else. 1500 CE: 2.2 MB → 0.55 MB.
- **Pairs through an index**: over a range each sketch (tier 3) and each OHM shape (tier 1) finds the
  shapes it coexists with and may overlap through `places_area_time_geom` (GiST over period and
  geometry of the non-site places), held to `live`'s own predicate there; a pair whose boxes overlap by
  no more than half the smaller shape cannot pass and skips the intersection; a valid shape is not
  "made valid" again. A kindless or site layer keeps the plain pairing; `PlaceGeoSuite` holds the two
  to the same answer.
- **One buffer** for the FeatureCollection instead of a string per feature joined and wrapped.
- **The whole range is drawn**: the client asks for 2 000 areas over a range (a year keeps 400), the
  server caps `/api/places/geo` at `places.geo-max-limit` (2 000) instead of `limits.max-page` (500) —
  1000..1500 has 551 states, -3000..2000 has 1 744.

Results (ms; "before" EXPLAIN ANALYZE on the live corpus and on a restore of its 2026-10-16 backup;
"after" on the same restore with V090 applied — EXPLAIN, and the HTTP p50 of 10 requests to a server
on it over loopback, polity layer). Answers are identical: id, label, dates, dating, basis and
boundary of every row in order, at limit 400 and unlimited, for all eleven cases compared.

| request | before, live | before, copy | after, copy SQL | after, HTTP | outlines before → response after |
|---|---:|---:|---:|---:|---:|
| `asOf=-1500` | 3.2 | 4.3 | 2.9 | 5.4 | 113 → 26 KB |
| `asOf=1200` | 25 | 15 | 2.5 | 7.7 | 2.0 MB → 483 KB |
| `asOf=1500` | — | 17 | 2.4 | 7.1 | 2.2 MB → 554 KB |
| `-2000..-1000` earliest | 29 | 17 | 13 | 17 | 313 → 79 KB |
| `1000..1500` earliest, 400 | 158–396 | 93–100 | 12 | 20 | 8.2 → 1.9 MB |
| `1000..1500` earliest, all 551 | — | — | 12 | 21 | 2.0 MB |
| `-3000..2000` earliest, 400 | 1 272 | 513–533 | 60–66 | 77 | 22 → 4.9 MB |
| `-3000..2000` earliest, all 1 744 | — | — | 70 | 90 | 7.0 MB |

The whole timeline's remaining time: the 13 000 shapes alive in it read with their time rule (19 ms),
the polygon intersections of ~640 candidate pairs (18 ms), the window that picks each state's
version (8 ms), and moving 7 MB through JDBC (~15 ms).

## The layer toolbar

One group per layer — its switch, then its own options — separated by the toolbar's line: findings
(+ "only sources in view"), boundaries (+ States shown), sites (+ site type), media (+ provider,
licence), events (+ event kind), then the event **tier** menu as a group of its own, then the base
map. An option whose layer is off stays where it is, **disabled** (dimmed, its title "turn on <layer>
first"), so switching a layer moves nothing.

Their sizes are `ui.Layout` (square 24 px, circle 26, the diamond's box 34, the print 32 × 24), their
look one block of `style.css` (the shape grammar, docs/visualization-guide.md §2a): the glyph size per
shape is `--glyph-size`, the stroke widths `--glyph-ink-w` / `--glyph-halo-w`. A cluster bubble is
`DotCluster.bubbleHtml` — one SVG, its rings concentric by construction, its outline `DotCluster.form`
(a rounded square for events only, a square tab for events among others, #1647) — and the cluster radius
(`MapView.clusterRadius`) is the widest bubble or the largest marker box, whichever is wider. Above
`Layout.clusterAnimateMax` points the group zooms without its split/merge animation (`ClusterMotion`).

`PointKindsSuite` runs the same assertions over all four descriptors (shape, glyph, aria, title, the
filter predicate, the cluster count), so the kinds cannot drift apart again.

## On the map interface (2026-10-23)

Leaflet is gone; the layers above now draw through the app's own map interface (`web/map/`,
docs/user-guide/contributing/architecture.md "The map"), and MapLibre GL JS draws it. What that changed
for the layers, and what stayed:

- **Points** stay HTML markers — the shapes, glyphs, tier classes, hover and focus cards and the
  keyboard button are unchanged — but are `DomMarker`s in named `MarkerPane`s (`hd-pane--<name>`),
  positioned by CSS custom properties (`--tx`, `--ty`) and served by one delegated listener per pane
  instead of one per marker. The ONE cluster group is `PointSet` over a pure greedy grid
  (`Clustering`): the bubbles, their counts and the period-ramp mix are as before; above `animateMax`
  points a regroup is not animated and the markers are made in slices across frames (#1647's rule).
- **Areas** (states, a regional event's outline, the place picker's areas) are `ShapeSet`s: one GeoJSON
  source with fill and line layers per set, colour and alpha as per-feature state (`restyle` touches
  one feature), and hover and click hit-tested against the geometry (`ShapeHits`), so a state under
  a marker still answers.
- **Popups** are the interface's `Popup` (`.hd-popup`, the content in `.hd-popup-content`); the
  legend, the base-map menu and the options sit in the surface's corners.
- The base map's names (Readable, Physical) are drawn and collision-checked by MapLibre over the whole
  view, so they are never cut at a tile's edge (docs/research-map-styles.md §2); the corpus's own
  labels stay HTML above them.
