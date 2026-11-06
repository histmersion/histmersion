package com.histmersion.web.map

import com.histmersion.api.instance.{MapStartConfig, MapStartMode}

import scala.util.Try

/** Where the map opens (`ui.map-start`): where this browser last left it ("last", kept per browser under
  * [[StorageKey]]) or always at the configured place.
  */
object MapStart:
  type EncodeResult = EncodeResult.T
  object EncodeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EncodeZoom = EncodeZoom.T
  object EncodeZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RoundResult = RoundResult.T
  object RoundResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RoundV = RoundV.T
  object RoundV extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RoundPlaces = RoundPlaces.T
  object RoundPlaces extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecodeResultPart2 = DecodeResultPart2.T
  object DecodeResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecodeS = DecodeS.T
  object DecodeS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InitialResultPart2 = InitialResultPart2.T
  object InitialResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InitialStored = InitialStored.T
  object InitialStored extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RememberZoom = _root_.com.histmersion.web.map.MapStart.EncodeZoom
  val RememberZoom: _root_.com.histmersion.web.map.MapStart.EncodeZoom.type = _root_.com.histmersion.web.map.MapStart.EncodeZoom

  val StorageKey = "histmersion.mapView"

  /** The view kept in the browser, as stored: `lat,lon,zoom`. Pure. */
  def encode(at: LatLon, zoom: com.histmersion.web.map.MapStart.EncodeZoom): com.histmersion.web.map.MapStart.EncodeResult =
    s"${round(at.lat, 5)},${round(at.lon, 5)},${round(zoom, 2)}"

  /** A number with at most `places` decimals, written the same in every locale. */
  private def round(v: com.histmersion.web.map.MapStart.RoundV, places: com.histmersion.web.map.MapStart.RoundPlaces): com.histmersion.web.map.MapStart.RoundResult =
    BigDecimal(v).setScale(places, BigDecimal.RoundingMode.HALF_UP).bigDecimal.stripTrailingZeros.toPlainString

  def decode(s: com.histmersion.web.map.MapStart.DecodeS): Option[(LatLon, com.histmersion.web.map.MapStart.DecodeResultPart2)] =
    Option(s).map(_.split(',').map(_.trim).toList).collect { case List(a, b, c) => (a, b, c) }.flatMap { (a, b, c) =>
      for
        lat <- a.toDoubleOption.filter(v => v >= -90 && v <= 90)
        lon <- b.toDoubleOption.filter(v => v >= -180 && v <= 180)
        zoom <- c.toDoubleOption.filter(v => v >= 0 && v <= 22)
      yield (LatLon(lat, lon), zoom)
    }

  /** The view the map opens on: the browser's last one under "last" (if it kept one), else the configured place. Pure.
    */
  def initial(cfg: MapStartConfig, stored: Option[com.histmersion.web.map.MapStart.InitialStored]): (LatLon, com.histmersion.web.map.MapStart.InitialResultPart2) =
    val place = (LatLon(cfg.lat, cfg.lon), cfg.zoom)
    MapStartMode.parse(cfg.mode) match
      case MapStartMode.Last  => stored.flatMap(decode).getOrElse(place)
      case MapStartMode.Place => place

  /** Keep the map's view for the next visit (only where the map opens where it was left). */
  def remember(cfg: MapStartConfig, at: LatLon, zoom: com.histmersion.web.map.MapStart.RememberZoom): Unit =
    if MapStartMode.parse(cfg.mode) == MapStartMode.Last then
      val _ = Try(com.histmersion.web.Store.Browser.put(StorageKey, encode(at, zoom)))
