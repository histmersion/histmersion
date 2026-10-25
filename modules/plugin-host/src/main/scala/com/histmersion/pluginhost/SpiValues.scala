package com.histmersion.pluginhost

import com.histmersion.plugin as spi
import com.histmersion.values

/** The values the plugin SPI exchanges, converted between plugin-api's newtypes and the model's
  * (`values`). The two sides keep copies of their own, over the same constraints, so a conversion is
  * free (`values.Latitude(l.value)`) and stops compiling when one side's constraint changes, which forces
  * a deliberate parse here.
  *
  * Towards a plugin, the host's value goes as it is; where the host still holds a plain value (a stored
  * year, a coordinate or a DOI), it is parsed, and one that does not parse is left out (`None`): no
  * plugin is ever handed a value its type refuses.
  *
  * From a plugin, every value is checked again before it is converted. A newtype is erased at run time,
  * so its type cannot stop a plugin from handing over anything (one built against another constraint, or
  * that used `assume`): what fails is a `Left` naming the value, which the caller refuses the record with.
  * Pure. */
object SpiValues:

  // ── towards a plugin ────────────────────────────────────────────────────────────────────────

  def year(y: values.Year): spi.Year = spi.Year(y.value)

  /** A stored year, `None` beyond what a `Year` holds. */
  def year(y: Long): Option[spi.Year] = Option.when(y.isValidInt)(spi.Year(y.toInt))

  /** A stored year as a bound of a query: held at the nearest year a `Year` holds. */
  def yearBound(y: Long): spi.Year = spi.Year(y.max(Int.MinValue).min(Int.MaxValue).toInt)

  def period(p: values.Period): Option[spi.Period] = spi.Period.from(year(p.start), year(p.end)).toOption

  /** Two stored years in either order. */
  def period(a: Long, b: Long): Option[spi.Period] =
    for
      start  <- year(a.min(b))
      end    <- year(a.max(b))
      period <- spi.Period.from(start, end).toOption
    yield period

  def latitude(l: Double): Option[spi.Latitude]   = spi.Latitude.option(l)
  def longitude(l: Double): Option[spi.Longitude] = spi.Longitude.option(l)

  /** A DOI as the host holds it, trimmed; `None` when it is none. */
  def doi(d: String): Option[spi.Doi] = spi.Doi.option(d.trim)

  def wikidata(q: String): Option[spi.WikidataId] = spi.WikidataId.option(q.trim)

  /** An area `(minLon, minLat, maxLon, maxLat)`; `None` when a corner is off the globe. */
  def area(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double)
      : Option[(spi.Longitude, spi.Latitude, spi.Longitude, spi.Latitude)] =
    for
      w <- longitude(minLon)
      s <- latitude(minLat)
      e <- longitude(maxLon)
      n <- latitude(maxLat)
    yield (w, s, e, n)

  // ── from a plugin ───────────────────────────────────────────────────────────────────────────

  def yearOf(y: spi.Year): Either[String, values.Year] =
    spi.Year.either(y.value).map(ok => values.Year(ok.value)).left.map(_ => s"year ${y.value} is not a year")

  /** A plugin's period, its ends in either order (a period a plugin could not have built that way is
    * still one span of years). */
  def periodOf(p: spi.Period): Either[String, values.Period] =
    for
      a <- yearOf(p.start)
      b <- yearOf(p.end)
      period <- values.Period.from(if a.value <= b.value then a else b, if a.value <= b.value then b else a)
        .left.map(_.message)
    yield period

  def latitudeOf(l: spi.Latitude): Either[String, values.Latitude] =
    spi.Latitude.either(l.value).map(ok => values.Latitude(ok.value))
      .left.map(_ => s"latitude ${l.value} is not between -90 and 90")

  def longitudeOf(l: spi.Longitude): Either[String, values.Longitude] =
    spi.Longitude.either(l.value).map(ok => values.Longitude(ok.value))
      .left.map(_ => s"longitude ${l.value} is not between -180 and 180")

  def doiOf(d: spi.Doi): Either[String, values.Doi] =
    spi.Doi.either(d.value).map(ok => values.Doi(ok.value)).left.map(_ => s"DOI '${d.value}' is not a DOI (10.<registrant>/<suffix>)")

  def wikidataOf(q: spi.WikidataId): Either[String, values.WikidataId] =
    spi.WikidataId.either(q.value).map(ok => values.WikidataId(ok.value))
      .left.map(_ => s"Wikidata id '${q.value}' is not a Wikidata item (Q and its number)")

  /** A licence statement, as the text the host reads (and judges itself). */
  def licenceOf(l: spi.Licence): String = l.value

  /** `None` stays `None`; a value that fails its check fails the whole. */
  def optional[A, B](a: Option[A])(f: A => Either[String, B]): Either[String, Option[B]] =
    a match
      case None    => Right(None)
      case Some(v) => f(v).map(Some(_))
