package com.histmersion.web.ui

object EdgeColors:
  val palette: Map[String, CssColor] = Map(
    "references"       -> CssColor("#6ea8fe"),
    "challenges"       -> CssColor("#f0b7b2"),
    "refutes"          -> CssColor("#e06660"),
    "updates"          -> CssColor("#7dc97d"),
    "superseded_by"    -> CssColor("#c4574f"),
    "supersedes"       -> CssColor("#c4574f"),
    "confirms"         -> CssColor("#5cb85c"),
    "cites"            -> CssColor("#9aa3b2"),
    "foundational_for" -> CssColor("#f0c040"),
    "compilation_of"   -> CssColor("#b060d0"),
    "review_of"        -> CssColor("#60b0d0"),
    "verifies"         -> CssColor("#5cb85c")
  )

  val default: CssColor = CssColor("#9aa3b2")

  def apply(kind: String): String = palette.getOrElse(kind, default).value

object StatusColors:
  val palette: Map[String, CssColor] = Map(
    "foundational" -> CssColor("#f0c040"),
    "current"      -> CssColor("#6ea8fe"),
    "contested"    -> CssColor("#f0b7b2"),
    "superseded"   -> CssColor("#9aa3b2"),
    "debunked"     -> CssColor("#e06660"),
    "retracted"    -> CssColor("#c4574f"),
    "unassessed"   -> CssColor("#666666")
  )

  val default: CssColor = CssColor("#666666")

  def apply(status: String): String = palette.getOrElse(status, default).value

object MediaKindColors:
  val palette: Map[String, CssColor] = Map(
    "figure" -> CssColor("#6ea8fe"),
    "photo"  -> CssColor("#7dc97d"),
    "map"    -> CssColor("#f0c040"),
    "plan"   -> CssColor("#b060d0"),
    "scan"   -> CssColor("#9aa3b2")
  )

  val default: CssColor = CssColor("#9aa3b2")

  def apply(kind: String): String = palette.getOrElse(kind, default).value

object OverlayColors:
  val boundary: CssColor     = CssColor("#f0c040")
  val boundaryFillOpacity    = 0.08
  val boundaryStrokeOpacity  = 0.7
  val boundaryWeight         = 1.5

object ViridisStops:
  val stops: Array[(Int, Int, Int)] =
    Array((68, 1, 84), (59, 82, 139), (33, 145, 140), (94, 201, 98), (253, 231, 37))

object ConfidenceSaturation:
  val levels: Map[String, Double] =
    Map("high" -> 1.0, "medium" -> 0.7, "low" -> 0.4, "contested" -> 0.3)

  val order: Map[String, Int] =
    Map("contested" -> 0, "low" -> 1, "medium" -> 2, "high" -> 3)

object GraphLayout:
  val hoverStroke: CssColor   = CssColor("#ffffff")
  val labelColor: CssColor    = CssColor("#bbbbbb")
  val labelHover: CssColor    = CssColor("#ffffff")
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
