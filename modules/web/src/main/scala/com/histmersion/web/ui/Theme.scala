package com.histmersion.web.ui

object Theme:
  val bg: CssColor       = CssColor("#12141a")
  val panel: CssColor    = CssColor("#1b1e27")
  val ink: CssColor      = CssColor("#e6e8ee")
  val muted: CssColor    = CssColor("#9aa3b2")
  val accent: CssColor   = CssColor("#6ea8fe")
  val line: CssColor     = CssColor("#2a2f3a")
  val inputBg: CssColor  = CssColor("#0e1016")

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
  val windowCascadeOffset: Int = 28
  val windowCascadeSlots: Int  = 5
  val pickerMax: Int       = 15
  val fitMaxZoom: Int      = 9
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
