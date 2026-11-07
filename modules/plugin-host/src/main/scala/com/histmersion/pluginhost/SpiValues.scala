package com.histmersion.pluginhost

import com.histmersion.plugin as spi

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
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  // ── towards a plugin ────────────────────────────────────────────────────────────────────────

  private def yearOfLong(y: Long): Option[spi.Year] = Option.when(y.isValidInt)(spi.Year(y.toInt))

  def year(y: com.histmersion.Year): spi.Year = spi.Year(y.value)

  /** A stored year, `None` beyond what a `Year` holds. */
  def year(y: com.histmersion.domain.DeepTime.SignedYear): Option[spi.Year] = yearOfLong(y.value)

  /** A stored year as a bound of a query: held at the nearest year a `Year` holds. */
  def yearBound(y: com.histmersion.domain.DeepTime.SignedYear): spi.Year = spi.Year(y.value.max(Int.MinValue).min(Int.MaxValue).toInt)

  def period(p: com.histmersion.Period): Option[spi.Period] = spi.Period.from(year(p.start), year(p.end)).toOption

  /** Two stored years in either order. */
  def period(a: com.histmersion.domain.DeepTime.SignedYear, b: com.histmersion.domain.DeepTime.SignedYear): Option[spi.Period] =
    for
      start <- yearOfLong(a.value.min(b.value))
      end <- yearOfLong(a.value.max(b.value))
      period <- spi.Period.from(start, end).toOption
    yield period

  def latitude(l: com.histmersion.Latitude): Option[spi.Latitude] = com.histmersion.Latitude.option(l.value).map(ok => spi.Latitude(ok.value))
  def longitude(l: com.histmersion.Longitude): Option[spi.Longitude] =
    com.histmersion.Longitude.option(l.value).map(ok => spi.Longitude(ok.value))

  /** A DOI as the host holds it, trimmed; `None` when it is none. */
  def doi(d: com.histmersion.domain.library.Doi): Option[spi.Doi] = com.histmersion.domain.library.Doi.option(d.value.trim).map(ok => spi.Doi(ok.value))

  def wikidata(q: com.histmersion.domain.catalogue.places.Wikidata.Id): Option[spi.WikidataId] =
    com.histmersion.domain.catalogue.places.Wikidata.Id.option(q.value.trim).map(ok => spi.WikidataId(ok.value))

  /** An area `(minLon, minLat, maxLon, maxLat)`; `None` when a corner is off the globe. */
  def area(
      minLon: com.histmersion.Longitude,
      minLat: com.histmersion.Latitude,
      maxLon: com.histmersion.Longitude,
      maxLat: com.histmersion.Latitude
  ): Option[(spi.Longitude, spi.Latitude, spi.Longitude, spi.Latitude)] =
    for
      w <- longitude(minLon)
      s <- latitude(minLat)
      e <- longitude(maxLon)
      n <- latitude(maxLat)
    yield (w, s, e, n)

  // ── from a plugin ───────────────────────────────────────────────────────────────────────────

  def yearOf(y: spi.Year): Either[Problem, com.histmersion.Year] =
    spi.Year.either(y.value).map(ok => com.histmersion.Year(ok.value)).left.map(_ => Problem(s"year ${y.value} is not a year"))

  /** A plugin's period, its ends in either order (a period a plugin could not have built that way is still one span of
    * years).
    */
  def periodOf(p: spi.Period): Either[Problem, com.histmersion.Period] =
    for
      a <- yearOf(p.start)
      b <- yearOf(p.end)
      period <- com.histmersion.Period
        .from(if a.value <= b.value then a else b, if a.value <= b.value then b else a)
        .left
        .map(e => Problem(e.message.value))
    yield period

  def latitudeOf(l: spi.Latitude): Either[Problem, com.histmersion.Latitude] =
    com.histmersion.Latitude
      .either(l.value)
      .left
      .map(_ => Problem(s"latitude ${l.value} is not between -90 and 90"))

  def longitudeOf(l: spi.Longitude): Either[Problem, com.histmersion.Longitude] =
    com.histmersion.Longitude
      .either(l.value)
      .left
      .map(_ => Problem(s"longitude ${l.value} is not between -180 and 180"))

  def doiOf(d: spi.Doi): Either[Problem, com.histmersion.domain.library.Doi] =
    com.histmersion.domain.library.Doi
      .either(d.value)
      .left
      .map(_ => Problem(s"DOI '${d.value}' is not a DOI (10.<registrant>/<suffix>)"))

  def wikidataOf(q: spi.WikidataId): Either[Problem, com.histmersion.domain.catalogue.places.Wikidata.Id] =
    com.histmersion.domain.catalogue.places.Wikidata.Id
      .either(q.value)
      .left
      .map(_ => Problem(s"Wikidata id '${q.value}' is not a Wikidata item (Q and its number)"))

  /** A licence statement, as the text the host reads (and judges itself). */
  def licenceOf(l: spi.Licence): com.histmersion.domain.Licence.Id = com.histmersion.domain.Licence.Id(l.value)

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
