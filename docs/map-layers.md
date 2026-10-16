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
- The limit comes last, largest first, as at a year.

Measured on the live corpus (EXPLAIN ANALYZE, 2026-10-16, limit 400): a year 2–20 ms; -2000..-1000
≈ 20 ms; 1000..1500 ≈ 85–140 ms (≈ 60 ms reading the 3 540 shapes alive then, ≈ 45 ms writing 400
large polygons as GeoJSON); the whole -3000..2000 ≈ 0.9 s.

## The layer toolbar

One group per layer — its switch, then its own options — separated by the toolbar's line: findings
(+ "only sources in view"), boundaries (+ States shown), sites (+ site type), media (+ provider,
licence), events (+ event kind), then the event **tier** menu as a group of its own, then the base
map. An option whose layer is off stays where it is, **disabled** (dimmed, its title "turn on <layer>
first"), so switching a layer moves nothing.

Their sizes are `ui.Layout` (square 24 px, circle 26, the diamond's box 34, the print 32 × 24), their
look one block of `style.css` (the shape grammar, docs/visualization-guide.md §2a): the glyph size per
shape is `--glyph-size`, the stroke widths `--glyph-ink-w` / `--glyph-halo-w`. A cluster bubble is
`DotCluster.bubbleHtml` — one SVG, its rings concentric by construction — and the cluster radius
(`MapView.clusterRadius`) is the widest bubble or the largest marker box, whichever is wider.

`PointKindsSuite` runs the same assertions over all four descriptors (shape, glyph, aria, title, the
filter predicate, the cluster count), so the kinds cannot drift apart again.
