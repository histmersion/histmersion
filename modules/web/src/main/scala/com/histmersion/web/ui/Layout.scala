package com.histmersion
package web.ui

import io.github.iltotore.iron.constraint.any.Pure

/** The UI's behavioural numbers: drag limits, fetch limits, timings, layout maths. Colours and looks are the
  * stylesheet's (`web/style.css`), not these.
  */
object Layout:
  type HandleWidth = HandleWidth.T
  object HandleWidth extends Newtype[Double, Pure]
  type MarkerRadiusDefault = MarkerRadiusDefault.T
  object MarkerRadiusDefault extends Newtype[Int, Pure]
  type MaxBuckets = MaxBuckets.T
  object MaxBuckets extends Newtype[Long, Pure]
  type ResizeSettleMs = ResizeSettleMs.T
  object ResizeSettleMs extends Newtype[Int, Pure]
  type MinSpan = MinSpan.T
  object MinSpan extends Newtype[Long, Pure]
  type WindowMaxWidth = WindowMaxWidth.T
  object WindowMaxWidth extends Newtype[Double, Pure]
  type WindowMaxHeight = WindowMaxHeight.T
  object WindowMaxHeight extends Newtype[Double, Pure]
  type MergeWindowMaxWidth = MergeWindowMaxWidth.T
  object MergeWindowMaxWidth extends Newtype[Double, Pure]
  type MergeWindowMaxHeight = MergeWindowMaxHeight.T
  object MergeWindowMaxHeight extends Newtype[Double, Pure]
  type WindowCascadeOffset = WindowCascadeOffset.T
  object WindowCascadeOffset extends Newtype[Int, Pure]
  type WindowCascadeSlots = WindowCascadeSlots.T
  object WindowCascadeSlots extends Newtype[Int, Pure]
  type PickerMax = PickerMax.T
  object PickerMax extends Newtype[Int, Pure]
  type SpiderMax = SpiderMax.T
  object SpiderMax extends Newtype[Int, Pure]
  type FitMaxZoom = FitMaxZoom.T
  object FitMaxZoom extends Newtype[Int, Pure]
  type AreaLabelMinZoom = AreaLabelMinZoom.T
  object AreaLabelMinZoom extends Newtype[Int, Pure]
  type OverlayFetchLimit = OverlayFetchLimit.T
  object OverlayFetchLimit extends Newtype[Int, Pure]
  type OverlayRangeFetchLimit = OverlayRangeFetchLimit.T
  object OverlayRangeFetchLimit extends Newtype[Int, Pure]
  type ClusterAnimateMax = ClusterAnimateMax.T
  object ClusterAnimateMax extends Newtype[Int, Pure]
  type PlaybackWindowHalf = PlaybackWindowHalf.T
  object PlaybackWindowHalf extends Newtype[Long, Pure]
  type PlaybackDuration = PlaybackDuration.T
  object PlaybackDuration extends Newtype[Double, Pure]
  type PlaybackFetchSeconds = PlaybackFetchSeconds.T
  object PlaybackFetchSeconds extends Newtype[Double, Pure]
  type PlaybackFetchLimit = PlaybackFetchLimit.T
  object PlaybackFetchLimit extends Newtype[Int, Pure]
  type PlaybackLeadSeconds = PlaybackLeadSeconds.T
  object PlaybackLeadSeconds extends Newtype[Double, Pure]
  type PlaybackAreaFadeSeconds = PlaybackAreaFadeSeconds.T
  object PlaybackAreaFadeSeconds extends Newtype[Double, Pure]
  type ExitAnimMs = ExitAnimMs.T
  object ExitAnimMs extends Newtype[Int, Pure]
  type LayerReloadMs = LayerReloadMs.T
  object LayerReloadMs extends Newtype[Int, Pure]
  type RangeReloadMs = RangeReloadMs.T
  object RangeReloadMs extends Newtype[Int, Pure]
  type GeoCacheSize = GeoCacheSize.T
  object GeoCacheSize extends Newtype[Int, Pure]
  type GeoCacheChars = GeoCacheChars.T
  object GeoCacheChars extends Newtype[Long, Pure]
  type CardCacheSize = CardCacheSize.T
  object CardCacheSize extends Newtype[Int, Pure]
  type IdentCacheSize = IdentCacheSize.T
  object IdentCacheSize extends Newtype[Int, Pure]
  type PlaceLabelCacheSize = PlaceLabelCacheSize.T
  object PlaceLabelCacheSize extends Newtype[Int, Pure]
  type TabReloadMs = TabReloadMs.T
  object TabReloadMs extends Newtype[Int, Pure]
  type TabFetch = TabFetch.T
  object TabFetch extends Newtype[Int, Pure]
  type TabPage = TabPage.T
  object TabPage extends Newtype[Int, Pure]
  type LibraryPage = LibraryPage.T
  object LibraryPage extends Newtype[Int, Pure]
  type ReadingFindings = ReadingFindings.T
  object ReadingFindings extends Newtype[Int, Pure]
  type ReadingOpening = ReadingOpening.T
  object ReadingOpening extends Newtype[Int, Pure]
  type HoverDelayMs = HoverDelayMs.T
  object HoverDelayMs extends Newtype[Int, Pure]
  type CountersRefreshMs = CountersRefreshMs.T
  object CountersRefreshMs extends Newtype[Int, Pure]

  val sidebarMinWidth: Px = Px(320)
  val sidebarMaxWidth: Px = Px(800)
  val sidebarDefault: Px = Px(480)
  val overviewHeight: Px = Px(44)
  val overviewBase: Px = Px(30)
  val detailHeight: Px = Px(58)
  val detailBase: Px = Px(40)
  val handleWidth: HandleWidth = HandleWidth(6.0)
  val markerRadiusDefault: MarkerRadiusDefault = MarkerRadiusDefault(5)
  val maxBuckets: MaxBuckets = MaxBuckets(1500L)

  /** Quiet period after the last resize event before a curve is redrawn exactly. */
  val resizeSettleMs: ResizeSettleMs = ResizeSettleMs(120)
  val minSpan: MinSpan = MinSpan(10L)
  val windowMinWidth: Px = Px(300)
  val windowMinHeight: Px = Px(200)
  val windowMaxWidth: WindowMaxWidth = WindowMaxWidth(780.0)
  val windowMaxHeight: WindowMaxHeight = WindowMaxHeight(600.0)

  /** The merge review screen's window: wide enough for a field table and a diff3 view side by side. */
  val mergeWindowMaxWidth: MergeWindowMaxWidth = MergeWindowMaxWidth(1500.0)
  val mergeWindowMaxHeight: MergeWindowMaxHeight = MergeWindowMaxHeight(1000.0)
  val windowCascadeOffset: WindowCascadeOffset = WindowCascadeOffset(28)

  /** The least room under the map options' button for the panel to open there on a tablet; with less, and more above,
    * it opens over the button.
    */
  val mapOptionsMinBelow: Px = Px(280)
  val windowCascadeSlots: WindowCascadeSlots = WindowCascadeSlots(5)
  val pickerMax: PickerMax = PickerMax(15)

  /** The most dots a bubble no zoom parts is fanned out into around its spot (more: listed only). */
  val spiderMax: SpiderMax = SpiderMax(40)
  val fitMaxZoom: FitMaxZoom = FitMaxZoom(9)

  /** Below this zoom a region or polity known only by a point (no boundary yet) is not labelled: zoomed out, one name
    * at one point would stand for a whole country ([[com.histmersion.web.PlaceGlyph]]).
    */
  val areaLabelMinZoom: AreaLabelMinZoom = AreaLabelMinZoom(5)

  /** A picture's marker: a landscape "photo print" badge (4:3), its white mat included. */
  val mediaMarkerWidth: Px = Px(32)
  val mediaMarkerHeight: Px = Px(24)

  /** A finding's diamond: its box is larger than a site's circle or an event's square, so the diamond inside (70 % of
    * the box, turned 45°) looks as big as they do and still holds a 15 px glyph.
    */
  val findingMarkerSize: Px = Px(34)
  val overlayFetchLimit: OverlayFetchLimit = OverlayFetchLimit(400) // polities alive in one year: up to ~140 (1200 CE); after de-duplication
  /** The areas over a whole range ("All states in period"): every state alive in it — 551 in 1000..1500, 1 744 over
    * -3000..2000 (~7 MB of stored simplified outlines, V090); the server caps it at `places.geo-max-limit`.
    */
  val overlayRangeFetchLimit: OverlayRangeFetchLimit = OverlayRangeFetchLimit(2000)

  /** The events layer (#1449): its markers' size (a square: smaller than the circle's box, as big to the eye).
    */
  val eventMarkerSize: Px = Px(24)

  /** The most markers (points drawn alone and bubbles, in view and its margin) the map's cluster layer draws before and
    * after a zoom and still animates it (bubbles splitting and merging): above it a zoom redraws them at once
    * ([[com.histmersion.web.map.ClusterMotion]]). Counted on screen, not among the points held: a wide window's view
    * holds ~2 000 events and findings and draws 200–500 markers.
    */
  val clusterAnimateMax: ClusterAnimateMax = ClusterAnimateMax(1000)

  /** A site's badge (#1449): its type's icon, the exclamation mark when the type is unknown. */
  val siteIconSize: Px = Px(26)
  val playbackWindowHalf: PlaybackWindowHalf = PlaybackWindowHalf(50L)
  val playbackDuration: PlaybackDuration = PlaybackDuration(30.0)

  /** Playback's findings are fetched this many seconds of playback ahead, at most [[playbackFetchLimit]] a request, and
    * cut to each frame's window locally (#3764).
    */
  val playbackFetchSeconds: PlaybackFetchSeconds = PlaybackFetchSeconds(4.0)
  val playbackFetchLimit: PlaybackFetchLimit = PlaybackFetchLimit(2000)

  /** Playback's states are fetched this many seconds of playback ahead of the playhead, and each fades in and out over
    * this many seconds of playback at its first and last year (#3764).
    */
  val playbackLeadSeconds: PlaybackLeadSeconds = PlaybackLeadSeconds(0.5)
  val playbackAreaFadeSeconds: PlaybackAreaFadeSeconds = PlaybackAreaFadeSeconds(0.3)
  val exitAnimMs: ExitAnimMs = ExitAnimMs(350)

  /** Minimum gap between overlay-layer (regions, sites, media) refetches while the span moves. */
  val layerReloadMs: LayerReloadMs = LayerReloadMs(200)

  /** The quiet period after the span's last move before the boundaries over a whole range ("All states in period",
    * megabytes an answer) are fetched again (#1949).
    */
  val rangeReloadMs: RangeReloadMs = RangeReloadMs(250)

  /** Place GeoJSON responses kept in memory, keyed by (kind, as-of year) — at most this many, and at most
    * [[geoCacheChars]] characters of them together: a year's polities are 0.1–2 MB, a whole range's ~7 MB (#1949).
    */
  val geoCacheSize: GeoCacheSize = GeoCacheSize(48)
  val geoCacheChars: GeoCacheChars = GeoCacheChars(24L * 1024 * 1024)

  /** Hover cards kept per kind (sources, articles, places, events), the least recently hovered going first.
    */
  val cardCacheSize: CardCacheSize = CardCacheSize(300)

  /** Identifier lookups ([[com.histmersion.web.IdentLink]]) and gazetteer labels kept. */
  val identCacheSize: IdentCacheSize = IdentCacheSize(1000)
  val placeLabelCacheSize: PlaceLabelCacheSize = PlaceLabelCacheSize(5000)

  /** Quiet period before a sidebar tab (articles, events) refetches after a filter change. */
  val tabReloadMs: TabReloadMs = TabReloadMs(150)

  /** How many rows the Articles and Events tabs fetch at once; past them, "show more" fetches the next page
    * ([[FilterTab]]).
    */
  val tabFetch: TabFetch = TabFetch(200)

  /** How many rows "show more" reveals in the Articles and Events tabs. */
  val tabPage: TabPage = TabPage(25)

  /** How many rows a page of Library's tables holds. */
  val libraryPage: LibraryPage = LibraryPage(50)

  /** How many of a source's findings Library's reading pane lists before "All N findings". */
  val readingFindings: ReadingFindings = ReadingFindings(3)

  /** How much of an article's opening paragraph the reading pane shows, in characters. */
  val readingOpening: ReadingOpening = ReadingOpening(600)

  /** How long the pointer rests on a map place before its card is fetched and shown. */
  val hoverDelayMs: HoverDelayMs = HoverDelayMs(180)

  /** The quiet period after the page's own change before the toolbar's counters are re-read (Notices). */
  val countersRefreshMs: CountersRefreshMs = CountersRefreshMs(250)
