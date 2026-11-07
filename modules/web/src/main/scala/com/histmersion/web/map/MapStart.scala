package com.histmersion.web.map

import com.histmersion.api.instance.{MapStartConfig, MapStartMode}

import scala.util.Try

/** Where the map opens (`ui.map-start`): where this browser last left it ("last", kept per browser under
  * [[StorageKey]]) or always at the configured place.
  */
object MapStart:
  type Encoded = Encoded.T
  object Encoded extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val StorageKey = "histmersion.mapView"

  /** The view kept in the browser, as stored: `lat,lon,zoom`. Pure. */
  def encode(at: LatLon, zoom: MapSurface.Zoom): Encoded =
    Encoded(s"${round(at.lat.value, 5)},${round(at.lon.value, 5)},${round(zoom.value, 2)}")

  /** A number with at most `places` decimals, written the same in every locale. */
  private def round(v: Double, places: Int): String =
    BigDecimal(v).setScale(places, BigDecimal.RoundingMode.HALF_UP).bigDecimal.stripTrailingZeros.toPlainString

  def decode(s: Encoded): Option[(LatLon, MapSurface.Zoom)] =
    Option(s.value).map(_.split(',').map(_.trim).toList).collect { case List(a, b, c) => (a, b, c) }.flatMap { (a, b, c) =>
      for
        lat <- a.toDoubleOption.filter(v => v >= -90 && v <= 90)
        lon <- b.toDoubleOption.filter(v => v >= -180 && v <= 180)
        zoom <- c.toDoubleOption.filter(v => v >= 0 && v <= 22)
      yield (LatLon(LatLon.Lat(lat), LatLon.Lon(lon)), MapSurface.Zoom(zoom))
    }

  /** The view the map opens on: the browser's last one under "last" (if it kept one), else the configured place. Pure.
    */
  def initial(cfg: MapStartConfig, stored: Option[Encoded]): (LatLon, MapSurface.Zoom) =
    val place = (LatLon(LatLon.Lat(cfg.lat.value), LatLon.Lon(cfg.lon.value)), MapSurface.Zoom(cfg.zoom.value))
    MapStartMode.parse(cfg.mode) match
      case MapStartMode.Last  => stored.flatMap(decode).getOrElse(place)
      case MapStartMode.Place => place

  /** Keep the map's view for the next visit (only where the map opens where it was left). */
  def remember(cfg: MapStartConfig, at: LatLon, zoom: MapSurface.Zoom): Unit =
    if MapStartMode.parse(cfg.mode) == MapStartMode.Last then
      val _ = Try(com.histmersion.web.Store.Browser.put(StorageKey, encode(at, zoom).value))
