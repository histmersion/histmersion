package com.histmersion
package web

import com.histmersion.web.map.*
import org.scalajs.dom

import scala.scalajs.js
import com.histmersion.api.library.GeoHit
import com.histmersion.api.catalogue.Latitude
import com.histmersion.api.catalogue.Longitude

/** Findings at one point: one marker, counting them all (a point holds several claims). */
final case class Spot(lat: Latitude, lon: Longitude, items: List[GeoHit])

object Spot:
  /** Pure: findings grouped by their exact point, in the order they came. */

  def of(points: Iterable[GeoHit]): List[Spot] =
    val spots =
      scala.collection.mutable.LinkedHashMap.empty[(Latitude, Longitude), scala.collection.mutable.ListBuffer[GeoHit]]
    for pt <- points; lat <- pt.lat; lon <- pt.lon do
      spots.getOrElseUpdate((lat, lon), scala.collection.mutable.ListBuffer.empty) += pt
    spots.toList.map { case ((lat, lon), items) => Spot(lat, lon, items.toList) }
