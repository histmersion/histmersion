package com.histmersion
package web

import com.histmersion.web.map.*
import org.scalajs.dom

import scala.scalajs.js
import com.histmersion.api.catalogue.SiteTypes
import com.histmersion.api.catalogue.Place
import com.histmersion.api.UiLabels
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** A site from the places feed (`/api/places/geo?kind=site`). */
final case class SitePoint(
    id: Option[SitePoint.Id],
    label: SitePoint.Label,
    kind: SitePoint.Kind,
    siteType: Option[SitePoint.SiteType],
    basis: Option[SitePoint.Basis],
    lat: LatLon.Lat,
    lon: LatLon.Lon,
    period: Option[(PlaybackAreas.FractionalYear, PlaybackAreas.FractionalYear)],
    undated: SitePoint.Undated = SitePoint.Undated(false)
):
  def name: UiLabels.Text = UiLabels.Text(if label.value.nonEmpty then label.value else id.fold("site")(_.value))
  def mid: Option[PlaybackAreas.FractionalYear] = period.map((a, b) => PlaybackAreas.FractionalYear((a.value + b.value) / 2))

  /** Its type, when it is one the map knows. */
  def known: Option[SiteTypes.SiteType] = siteType.flatMap(t => SiteTypes.get(SiteTypes.SiteType.Id(t.value)))

object SitePoint:

  type Id = Id.T
  object Id extends RefinedType[String, Pure]
  type Label = Label.T
  object Label extends RefinedType[String, Pure]
  type Kind = Kind.T
  object Kind extends RefinedType[String, Pure]
  type SiteType = SiteType.T
  object SiteType extends RefinedType[String, Pure]
  type Basis = Basis.T
  object Basis extends RefinedType[String, Pure]
  type Undated = Undated.T
  object Undated extends RefinedType[Boolean, Pure]

  private def str(v: js.Dynamic): Option[String] = if js.isUndefined(v) || v == null then None else Some(v.toString)

  /** A GeoJSON point feature of the places feed. */
  def of(f: js.Dynamic): SitePoint =
    val p = f.properties
    val c = f.geometry.coordinates
    val ps = p.selectDynamic("periodStart")
    val pe = p.selectDynamic("periodEnd")
    val period = if js.isUndefined(ps) || js.isUndefined(pe) || ps == null || pe == null then None
    else Some((PlaybackAreas.FractionalYear(ps.asInstanceOf[Double]), PlaybackAreas.FractionalYear(pe.asInstanceOf[Double])))
    SitePoint(
      str(p.id).map(Id(_)),
      Label(str(p.label).getOrElse("")),
      Kind(str(p.kind).getOrElse(Place.Kind.Site)),
      str(p.siteType).map(SiteType(_)),
      str(p.basis).map(Basis(_)),
      LatLon.Lat(c.selectDynamic("1").asInstanceOf[Double]),
      LatLon.Lon(c.selectDynamic("0").asInstanceOf[Double]),
      period
    )
