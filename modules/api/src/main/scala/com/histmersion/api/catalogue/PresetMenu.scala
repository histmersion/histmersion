package com.histmersion.api.catalogue

import com.histmersion.api.BBox
import com.histmersion.api.given
object PresetMenu:

  type MinShare = MinShareValue
  type MinShareValue = MinShareValue.T
  object MinShareValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ShareResult = ShareResult.T
  object ShareResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceResult = SourceResult.T
  object SourceResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodoIdResult = PeriodoIdResult.T
  object PeriodoIdResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Group(region: PresetRegion, presets: List[HistoricalPeriod], inView: Group.InView)

  object Group:
    type InView = InView.T
    object InView extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val MinShare: com.histmersion.api.catalogue.PresetMenu.MinShare = _root_.com.histmersion.api.catalogue.PresetMenu.MinShareValue(0.5)

  def share(view: BBox, box: BBox): com.histmersion.api.catalogue.PresetMenu.ShareResult =
    val area = (view.maxLon - view.minLon) * (view.maxLat - view.minLat)
    if ((view.crossesAntimeridian) == _root_.com.histmersion.api.BBox.CrossesAntimeridianResult(true)) || area <= 0 then _root_.com.histmersion.api.catalogue.PresetMenu.ShareResult(0.0)
    else
      val w = math.min(view.maxLon, box.maxLon) - math.max(view.minLon, box.minLon)
      val h = math.min(view.maxLat, box.maxLat) - math.max(view.minLat, box.minLat)
      if w <= 0 || h <= 0 then _root_.com.histmersion.api.catalogue.PresetMenu.ShareResult(0.0) else (w * h) / area

  def groups(ps: List[HistoricalPeriod], view: Option[BBox] = None): List[Group] =
    val byRegion = ps.flatMap(p => p.preset.map(_ -> p)).groupMap(_._1)(_._2)
    def cover(r: PresetRegion) = (for v <- view; b <- r.box yield share(v, b)).getOrElse(0.0)
    PresetRegion.values.toList
      .flatMap { r =>
        byRegion
          .get(r)
          .map(rows => Group(r, rows.sortBy(p => (p.period.start, p.period.end, p.label)), cover(r) >= MinShare))
      }
      .sortBy(g => if ((g.inView) == _root_.com.histmersion.api.catalogue.PresetMenu.Group.InView(true)) then (0, -cover(g.region)) else (1, 0.0))

  def source(p: HistoricalPeriod): Option[com.histmersion.api.catalogue.PresetMenu.SourceResult] =
    p.authority.map(a =>
      a.split(" · ", 2) match
        case Array(id, rest) if id.startsWith("PeriodO ") => rest
        case _                                            => a
    )

  /** A PeriodO definition can be looked up at `https://n2t.net/ark:/99152/<id>`. */
  def periodoId(p: HistoricalPeriod): Option[com.histmersion.api.catalogue.PresetMenu.PeriodoIdResult] =
    p.authority.flatMap(a =>
      Option.when(a.startsWith("PeriodO "))(a.drop("PeriodO ".length).takeWhile(!_.isWhitespace))
    )
