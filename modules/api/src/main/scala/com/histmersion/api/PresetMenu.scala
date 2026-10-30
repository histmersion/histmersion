package com.histmersion.api

object PresetMenu:

  final case class Group(region: PresetRegion, presets: List[HistoricalPeriod], inView: Boolean)

  val MinShare: Double = 0.5

  def share(view: BBox, box: BBox): Double =
    val area = (view.maxLon - view.minLon) * (view.maxLat - view.minLat)
    if view.crossesAntimeridian || area <= 0 then 0.0
    else
      val w = math.min(view.maxLon, box.maxLon) - math.max(view.minLon, box.minLon)
      val h = math.min(view.maxLat, box.maxLat) - math.max(view.minLat, box.minLat)
      if w <= 0 || h <= 0 then 0.0 else (w * h) / area

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

  def source(p: HistoricalPeriod): Option[String] =
    p.authority.map(a =>
      a.split(" · ", 2) match
        case Array(id, rest) if id.startsWith("PeriodO ") => rest
        case _                                            => a
    )

  /** A PeriodO definition can be looked up at `https://n2t.net/ark:/99152/<id>`. */
  def periodoId(p: HistoricalPeriod): Option[String] =
    p.authority.flatMap(a =>
      Option.when(a.startsWith("PeriodO "))(a.drop("PeriodO ".length).takeWhile(!_.isWhitespace))
    )
