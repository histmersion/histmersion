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

`PointKindsSuite` runs the same assertions over all four descriptors (shape, glyph, aria, title, the
filter predicate, the cluster count), so the kinds cannot drift apart again.
