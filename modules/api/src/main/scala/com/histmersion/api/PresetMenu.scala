package com.histmersion.api

/** The timeline's preset menu, as data: the presets grouped under their region headings — the heading(s) the map is
  * looking at first — and what each row says about where it applies and who defines it. Pure, so the web menu and its
  * tests share it.
  */
object PresetMenu:

  /** A heading and its presets, oldest first; `inView` when the map mostly shows that region. */
  final case class Group(region: PresetRegion, presets: List[HistoricalPeriod], inView: Boolean)

  /** How much of the map must lie inside a region's box for that region to come first. */
  val MinShare: Double = 0.5

  /** The fraction of `view`'s area that lies inside `box` (0 for a view across the antimeridian or with no area — such
    * a view is not "in" any one region).
    */
  def share(view: BBox, box: BBox): Double =
    val area = (view.maxLon - view.minLon) * (view.maxLat - view.minLat)
    if view.crossesAntimeridian || area <= 0 then 0.0
    else
      val w = math.min(view.maxLon, box.maxLon) - math.max(view.minLon, box.minLon)
      val h = math.min(view.maxLat, box.maxLat) - math.max(view.minLat, box.minLat)
      if w <= 0 || h <= 0 then 0.0 else (w * h) / area

  /** The presets (rows with a `preset` heading) grouped by heading. Headings the map is mostly showing come first, the
    * best covered first; the rest keep [[PresetRegion]]'s order.
    */
  def groups(ps: List[HistoricalPeriod], view: Option[BBox] = None): List[Group] =
    val byRegion = ps.flatMap(p => p.preset.map(_ -> p)).groupMap(_._1)(_._2)
    def cover(r: PresetRegion) = (for v <- view; b <- r.box yield share(v, b)).getOrElse(0.0)
    PresetRegion.values.toList
      .flatMap { r =>
        byRegion
          .get(r)
          .map(rows => Group(r, rows.sortBy(p => (p.period.start, p.period.end, p.label)), cover(r) >= MinShare))
      }
      .sortBy(g => if g.inView then (0, -cover(g.region)) else (1, 0.0))

  /** Who defines a preset, short: the authority's title without the PeriodO id in front (`PeriodO p0ds9qjq7b2 · The
    * British School at Athens (2022)` → `The British School at Athens (2022)`).
    */
  def source(p: HistoricalPeriod): Option[String] =
    p.authority.map(a =>
      a.split(" · ", 2) match
        case Array(id, rest) if id.startsWith("PeriodO ") => rest
        case _                                            => a
    )

  /** The PeriodO id a row cites, when it cites one — the definition can be looked up at
    * `https://n2t.net/ark:/99152/<id>`.
    */
  def periodoId(p: HistoricalPeriod): Option[String] =
    p.authority.flatMap(a =>
      Option.when(a.startsWith("PeriodO "))(a.drop("PeriodO ".length).takeWhile(!_.isWhitespace))
    )
