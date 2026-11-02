package com.histmersion.web

import com.histmersion.web.map.*
import org.scalajs.dom

import scala.scalajs.js
import com.histmersion.api.library.GeoHit

/** Findings at one point: one marker, counting them all (a point holds several claims). */
final case class Spot(lat: Double, lon: Double, items: List[GeoHit])

object Spot:
  /** Pure: findings grouped by their exact point, in the order they came. */
  def of(points: Iterable[GeoHit]): List[Spot] =
    val spots =
      scala.collection.mutable.LinkedHashMap.empty[(Double, Double), scala.collection.mutable.ListBuffer[GeoHit]]
    for pt <- points; lat <- pt.lat; lon <- pt.lon do
      spots.getOrElseUpdate((lat, lon), scala.collection.mutable.ListBuffer.empty) += pt
    spots.toList.map { case ((lat, lon), items) => Spot(lat, lon, items.toList) }
