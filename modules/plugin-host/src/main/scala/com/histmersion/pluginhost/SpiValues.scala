package com.histmersion
package pluginhost

import com.histmersion.plugin.Problem
import com.histmersion.plugin as spi
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.plugin.Year
import com.histmersion.domain.DeepTime
import com.histmersion.plugin.Latitude
import com.histmersion.plugin.Longitude
import com.histmersion.domain.library.Doi
import com.histmersion.domain.catalogue.places.Wikidata
import com.histmersion.plugin.WikidataId
import com.histmersion.plugin.Licence

/** The values the plugin SPI exchanges, converted between plugin-api's newtypes and the model's. plugin-api's copies
  * judge nothing (Iron `Pure`): the model's types are the only judge, here, where a plugin's value becomes the model's.
  *
  * Towards a plugin, the host's value goes as it is; where the host still holds a plain value (a stored year, a
  * coordinate or a DOI), it is parsed by the model's type, and one that does not parse is left out (`None`): no plugin
  * is handed a value the host would refuse.
  *
  * From a plugin, every value is checked by the model's type before it is converted: what fails is a `Left` naming the
  * value. The caller decides what that costs — a field that is only a detail (a DOI, a Wikidata id, a position) is left
  * out with a warning ([[kept]]), a dating that is no dating refuses the record. Pure.
  */
object SpiValues:

  // ── towards a plugin ────────────────────────────────────────────────────────────────────────

  private def yearOfLong(y: Long): Option[Year] = Option.when(y.isValidInt)(Year(y.toInt))

  def year(y: com.histmersion.Year): Year = Year(y.value)

  /** A stored year, `None` beyond what a `Year` holds. */
  def year(y: DeepTime.SignedYear): Option[Year] = yearOfLong(y.value)

  /** A stored year as a bound of a query: held at the nearest year a `Year` holds. */
  def yearBound(y: DeepTime.SignedYear): Year = Year(y.value.max(Int.MinValue).min(Int.MaxValue).toInt)

  def period(p: Period): Option[spi.Period] = spi.Period.from(year(p.start), year(p.end)).toOption

  /** Two stored years in either order. */
  def period(a: DeepTime.SignedYear, b: DeepTime.SignedYear): Option[spi.Period] =
    for
      start <- yearOfLong(a.value.min(b.value))
      end <- yearOfLong(a.value.max(b.value))
      period <- spi.Period.from(start, end).toOption
    yield period

  def latitude(l: Latitude): Option[Latitude] = com.histmersion.Latitude.option(l.value).map(_ => l)
  def longitude(l: Longitude): Option[Longitude] = com.histmersion.Longitude.option(l.value).map(_ => l)

  /** A DOI as the host holds it, trimmed; `None` when it is none. */
  def doi(d: Doi): Option[spi.Doi] = Doi.option(d.value.trim).map(ok => spi.Doi(ok.value))

  def wikidata(q: Wikidata.Id): Option[WikidataId] =
    Wikidata.Id.option(q.value.trim).map(ok => WikidataId(ok.value))

  /** An area `(minLon, minLat, maxLon, maxLat)`; `None` when a corner is off the globe. */
  def area(
      minLon: Longitude,
      minLat: Latitude,
      maxLon: Longitude,
      maxLat: Latitude
  ): Option[(Longitude, Latitude, Longitude, Latitude)] =
    for
      w <- longitude(minLon)
      s <- latitude(minLat)
      e <- longitude(maxLon)
      n <- latitude(maxLat)
    yield (w, s, e, n)

  // ── from a plugin ───────────────────────────────────────────────────────────────────────────

  def yearOf(y: Year): Either[Problem, com.histmersion.Year] =
    Year
      .either(y.value)
      .map(ok => com.histmersion.Year(ok.value))
      .left
      .map(_ => Problem(s"year ${y.value} is not a year"))

  /** A plugin's period, its ends in either order (a period a plugin could not have built that way is still one span of
    * years).
    */
  def periodOf(p: spi.Period): Either[Problem, Period] =
    for
      a <- yearOf(p.start)
      b <- yearOf(p.end)
      period <- Period
        .from(if a <= b then a else b, if a <= b then b else a)
        .left
        .map(e => Problem(e.message.value))
    yield period

  def latitudeOf(l: Latitude): Either[Problem, com.histmersion.Latitude] =
    com.histmersion.Latitude
      .either(l.value)
      .left
      .map(_ => Problem(s"latitude ${l.value} is not between -90 and 90"))

  def longitudeOf(l: Longitude): Either[Problem, com.histmersion.Longitude] =
    com.histmersion.Longitude
      .either(l.value)
      .left
      .map(_ => Problem(s"longitude ${l.value} is not between -180 and 180"))

  def doiOf(d: spi.Doi): Either[Problem, Doi] =
    Doi
      .either(d.value)
      .left
      .map(_ => Problem(s"DOI '${d.value}' is not a DOI (10.<registrant>/<suffix>)"))

  def wikidataOf(q: WikidataId): Either[Problem, Wikidata.Id] =
    Wikidata.Id
      .either(q.value)
      .left
      .map(_ => Problem(s"Wikidata id '${q.value}' is not a Wikidata item (Q and its number)"))

  /** A licence statement, as the text the host reads (and judges itself). */
  def licenceOf(l: Licence): com.histmersion.domain.Licence.Id = com.histmersion.domain.Licence.Id(l.value)

  /** `None` stays `None`; a value that fails its check is left out (`None`), with the reason as a warning. */
  def kept[A, B](a: Option[A])(f: A => Either[Problem, B]): (Option[B], Option[Problem]) =
    a.map(f) match
      case Some(Right(ok)) => (Some(ok), None)
      case Some(Left(why)) => (None, Some(why))
      case None            => (None, None)

  /** `None` stays `None`; a value that fails its check fails the whole. */
  def optional[A, B](a: Option[A])(f: A => Either[Problem, B]): Either[Problem, Option[B]] =
    a match
      case None    => Right(None)
      case Some(v) => f(v).map(Some(_))
