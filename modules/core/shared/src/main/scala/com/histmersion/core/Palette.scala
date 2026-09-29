package com.histmersion.core

/** The NAMES of the colour-coded categories. Their colours are the stylesheet's: every name here has
  * a custom property in `web/style.css` (`--edge-cites`, `--status-current`, …, and a `-default` for
  * anything unlisted), which `CssTokensSuite` (server) checks. Code refers to a colour by its token — as
  * `var(--…)` in markup, or through the web UI's `ThemeTokens` where it must compute with the value (the graph's
  * canvas) — so a theme is a change to the stylesheet alone. */
object Palette:
  val edgeKinds: List[String] = List("references", "challenges", "refutes", "updates", "superseded_by",
    "supersedes", "confirms", "cites", "foundational_for", "compilation_of", "review_of", "verifies")
  val statuses: List[String] =
    List("foundational", "current", "contested", "superseded", "debunked", "retracted", "unassessed")
  val mediaKinds: List[String] = List("figure", "photo", "map", "plan", "scan")
  val confidences: List[String] = List("high", "medium", "low", "contested")
  /** The period ramp's stops, `--ramp-0` … `--ramp-4` (viridis by default). */
  val rampStops: Int = 5

  private def token(family: String, known: List[String], name: String): String =
    if known.contains(name) then s"--$family-$name" else s"--$family-default"

  def edge(kind: String): String     = token("edge", edgeKinds, kind)
  def status(status: String): String = token("status", statuses, status)
  def media(kind: String): String    = token("media", mediaKinds, kind)
  def confidence(level: String): String = s"--conf-$level"

  /** Every token the palettes name — what the stylesheet must define. */
  def allTokens: List[String] =
    (edgeKinds.map(edge) :+ "--edge-default") ++ (statuses.map(status) :+ "--status-default") ++
      (mediaKinds.map(media) :+ "--media-default") ++ confidences.map(confidence) ++
      (0 until rampStops).map(i => s"--ramp-$i") ++
      List("--graph-hover", "--graph-label", "--graph-label-hover", "--place-pick", "--place-preview")
