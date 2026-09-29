package com.histmersion.web.ui

/** The graph's force layout and label sizes (its colours are `--graph-*` tokens). */
object GraphLayout:
  val repulsion: Double       = 3000.0
  val springK: Double         = 0.008
  val restLen: Double         = 120.0
  val damping: Double         = 0.92
  val centering: Double       = 0.01
  val maxTicks: Int           = 300
  val rootRadius: Double      = 8.0
  val depth1Radius: Double    = 6.0
  val leafRadius: Double      = 4.5
  val labelMaxLen: Int        = 30
  val labelTruncLen: Int      = 28
