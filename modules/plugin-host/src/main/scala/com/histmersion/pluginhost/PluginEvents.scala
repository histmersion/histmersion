package com.histmersion.pluginhost

import com.histmersion.api.{BBox, TimeSpan}
import com.histmersion.api.catalogue.{CatalogRecord, EventTaxonomy}
import com.histmersion.upstreams.dating.CatalogDating
import com.histmersion.config.MediaConfig
import com.histmersion.plugin.*

import scala.util.control.NonFatal
import com.histmersion.domain.catalogue.Historicity

/** A plugin's catalogue as the host reads it: the query it is asked, its polite HTTP, and its records checked and
  * turned into the host's [[CatalogRecord]]s. Pure but for [[http]].
  */
object PluginEvents:

  /** The limits of a plugin catalogue's dating (pure but for asking the plugin) — the present, and the catalogue's own
    * [[EventCatalogue.floorYear]] (`None` when it states none, throws, or is not registered now: a dating stored under
    * a floor is already held at it, so a looser limit changes nothing).
    */
  def limits(catalogue: Option[EventCatalogue], present: Long): CatalogDating.Limits =
    CatalogDating.Limits(
      present,
      catalogue
        .flatMap(c => safe(c.floorYear.flatMap(SpiValues.yearOf(_).toOption)).flatten)
        .map(_.value.toLong)
    )

  /** Pure: a plugin catalogue's record dated as the host dates its own: a record that gives no point of its span but a
    * stated year is `central ± plusMinus` ([[CatalogDating.year]], as the GVP's and NOAA's stated years are), then held
    * inside `limits` ([[CatalogDating.clamp]]). The ONE derivation of the import and of `events rederive` (which
    * re-reads the stored record — the central year and ± are kept as stated — so a record derived at the import derives
    * to itself again).
    */
  def derive(r: CatalogRecord, limits: CatalogDating.Limits): CatalogRecord =
    val ts = r.timeSpan
    val undated = List(ts.notBefore, ts.beginBy, ts.endFrom, ts.notAfter).forall(_.isEmpty)
    val stated = r.central
      .filter(_ => undated)
      .fold(r)(c => r.copy(timeSpan = timeSpan(CatalogDating.span(Some(CatalogDating.year(c, r.plusMinus)), None))))
    stated.copy(timeSpan = timeSpan(CatalogDating.clamp(span(stated.timeSpan), limits)))

  /** Pure: a span as the dating rules read it. */
  def span(ts: TimeSpan): CatalogDating.Span = CatalogDating.Span(ts.notBefore, ts.beginBy, ts.endFrom, ts.notAfter)

  /** Pure: a span as the host stores it. */
  def timeSpan(s: CatalogDating.Span): TimeSpan = TimeSpan(s.notBefore, s.beginBy, s.endFrom, s.notAfter)

  /** Pure: a stored record as its catalogue's event, for the catalogue to read again ([[EventCatalogue.reread]]);
    * `None` when a stored value is one the SPI's types do not hold (a year beyond an `Int`, a position off the globe).
    */
  def event(r: CatalogRecord): Option[CatalogueEvent] =
    def opt[A, B](a: Option[A])(f: A => Option[B]): Option[Option[B]] = a.fold(Some(None))(v => f(v).map(Some(_)))
    for
      notBefore <- opt(r.timeSpan.notBefore)(SpiValues.year)
      beginBy <- opt(r.timeSpan.beginBy)(SpiValues.year)
      endFrom <- opt(r.timeSpan.endFrom)(SpiValues.year)
      notAfter <- opt(r.timeSpan.notAfter)(SpiValues.year)
      central <- opt(r.central)(SpiValues.year)
      lat <- opt(r.lat)(SpiValues.latitude)
      lon <- opt(r.lon)(SpiValues.longitude)
      wikidata <- opt(r.wikidata)(SpiValues.wikidata)
    yield CatalogueEvent(
      r.providerId,
      r.url,
      r.label,
      r.kind,
      notBefore,
      notAfter,
      beginBy,
      endFrom,
      lat,
      lon,
      r.historicity,
      r.vei,
      r.magnitude,
      r.deaths,
      wikidata,
      r.names,
      r.description,
      r.group,
      central,
      r.plusMinus,
      r.datingMethod,
      r.raw
    )

  /** A stored record read again by its catalogue, without the network: the catalogue's own reading of it as the host
    * stores a record ([[record]]), or `None` when the catalogue does not re-read (or not this record).
    */
  def reread(catalogue: EventCatalogue, provider: String, r: CatalogRecord): Option[CatalogRecord] =
    event(r).flatMap(e => guard(Right(catalogue.reread(e))).toOption.flatten).flatMap(record(provider, _).toOption)

  /** The query a plugin catalogue is asked: the import's years (held at what a `Year` holds) and its area (left out
    * when a corner is off the globe: the host applies the area itself anyway).
    */
  def query(from: Option[Long], to: Long, area: Option[BBox]): CatalogueQuery =
    CatalogueQuery(
      from.map(SpiValues.yearBound),
      SpiValues.yearBound(to),
      area.flatMap(b => SpiValues.area(b.minLon, b.minLat, b.maxLon, b.maxLat))
    )

  /** A catalogue run's polite client at `intervalMs` between requests to one host; the media politeness otherwise
    * (User-Agent, retries) — and up to two minutes' Retry-After, which a query service asks for.
    */
  def polite(
      http: HttpTransport,
      media: MediaConfig,
      intervalMs: Int,
      sleep: Long => Unit = ms => Thread.sleep(ms),
      log: HostLog = HostLog.scribe
  ): Polite =
    Polite(
      http,
      media.copy(requestIntervalMs = intervalMs, maxRetryAfterSeconds = media.maxRetryAfterSeconds.max(120)),
      sleep = sleep,
      log = log
    )

  /** One part as the host reads it: the part, then each `next` page it names, until a page names none, names itself or
    * a part `done`, or `full` says the run has what it asked for. `each` gets every page read (or why it could not be).
    */
  def readPart(
      cat: EventCatalogue,
      first: String,
      query: CatalogueQuery,
      http: PluginHttp,
      done: String => Boolean,
      full: => Boolean
  )(each: (String, Either[String, CataloguePage]) => Unit): Unit =
    @annotation.tailrec
    def from(part: Option[String]): Unit = part match
      case Some(p) if !full =>
        val got = guard(cat.read(p, query, http))
        each(p, got)
        from(got.toOption.flatMap(_.next).filterNot(n => done(n) || n == p))
      case _ => ()
    from(Option(first).filter(p => !done(p)))

  def http(polite: Polite): PluginHttp = new PluginHttp:
    def get(url: String, headers: Map[String, String]) = polite.get(url, headers).map(_.body)

  def safe[A](a: => A): Option[A] =
    try Option(a)
    catch case NonFatal(_) => None

  /** A plugin's answer, or what it threw as a failure of that call. */
  def guard[A](a: => Either[String, A]): Either[String, A] =
    try Option(a).getOrElse(Left("the plugin answered nothing"))
    catch
      case e: InterruptedException => throw e
      case NonFatal(e)             => Left(s"the plugin failed: ${e.getClass.getSimpleName}: ${e.getMessage}")

  /** Pure: the record a plugin's event becomes under catalogue `provider`, or why it is refused: no id or label, a
    * historicity that is none of the host's, a dating that runs backwards, a position off the globe, a value its type
    * would refuse (named with the catalogue and the value), or a record the host cannot read at all (a plugin built
    * against another SPI). Its kind is the host's spelling of it ([[EventTaxonomy.refine]]).
    */
  def record(provider: String, e: CatalogueEvent): Either[String, CatalogRecord] =
    try checked(provider, e)
    catch
      case NonFatal(x) =>
        Left(
          s"${safe(e.id).getOrElse("a record")}: catalogue '$provider' handed over a record the host cannot read " +
            s"(${x.getClass.getSimpleName}: ${x.getMessage}) — is the plugin built against plugin SPI ${Spi.Version}?"
        )

  private def checked(provider: String, e: CatalogueEvent): Either[String, CatalogRecord] =
    val id = Option(e.id).getOrElse("").trim
    val label = Option(e.label).getOrElse("").trim
    def stated[A, B](a: Option[A])(f: A => Either[String, B]): Either[String, Option[B]] =
      SpiValues.optional(a)(f).left.map(why => s"$id: catalogue '$provider': $why")
    if id.isEmpty then Left("a record without an id")
    else if label.isEmpty then Left(s"$id: no label")
    else if !Historicity.all.contains(e.historicity) then
      Left(s"$id: historicity '${e.historicity}' is none of ${Historicity.all.mkString(", ")}")
    else
      for
        notBefore <- stated(e.notBefore)(SpiValues.yearOf)
        beginBy <- stated(e.beginBy)(SpiValues.yearOf)
        endFrom <- stated(e.endFrom)(SpiValues.yearOf)
        notAfter <- stated(e.notAfter)(SpiValues.yearOf)
        central <- stated(e.central)(SpiValues.yearOf)
        lat <- stated(e.lat)(SpiValues.latitudeOf)
        lon <- stated(e.lon)(SpiValues.longitudeOf)
        wikidata <- stated(e.wikidata)(SpiValues.wikidataOf)
        points = List(notBefore, beginBy, endFrom, notAfter).flatten.map(_.value)
        _ <- Either.cond(!points.zip(points.drop(1)).exists((a, b) => a > b), (), s"$id: its dating runs backwards")
        _ <- Either.cond(lat.isDefined == lon.isDefined, (), s"$id: a position needs both latitude and longitude")
      yield
        def long(y: Option[com.histmersion.Year]) = y.map(_.value.toLong)
        CatalogRecord(
          provider,
          id,
          Option(e.url).getOrElse("").trim,
          label,
          EventTaxonomy.refine(e.kind, label),
          TimeSpan(long(notBefore), long(beginBy), long(endFrom), long(notAfter)),
          lat.map(_.value),
          lon.map(_.value),
          e.historicity,
          e.vei,
          e.magnitude,
          e.deaths,
          wikidata.map(_.value),
          e.names.map(_.trim).filter(n => n.nonEmpty && n != label).distinct,
          e.description.map(_.trim).filter(_.nonEmpty),
          e.group.map(_.trim).filter(_.nonEmpty),
          long(central),
          e.plusMinus.filter(_ >= 0),
          e.datingMethod,
          e.raw
        )
