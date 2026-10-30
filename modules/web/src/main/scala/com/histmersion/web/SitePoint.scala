package com.histmersion.web

import com.histmersion.api.*
import com.histmersion.web.map.*
import org.scalajs.dom

import scala.scalajs.js
import com.histmersion.values.PlaceKind

/** A site from the places feed (`/api/places/geo?kind=site`). */
final case class SitePoint(
    id: Option[String],
    label: String,
    kind: String,
    siteType: Option[String],
    basis: Option[String],
    lat: Double,
    lon: Double,
    period: Option[(Double, Double)],
    undated: Boolean = false
):
  def name: String = if label.nonEmpty then label else id.getOrElse("site")
  def mid: Option[Double] = period.map((a, b) => (a + b) / 2)

  /** Its type, when it is one the map knows. */
  def known: Option[SiteTypes.SiteType] = siteType.flatMap(SiteTypes.get)

object SitePoint:
  private def str(v: js.Dynamic): Option[String] = if js.isUndefined(v) || v == null then None else Some(v.toString)

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
      str(p.kind).getOrElse(PlaceKind.Site),
      str(p.siteType),
      str(p.basis),
      c.selectDynamic("1").asInstanceOf[Double],
      c.selectDynamic("0").asInstanceOf[Double],
      period
    )
