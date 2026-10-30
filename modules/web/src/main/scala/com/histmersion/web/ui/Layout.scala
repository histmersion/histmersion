package com.histmersion.web.ui

/** The UI's behavioural numbers: drag limits, fetch limits, timings, layout maths. Colours and looks are the
  * stylesheet's (`web/style.css`), not these.
  */
object Layout:
  val sidebarMinWidth: Px = Px(320)
  val sidebarMaxWidth: Px = Px(800)
  val sidebarDefault: Px = Px(480)
  val overviewHeight: Px = Px(44)
  val overviewBase: Px = Px(30)
  val detailHeight: Px = Px(58)
  val detailBase: Px = Px(40)
  val handleWidth: Double = 6.0
  val markerRadiusDefault: Int = 5
  val maxBuckets: Long = 1500L

  /** Quiet period after the last resize event before a curve is redrawn exactly. */
  val resizeSettleMs: Int = 120
  val minSpan: Long = 10L
  val windowMinWidth: Px = Px(300)
  val windowMinHeight: Px = Px(200)
  val windowMaxWidth: Double = 780.0
  val windowMaxHeight: Double = 600.0

  /** The merge review screen's window: wide enough for a field table and a diff3 view side by side. */
  val mergeWindowMaxWidth: Double = 1500.0
  val mergeWindowMaxHeight: Double = 1000.0
  val windowCascadeOffset: Int = 28

  /** The least room under the map options' button for the panel to open there on a tablet; with less, and more above,
    * it opens over the button.
    */
  val mapOptionsMinBelow: Px = Px(280)
  val windowCascadeSlots: Int = 5
  val pickerMax: Int = 15

  /** The most dots a bubble no zoom parts is fanned out into around its spot (more: listed only). */
  val spiderMax: Int = 40
  val fitMaxZoom: Int = 9

  /** Below this zoom a region or polity known only by a point (no boundary yet) is not labelled: zoomed out, one name
    * at one point would stand for a whole country ([[com.histmersion.web.PlaceGlyph]]).
    */
  val areaLabelMinZoom: Int = 5

  /** A picture's marker: a landscape "photo print" badge (4:3), its white mat included. */
  val mediaMarkerWidth: Px = Px(32)
  val mediaMarkerHeight: Px = Px(24)

  /** A finding's diamond: its box is larger than a site's circle or an event's square, so the diamond inside (70 % of
    * the box, turned 45°) looks as big as they do and still holds a 15 px glyph.
    */
  val findingMarkerSize: Px = Px(34)
  val overlayFetchLimit: Int = 400 // polities alive in one year: up to ~140 (1200 CE); after de-duplication
  /** The areas over a whole range ("All states in period"): every state alive in it — 551 in 1000..1500, 1 744 over
    * -3000..2000 (~7 MB of stored simplified outlines, V090); the server caps it at `places.geo-max-limit`.
    */
  val overlayRangeFetchLimit: Int = 2000

  /** The events layer (#1449): its markers' size (a square: smaller than the circle's box, as big to the eye).
    */
  val eventMarkerSize: Px = Px(24)

  /** The most markers (points drawn alone and bubbles, in view and its margin) the map's cluster layer draws before and
    * after a zoom and still animates it (bubbles splitting and merging): above it a zoom redraws them at once
    * ([[com.histmersion.web.map.ClusterMotion]]). Counted on screen, not among the points held: a wide window's view
    * holds ~2 000 events and findings and draws 200–500 markers.
    */
  val clusterAnimateMax: Int = 1000

  /** A site's badge (#1449): its type's icon, the exclamation mark when the type is unknown. */
  val siteIconSize: Px = Px(26)
  val playbackWindowHalf: Long = 50L
  val playbackDuration: Double = 30.0

  /** Playback's findings are fetched this many seconds of playback ahead, at most [[playbackFetchLimit]] a request, and
    * cut to each frame's window locally (#3764).
    */
  val playbackFetchSeconds: Double = 4.0
  val playbackFetchLimit: Int = 2000

  /** Playback's states are fetched this many seconds of playback ahead of the playhead, and each fades in and out over
    * this many seconds of playback at its first and last year (#3764).
    */
  val playbackLeadSeconds: Double = 0.5
  val playbackAreaFadeSeconds: Double = 0.3
  val exitAnimMs: Int = 350

  /** Minimum gap between overlay-layer (regions, sites, media) refetches while the span moves. */
  val layerReloadMs: Int = 200

  /** The quiet period after the span's last move before the boundaries over a whole range ("All states in period",
    * megabytes an answer) are fetched again (#1949).
    */
  val rangeReloadMs: Int = 250

  /** Place GeoJSON responses kept in memory, keyed by (kind, as-of year) — at most this many, and at most
    * [[geoCacheChars]] characters of them together: a year's polities are 0.1–2 MB, a whole range's ~7 MB (#1949).
    */
  val geoCacheSize: Int = 48
  val geoCacheChars: Long = 24L * 1024 * 1024

  /** Hover cards kept per kind (sources, articles, places, events), the least recently hovered going first.
    */
  val cardCacheSize: Int = 300

  /** Identifier lookups ([[com.histmersion.web.IdentLink]]) and gazetteer labels kept. */
  val identCacheSize: Int = 1000
  val placeLabelCacheSize: Int = 5000

  /** Quiet period before a sidebar tab (articles, events) refetches after a filter change. */
  val tabReloadMs: Int = 150

  /** How many rows the Articles and Events tabs fetch at once; past them, "show more" fetches the next page
    * ([[FilterTab]]).
    */
  val tabFetch: Int = 200

  /** How many rows "show more" reveals in the Articles and Events tabs. */
  val tabPage: Int = 25

  /** How many rows a page of Library's tables holds. */
  val libraryPage: Int = 50

  /** How many of a source's findings Library's reading pane lists before "All N findings". */
  val readingFindings: Int = 3

  /** How much of an article's opening paragraph the reading pane shows, in characters. */
  val readingOpening: Int = 600

  /** How long the pointer rests on a map place before its card is fetched and shown. */
  val hoverDelayMs: Int = 180

  /** The quiet period after the page's own change before the toolbar's counters are re-read (Notices). */
  val countersRefreshMs: Int = 250
