package com.histmersion.web.ui

/** The UI's behavioural numbers: drag limits, fetch limits, timings, layout maths. Colours and looks
  * are the stylesheet's (`web/style.css`), not these. */
object Layout:
  val sidebarMinWidth: Px  = Px(200)
  val sidebarMaxWidth: Px  = Px(800)
  val sidebarDefault: Px   = Px(360)
  val overviewHeight: Px   = Px(44)
  val overviewBase: Px     = Px(30)
  val detailHeight: Px     = Px(58)
  val detailBase: Px       = Px(40)
  val handleWidth: Double  = 6.0
  val markerRadiusDefault: Int = 5
  val maxBuckets: Long     = 1500L
  /** Quiet period after the last resize event before a curve is redrawn exactly. */
  val resizeSettleMs: Int  = 120
  val minSpan: Long        = 10L
  val windowMinWidth: Px   = Px(300)
  val windowMinHeight: Px  = Px(200)
  val windowMaxWidth: Double = 780.0
  val windowMaxHeight: Double = 600.0
  /** The merge review screen's window: wide enough for a field table and a diff3 view side by side. */
  val mergeWindowMaxWidth: Double  = 1500.0
  val mergeWindowMaxHeight: Double = 1000.0
  val windowCascadeOffset: Int = 28
  val windowCascadeSlots: Int  = 5
  val pickerMax: Int       = 15
  val fitMaxZoom: Int      = 9
  /** Below this zoom a region or polity known only by a point (no boundary yet) is not labelled:
    * zoomed out, one name at one point would stand for a whole country ([[com.histmersion.web.PlaceGlyph]]). */
  val areaLabelMinZoom: Int = 5
  /** A picture's marker: a landscape "photo print" badge (4:3), its white mat included. */
  val mediaMarkerWidth: Px  = Px(32)
  val mediaMarkerHeight: Px = Px(24)
  /** A finding's diamond: its box is larger than a site's circle or an event's square, so the diamond
    * inside (70 % of the box, turned 45°) looks as big as they do and still holds a 15 px glyph. */
  val findingMarkerSize: Px = Px(34)
  val overlayFetchLimit: Int = 400   // polities alive in one year: up to ~140 (1200 CE); after de-duplication
  /** The areas over a whole range ("All states in period"): every state alive in it — 551 in 1000..1500,
    * 1 744 over -3000..2000 (~7 MB of stored simplified outlines, V090); the server caps it at
    * `places.geo-max-limit`. */
  val overlayRangeFetchLimit: Int = 2000
  val mediaFetchLimit: Int   = 200
  val siteFetchLimit: Int    = 200
  /** The events layer (#1449): its markers' size (a square: smaller than the circle's box, as big to the
    * eye), and the most one view asks for. */
  val eventMarkerSize: Px    = Px(24)
  val eventFetchLimit: Int   = 1500
  /** A site's badge (#1449): its type's icon, the exclamation mark when the type is unknown. */
  val siteIconSize: Px       = Px(26)
  val playbackWindowHalf: Long = 50L
  val playbackDuration: Double = 30.0
  val exitAnimMs: Int        = 350
  /** Minimum gap between overlay-layer (regions, sites, media) refetches while the span moves. */
  val layerReloadMs: Int     = 200
  /** Place GeoJSON responses kept in memory, keyed by (kind, as-of year). */
  val geoCacheSize: Int      = 48
  /** Quiet period before a sidebar tab (articles, events) refetches after a filter change. */
  val tabReloadMs: Int       = 150
  /** How many rows the Articles and Events tabs fetch at once (they page locally, 25 at a time). */
  val tabFetch: Int          = 200
  /** How long the pointer rests on a map place before its card is fetched and shown. */
  val hoverDelayMs: Int      = 180
  /** The quiet period after the page's own change before the toolbar's counters are re-read (Notices). */
  val countersRefreshMs: Int = 250
