package com.histmersion.web

import com.histmersion.web.map.*
import org.scalajs.dom

import scala.scalajs.js
import com.histmersion.api.library.GeoHit

/** Findings at one point: one marker, counting them all (a point holds several claims). */
final case class Spot(lat: Spot.Lat, lon: Spot.Lon, items: List[GeoHit])

object Spot:
  /** Pure: findings grouped by their exact point, in the order they came. */
  type Lat = Lat.T
  object Lat extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Lon = Lon.T
  object Lon extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def of(points: Iterable[GeoHit]): List[Spot] =
    val spots =
      scala.collection.mutable.LinkedHashMap.empty[(Double, Double), scala.collection.mutable.ListBuffer[GeoHit]]
    for pt <- points; lat <- pt.lat; lon <- pt.lon do
      spots.getOrElseUpdate((lat, lon), scala.collection.mutable.ListBuffer.empty) += pt
    spots.toList.map { case ((lat, lon), items) => Spot(lat, lon, items.toList) }
