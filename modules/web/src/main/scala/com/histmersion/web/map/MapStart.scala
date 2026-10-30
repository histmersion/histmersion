package com.histmersion.web.map

import com.histmersion.api.{MapStartConfig, MapStartMode}

import scala.util.Try

/** Where the map opens (`ui.map-start`): where this browser last left it ("last", kept per browser under
  * [[StorageKey]]) or always at the configured place. */
object MapStart:
  val StorageKey = "histmersion.mapView"

  /** The view kept in the browser, as stored: `lat,lon,zoom`. Pure. */
  def encode(at: LatLon, zoom: Double): String =
    s"${round(at.lat, 5)},${round(at.lon, 5)},${round(zoom, 2)}"

  /** A number with at most `places` decimals, written the same in every locale. */
  private def round(v: Double, places: Int): String =
    BigDecimal(v).setScale(places, BigDecimal.RoundingMode.HALF_UP).bigDecimal.stripTrailingZeros.toPlainString

  def decode(s: String): Option[(LatLon, Double)] =
    Option(s).map(_.split(',').map(_.trim).toList).collect { case List(a, b, c) => (a, b, c) }.flatMap { (a, b, c) =>
      for
        lat  <- a.toDoubleOption.filter(v => v >= -90 && v <= 90)
        lon  <- b.toDoubleOption.filter(v => v >= -180 && v <= 180)
        zoom <- c.toDoubleOption.filter(v => v >= 0 && v <= 22)
      yield (LatLon(lat, lon), zoom)
    }

  /** The view the map opens on: the browser's last one under "last" (if it kept one), else the configured
    * place. Pure. */
  def initial(cfg: MapStartConfig, stored: Option[String]): (LatLon, Double) =
    val place = (LatLon(cfg.lat, cfg.lon), cfg.zoom)
    MapStartMode.parse(cfg.mode) match
      case MapStartMode.Last  => stored.flatMap(decode).getOrElse(place)
      case MapStartMode.Place => place

  /** Keep the map's view for the next visit (only where the map opens where it was left). */
  def remember(cfg: MapStartConfig, at: LatLon, zoom: Double): Unit =
    if MapStartMode.parse(cfg.mode) == MapStartMode.Last then
      val _ = Try(com.histmersion.web.Store.Browser.put(StorageKey, encode(at, zoom)))
