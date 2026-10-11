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
  val mediaDotSize: Px     = Px(10)
  val overlayFetchLimit: Int = 400   // polities alive in one year: up to ~140 (1200 CE); after de-duplication
  val mediaFetchLimit: Int   = 200
  val siteFetchLimit: Int    = 200
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
