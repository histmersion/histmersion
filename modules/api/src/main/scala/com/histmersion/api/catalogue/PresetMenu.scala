package com.histmersion
package api.catalogue

import com.histmersion.api.BBox
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
object PresetMenu:
  type PeriodSource = PeriodSource.T
  object PeriodSource extends Newtype[String, Pure]
  type PeriodoId = PeriodoId.T
  object PeriodoId extends Newtype[String, Pure]
  type Share = Share.T
  object Share extends Newtype[Double, Pure]

  final case class Group(region: PresetRegion, presets: List[HistoricalPeriod], inView: Boolean)

  val MinShare: Share = Share(0.5)

  def share(view: BBox, box: BBox): Share =
    val area = (view.maxLon.value - view.minLon.value) * (view.maxLat.value - view.minLat.value)
    if view.crossesAntimeridian || area <= 0 then Share(0.0)
    else
      val w = math.min(view.maxLon.value, box.maxLon.value) - math.max(view.minLon.value, box.minLon.value)
      val h = math.min(view.maxLat.value, box.maxLat.value) - math.max(view.minLat.value, box.minLat.value)
      if w <= 0 || h <= 0 then Share(0.0) else Share((w * h) / area)

  def groups(ps: List[HistoricalPeriod], view: Option[BBox] = None): List[Group] =
    val byRegion = ps.flatMap(p => p.preset.map(_ -> p)).groupMap(_._1)(_._2)
    def cover(r: PresetRegion) = (for v <- view; b <- r.box yield share(v, b)).getOrElse(Share(0.0))
    PresetRegion.values.toList
      .flatMap { r =>
        byRegion
          .get(r)
          .map(rows =>
            Group(
              r,
              rows.sortBy(p => (p.period.start.value: Long, p.period.end.value: Long, p.label.value: String)),
              cover(r).value >= MinShare.value
            )
          )
      }
      .sortBy(g => if g.inView then (0, -cover(g.region).value) else (1, 0.0))

  def source(p: HistoricalPeriod): Option[PeriodSource] =
    p.authority.map(a =>
      a.value.split(" · ", 2) match
        case Array(id, rest) if id.startsWith("PeriodO ") => PeriodSource(rest)
        case _                                            => PeriodSource(a.value)
    )

  /** A PeriodO definition can be looked up at `https://n2t.net/ark:/99152/<id>`. */
  def periodoId(p: HistoricalPeriod): Option[PeriodoId] =
    p.authority.flatMap(a =>
      Option.when(a.value.startsWith("PeriodO "))(PeriodoId(a.value.drop("PeriodO ".length).takeWhile(!_.isWhitespace)))
    )
