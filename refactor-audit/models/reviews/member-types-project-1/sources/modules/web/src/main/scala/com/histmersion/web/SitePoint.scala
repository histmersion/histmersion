package com.histmersion.web

import com.histmersion.web.map.*
import org.scalajs.dom

import scala.scalajs.js
import com.histmersion.api.catalogue.SiteTypes
import com.histmersion.api.catalogue.Place

/** A site from the places feed (`/api/places/geo?kind=site`). */
final case class SitePoint(
    id: Option[SitePoint.Id],
    label: SitePoint.Label,
    kind: SitePoint.Kind,
    siteType: Option[SitePoint.SiteType],
    basis: Option[SitePoint.Basis],
    lat: SitePoint.Lat,
    lon: SitePoint.Lon,
    period: Option[(SitePoint.PeriodPart1, SitePoint.PeriodPart2)],
    undated: SitePoint.Undated = false
):
  def name: com.histmersion.web.SitePoint.NameResult = if label.nonEmpty then label else id.getOrElse("site")
  def mid: Option[com.histmersion.web.SitePoint.MidResult] = period.map((a, b) => (a + b) / 2)

  /** Its type, when it is one the map knows. */
  def known: Option[SiteTypes.SiteType] = siteType.flatMap(SiteTypes.get)

object SitePoint:
  type NameResult = NameResult.T
  object NameResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MidResult = MidResult.T
  object MidResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StrResult = StrResult.T
  object StrResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SiteType = SiteType.T
  object SiteType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Basis = Basis.T
  object Basis extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Lat = Lat.T
  object Lat extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Lon = Lon.T
  object Lon extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodPart1 = PeriodPart1.T
  object PeriodPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodPart2 = PeriodPart2.T
  object PeriodPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Undated = Undated.T
  object Undated extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private def str(v: js.Dynamic): Option[com.histmersion.web.SitePoint.StrResult] = if js.isUndefined(v) || v == null then None else Some(v.toString)

  /** A GeoJSON point feature of the places feed. */
  def of(f: js.Dynamic): SitePoint =
    val p = f.properties
    val c = f.geometry.coordinates
    val ps = p.selectDynamic("periodStart")
    val pe = p.selectDynamic("periodEnd")
    val period = if js.isUndefined(ps) || js.isUndefined(pe) || ps == null || pe == null then None
    else Some((ps.asInstanceOf[Double], pe.asInstanceOf[Double]))
    SitePoint(
      str(p.id),
      str(p.label).getOrElse(""),
      str(p.kind).getOrElse(Place.Kind.Site),
      str(p.siteType),
      str(p.basis),
      c.selectDynamic("1").asInstanceOf[Double],
      c.selectDynamic("0").asInstanceOf[Double],
      period
    )
