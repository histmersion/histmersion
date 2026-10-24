package com.histmersion.api

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
      List("--graph-hover", "--graph-label", "--graph-label-hover", "--place-pick", "--place-preview") ++
      mapLabelTokens

  /** The map's place-name colours, for light tiles and for dark: ink, halo and the small note line. */
  val mapLabelTokens: List[String] =
    for tone <- List("light", "dark"); part <- List("ink", "halo", "note") yield s"--map-label-$part-$tone"

/** WCAG 2.x contrast, for holding the stylesheet's text colours to a ratio in a test. Pure. */
object Wcag:
  /** Relative luminance of an sRGB colour given as 0–255 components. */
  def luminance(r: Int, g: Int, b: Int): Double =
    def lin(c: Int): Double =
      val v = c / 255.0
      if v <= 0.03928 then v / 12.92 else math.pow((v + 0.055) / 1.055, 2.4)
    0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)

  /** `#rrggbb` / `#rgb` → components; anything else is `None`. */
  def hex(s: String): Option[(Int, Int, Int)] =
    val h = s.trim.stripPrefix("#")
    val full = if h.length == 3 then h.flatMap(c => s"$c$c") else h
    Option.when(full.length == 6 && full.forall(c => Character.digit(c, 16) >= 0))(
      (Integer.parseInt(full.take(2), 16), Integer.parseInt(full.slice(2, 4), 16), Integer.parseInt(full.drop(4), 16)))

  /** The contrast ratio of two colours, 1 (none) to 21 (black on white). */
  def contrast(a: (Int, Int, Int), b: (Int, Int, Int)): Double =
    val la = luminance(a._1, a._2, a._3)
    val lb = luminance(b._1, b._2, b._3)
    (math.max(la, lb) + 0.05) / (math.min(la, lb) + 0.05)

  /** AA for normal text; large text (≥ 18.66 px bold) needs 3. */
  val AaText: Double = 4.5
