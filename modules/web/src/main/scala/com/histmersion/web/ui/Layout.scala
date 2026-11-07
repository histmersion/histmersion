package com.histmersion.web.ui

/** The UI's behavioural numbers: drag limits, fetch limits, timings, layout maths. Colours and looks are the
  * stylesheet's (`web/style.css`), not these.
  */
object Layout:
  type HandleWidth = HandleWidth.T
  object HandleWidth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkerRadiusDefault = MarkerRadiusDefault.T
  object MarkerRadiusDefault extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxBuckets = MaxBuckets.T
  object MaxBuckets extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResizeSettleMs = ResizeSettleMs.T
  object ResizeSettleMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MinSpan = MinSpan.T
  object MinSpan extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WindowMaxWidth = WindowMaxWidth.T
  object WindowMaxWidth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WindowMaxHeight = WindowMaxHeight.T
  object WindowMaxHeight extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeWindowMaxWidth = MergeWindowMaxWidth.T
  object MergeWindowMaxWidth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeWindowMaxHeight = MergeWindowMaxHeight.T
  object MergeWindowMaxHeight extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WindowCascadeOffset = WindowCascadeOffset.T
  object WindowCascadeOffset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WindowCascadeSlots = WindowCascadeSlots.T
  object WindowCascadeSlots extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PickerMax = PickerMax.T
  object PickerMax extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SpiderMax = SpiderMax.T
  object SpiderMax extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FitMaxZoom = FitMaxZoom.T
  object FitMaxZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AreaLabelMinZoom = AreaLabelMinZoom.T
  object AreaLabelMinZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OverlayFetchLimit = OverlayFetchLimit.T
  object OverlayFetchLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OverlayRangeFetchLimit = OverlayRangeFetchLimit.T
  object OverlayRangeFetchLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClusterAnimateMax = ClusterAnimateMax.T
  object ClusterAnimateMax extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaybackWindowHalf = PlaybackWindowHalf.T
  object PlaybackWindowHalf extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaybackDuration = PlaybackDuration.T
  object PlaybackDuration extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaybackFetchSeconds = PlaybackFetchSeconds.T
  object PlaybackFetchSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaybackFetchLimit = PlaybackFetchLimit.T
  object PlaybackFetchLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaybackLeadSeconds = PlaybackLeadSeconds.T
  object PlaybackLeadSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaybackAreaFadeSeconds = PlaybackAreaFadeSeconds.T
  object PlaybackAreaFadeSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExitAnimMs = ExitAnimMs.T
  object ExitAnimMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LayerReloadMs = LayerReloadMs.T
  object LayerReloadMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RangeReloadMs = RangeReloadMs.T
  object RangeReloadMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GeoCacheSize = GeoCacheSize.T
  object GeoCacheSize extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GeoCacheChars = GeoCacheChars.T
  object GeoCacheChars extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CardCacheSize = CardCacheSize.T
  object CardCacheSize extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IdentCacheSize = IdentCacheSize.T
  object IdentCacheSize extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceLabelCacheSize = PlaceLabelCacheSize.T
  object PlaceLabelCacheSize extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TabReloadMs = TabReloadMs.T
  object TabReloadMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TabFetch = TabFetch.T
  object TabFetch extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TabPage = TabPage.T
  object TabPage extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LibraryPage = LibraryPage.T
  object LibraryPage extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadingFindings = ReadingFindings.T
  object ReadingFindings extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadingOpening = ReadingOpening.T
  object ReadingOpening extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HoverDelayMs = HoverDelayMs.T
  object HoverDelayMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountersRefreshMs = CountersRefreshMs.T
  object CountersRefreshMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val sidebarMinWidth: Px = Px(320)
  val sidebarMaxWidth: Px = Px(800)
  val sidebarDefault: Px = Px(480)
  val overviewHeight: Px = Px(44)
  val overviewBase: Px = Px(30)
  val detailHeight: Px = Px(58)
  val detailBase: Px = Px(40)
  val handleWidth: com.histmersion.web.ui.Layout.HandleWidth = com.histmersion.web.ui.Layout.HandleWidth(6.0)
  val markerRadiusDefault: com.histmersion.web.ui.Layout.MarkerRadiusDefault = com.histmersion.web.ui.Layout.MarkerRadiusDefault(5)
  val maxBuckets: com.histmersion.web.ui.Layout.MaxBuckets = com.histmersion.web.ui.Layout.MaxBuckets(1500L)

  /** Quiet period after the last resize event before a curve is redrawn exactly. */
  val resizeSettleMs: com.histmersion.web.ui.Layout.ResizeSettleMs = com.histmersion.web.ui.Layout.ResizeSettleMs(120)
  val minSpan: com.histmersion.web.ui.Layout.MinSpan = com.histmersion.web.ui.Layout.MinSpan(10L)
  val windowMinWidth: Px = Px(300)
  val windowMinHeight: Px = Px(200)
  val windowMaxWidth: com.histmersion.web.ui.Layout.WindowMaxWidth = com.histmersion.web.ui.Layout.WindowMaxWidth(780.0)
  val windowMaxHeight: com.histmersion.web.ui.Layout.WindowMaxHeight = com.histmersion.web.ui.Layout.WindowMaxHeight(600.0)

  /** The merge review screen's window: wide enough for a field table and a diff3 view side by side. */
  val mergeWindowMaxWidth: com.histmersion.web.ui.Layout.MergeWindowMaxWidth = com.histmersion.web.ui.Layout.MergeWindowMaxWidth(1500.0)
  val mergeWindowMaxHeight: com.histmersion.web.ui.Layout.MergeWindowMaxHeight = com.histmersion.web.ui.Layout.MergeWindowMaxHeight(1000.0)
  val windowCascadeOffset: com.histmersion.web.ui.Layout.WindowCascadeOffset = com.histmersion.web.ui.Layout.WindowCascadeOffset(28)

  /** The least room under the map options' button for the panel to open there on a tablet; with less, and more above,
    * it opens over the button.
    */
  val mapOptionsMinBelow: Px = Px(280)
  val windowCascadeSlots: com.histmersion.web.ui.Layout.WindowCascadeSlots = com.histmersion.web.ui.Layout.WindowCascadeSlots(5)
  val pickerMax: com.histmersion.web.ui.Layout.PickerMax = com.histmersion.web.ui.Layout.PickerMax(15)

  /** The most dots a bubble no zoom parts is fanned out into around its spot (more: listed only). */
  val spiderMax: com.histmersion.web.ui.Layout.SpiderMax = com.histmersion.web.ui.Layout.SpiderMax(40)
  val fitMaxZoom: com.histmersion.web.ui.Layout.FitMaxZoom = com.histmersion.web.ui.Layout.FitMaxZoom(9)

  /** Below this zoom a region or polity known only by a point (no boundary yet) is not labelled: zoomed out, one name
    * at one point would stand for a whole country ([[com.histmersion.web.PlaceGlyph]]).
    */
  val areaLabelMinZoom: com.histmersion.web.ui.Layout.AreaLabelMinZoom = com.histmersion.web.ui.Layout.AreaLabelMinZoom(5)

  /** A picture's marker: a landscape "photo print" badge (4:3), its white mat included. */
  val mediaMarkerWidth: Px = Px(32)
  val mediaMarkerHeight: Px = Px(24)

  /** A finding's diamond: its box is larger than a site's circle or an event's square, so the diamond inside (70 % of
    * the box, turned 45°) looks as big as they do and still holds a 15 px glyph.
    */
  val findingMarkerSize: Px = Px(34)
  val overlayFetchLimit: com.histmersion.web.ui.Layout.OverlayFetchLimit = com.histmersion.web.ui.Layout.OverlayFetchLimit(400) // polities alive in one year: up to ~140 (1200 CE); after de-duplication
  /** The areas over a whole range ("All states in period"): every state alive in it — 551 in 1000..1500, 1 744 over
    * -3000..2000 (~7 MB of stored simplified outlines, V090); the server caps it at `places.geo-max-limit`.
    */
  val overlayRangeFetchLimit: com.histmersion.web.ui.Layout.OverlayRangeFetchLimit = com.histmersion.web.ui.Layout.OverlayRangeFetchLimit(2000)

  /** The events layer (#1449): its markers' size (a square: smaller than the circle's box, as big to the eye).
    */
  val eventMarkerSize: Px = Px(24)

  /** The most markers (points drawn alone and bubbles, in view and its margin) the map's cluster layer draws before and
    * after a zoom and still animates it (bubbles splitting and merging): above it a zoom redraws them at once
    * ([[com.histmersion.web.map.ClusterMotion]]). Counted on screen, not among the points held: a wide window's view
    * holds ~2 000 events and findings and draws 200–500 markers.
    */
  val clusterAnimateMax: com.histmersion.web.ui.Layout.ClusterAnimateMax = com.histmersion.web.ui.Layout.ClusterAnimateMax(1000)

  /** A site's badge (#1449): its type's icon, the exclamation mark when the type is unknown. */
  val siteIconSize: Px = Px(26)
  val playbackWindowHalf: com.histmersion.web.ui.Layout.PlaybackWindowHalf = com.histmersion.web.ui.Layout.PlaybackWindowHalf(50L)
  val playbackDuration: com.histmersion.web.ui.Layout.PlaybackDuration = com.histmersion.web.ui.Layout.PlaybackDuration(30.0)

  /** Playback's findings are fetched this many seconds of playback ahead, at most [[playbackFetchLimit]] a request, and
    * cut to each frame's window locally (#3764).
    */
  val playbackFetchSeconds: com.histmersion.web.ui.Layout.PlaybackFetchSeconds = com.histmersion.web.ui.Layout.PlaybackFetchSeconds(4.0)
  val playbackFetchLimit: com.histmersion.web.ui.Layout.PlaybackFetchLimit = com.histmersion.web.ui.Layout.PlaybackFetchLimit(2000)

  /** Playback's states are fetched this many seconds of playback ahead of the playhead, and each fades in and out over
    * this many seconds of playback at its first and last year (#3764).
    */
  val playbackLeadSeconds: com.histmersion.web.ui.Layout.PlaybackLeadSeconds = com.histmersion.web.ui.Layout.PlaybackLeadSeconds(0.5)
  val playbackAreaFadeSeconds: com.histmersion.web.ui.Layout.PlaybackAreaFadeSeconds = com.histmersion.web.ui.Layout.PlaybackAreaFadeSeconds(0.3)
  val exitAnimMs: com.histmersion.web.ui.Layout.ExitAnimMs = com.histmersion.web.ui.Layout.ExitAnimMs(350)

  /** Minimum gap between overlay-layer (regions, sites, media) refetches while the span moves. */
  val layerReloadMs: com.histmersion.web.ui.Layout.LayerReloadMs = com.histmersion.web.ui.Layout.LayerReloadMs(200)

  /** The quiet period after the span's last move before the boundaries over a whole range ("All states in period",
    * megabytes an answer) are fetched again (#1949).
    */
  val rangeReloadMs: com.histmersion.web.ui.Layout.RangeReloadMs = com.histmersion.web.ui.Layout.RangeReloadMs(250)

  /** Place GeoJSON responses kept in memory, keyed by (kind, as-of year) — at most this many, and at most
    * [[geoCacheChars]] characters of them together: a year's polities are 0.1–2 MB, a whole range's ~7 MB (#1949).
    */
  val geoCacheSize: com.histmersion.web.ui.Layout.GeoCacheSize = com.histmersion.web.ui.Layout.GeoCacheSize(48)
  val geoCacheChars: com.histmersion.web.ui.Layout.GeoCacheChars = com.histmersion.web.ui.Layout.GeoCacheChars(24L * 1024 * 1024)

  /** Hover cards kept per kind (sources, articles, places, events), the least recently hovered going first.
    */
  val cardCacheSize: com.histmersion.web.ui.Layout.CardCacheSize = com.histmersion.web.ui.Layout.CardCacheSize(300)

  /** Identifier lookups ([[com.histmersion.web.IdentLink]]) and gazetteer labels kept. */
  val identCacheSize: com.histmersion.web.ui.Layout.IdentCacheSize = com.histmersion.web.ui.Layout.IdentCacheSize(1000)
  val placeLabelCacheSize: com.histmersion.web.ui.Layout.PlaceLabelCacheSize = com.histmersion.web.ui.Layout.PlaceLabelCacheSize(5000)

  /** Quiet period before a sidebar tab (articles, events) refetches after a filter change. */
  val tabReloadMs: com.histmersion.web.ui.Layout.TabReloadMs = com.histmersion.web.ui.Layout.TabReloadMs(150)

  /** How many rows the Articles and Events tabs fetch at once; past them, "show more" fetches the next page
    * ([[FilterTab]]).
    */
  val tabFetch: com.histmersion.web.ui.Layout.TabFetch = com.histmersion.web.ui.Layout.TabFetch(200)

  /** How many rows "show more" reveals in the Articles and Events tabs. */
  val tabPage: com.histmersion.web.ui.Layout.TabPage = com.histmersion.web.ui.Layout.TabPage(25)

  /** How many rows a page of Library's tables holds. */
  val libraryPage: com.histmersion.web.ui.Layout.LibraryPage = com.histmersion.web.ui.Layout.LibraryPage(50)

  /** How many of a source's findings Library's reading pane lists before "All N findings". */
  val readingFindings: com.histmersion.web.ui.Layout.ReadingFindings = com.histmersion.web.ui.Layout.ReadingFindings(3)

  /** How much of an article's opening paragraph the reading pane shows, in characters. */
  val readingOpening: com.histmersion.web.ui.Layout.ReadingOpening = com.histmersion.web.ui.Layout.ReadingOpening(600)

  /** How long the pointer rests on a map place before its card is fetched and shown. */
  val hoverDelayMs: com.histmersion.web.ui.Layout.HoverDelayMs = com.histmersion.web.ui.Layout.HoverDelayMs(180)

  /** The quiet period after the page's own change before the toolbar's counters are re-read (Notices). */
  val countersRefreshMs: com.histmersion.web.ui.Layout.CountersRefreshMs = com.histmersion.web.ui.Layout.CountersRefreshMs(250)
