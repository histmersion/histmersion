package com.histmersion.pluginhost

import hearth.kindlings.optics.syntax.*

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
  type ReadPartFromPart = ReadPartFromPart.T
  object ReadPartFromPart extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HttpGetUrl = HttpGetUrl.T
  object HttpGetUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HttpGetHeadersPart1 = HttpGetHeadersPart1.T
  object HttpGetHeadersPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HttpGetHeadersPart2 = HttpGetHeadersPart2.T
  object HttpGetHeadersPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckedStatedResultPart1 = CheckedStatedResultPart1.T
  object CheckedStatedResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckedStatedFResultPart1 = CheckedStatedFResultPart1.T
  object CheckedStatedFResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckedLeftWhy = CheckedLeftWhy.T
  object CheckedLeftWhy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type LimitsPresent = LimitsPresent.T
  object LimitsPresent extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RereadProvider = RereadProvider.T
  object RereadProvider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueryFrom = QueryFrom.T
  object QueryFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueryTo = QueryTo.T
  object QueryTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PoliteIntervalMs = PoliteIntervalMs.T
  object PoliteIntervalMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PoliteSleepArg1 = PoliteSleepArg1.T
  object PoliteSleepArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadPartFirst = ReadPartFirst.T
  object ReadPartFirst extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadPartDoneArg1 = ReadPartDoneArg1.T
  object ReadPartDoneArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadPartDoneResult = ReadPartDoneResult.T
  object ReadPartDoneResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadPartEachArg1 = ReadPartEachArg1.T
  object ReadPartEachArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadPartEachArg2Part1 = ReadPartEachArg2Part1.T
  object ReadPartEachArg2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GuardResultPart1 = GuardResultPart1.T
  object GuardResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordResultPart1 = RecordResultPart1.T
  object RecordResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordProvider = RecordProvider.T
  object RecordProvider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckedResultPart1 = CheckedResultPart1.T
  object CheckedResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckedProvider = CheckedProvider.T
  object CheckedProvider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def limits(catalogue: Option[EventCatalogue], present: com.histmersion.pluginhost.PluginEvents.LimitsPresent): CatalogDating.Limits =
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
    stated.modify(_.timeSpan).setTo(timeSpan(CatalogDating.clamp(span(stated.timeSpan), limits)))

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
  def reread(catalogue: EventCatalogue, provider: com.histmersion.pluginhost.PluginEvents.RereadProvider, r: CatalogRecord): Option[CatalogRecord] =
    event(r)
      .flatMap(e => guard(Right(catalogue.reread(e))).toOption.flatten)
      .flatMap(record(provider, _).toOption.map(_.record))

  /** The query a plugin catalogue is asked: the import's years (held at what a `Year` holds) and its area (left out
    * when a corner is off the globe: the host applies the area itself anyway).
    */
  def query(from: Option[com.histmersion.pluginhost.PluginEvents.QueryFrom], to: com.histmersion.pluginhost.PluginEvents.QueryTo, area: Option[BBox]): CatalogueQuery =
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
      intervalMs: com.histmersion.pluginhost.PluginEvents.PoliteIntervalMs,
      sleep: com.histmersion.pluginhost.PluginEvents.PoliteSleepArg1 => Unit = ms => Thread.sleep(ms),
      log: HostLog = HostLog.scribe
  ): Polite =
    Polite(
      http,
      media.modify(_.requestIntervalMs).setTo(intervalMs).modify(_.maxRetryAfterSeconds).setTo(media.maxRetryAfterSeconds.max(120)),
      sleep = sleep,
      log = log
    )

  /** One part as the host reads it: the part, then each `next` page it names, until a page names none, names itself or
    * a part `done`, or `full` says the run has what it asked for. `each` gets every page read (or why it could not be).
    */
  def readPart(
      cat: EventCatalogue,
      first: com.histmersion.pluginhost.PluginEvents.ReadPartFirst,
      query: CatalogueQuery,
      http: Plugin.Http,
      done: com.histmersion.pluginhost.PluginEvents.ReadPartDoneArg1 => com.histmersion.pluginhost.PluginEvents.ReadPartDoneResult,
      full: => Boolean
  )(each: (com.histmersion.pluginhost.PluginEvents.ReadPartEachArg1, Either[com.histmersion.pluginhost.PluginEvents.ReadPartEachArg2Part1, CataloguePage]) => Unit): Unit =
    @annotation.tailrec
    def from(part: Option[com.histmersion.pluginhost.PluginEvents.ReadPartFromPart]): Unit = part match
      case Some(p) if !full =>
        val got = guard(cat.read(p, query, http))
        each(p, got)
        from(got.toOption.flatMap(_.next).filterNot(n => done(n) || n == p))
      case _ => ()
    from(Option(first).filter(p => !done(p)))

  def http(polite: Polite): Plugin.Http = new Plugin.Http:
    def get(url: com.histmersion.pluginhost.PluginEvents.HttpGetUrl, headers: Map[com.histmersion.pluginhost.PluginEvents.HttpGetHeadersPart1, com.histmersion.pluginhost.PluginEvents.HttpGetHeadersPart2]) = polite.get(url, headers).map(_.body)

  def safe[A](a: => A): Option[A] =
    try Option(a)
    catch case NonFatal(_) => None

  /** A plugin's answer, or what it threw as a failure of that call. */
  def guard[A](a: => Either[String, A]): Either[com.histmersion.pluginhost.PluginEvents.GuardResultPart1, A] =
    try Option(a).getOrElse(Left("the plugin answered nothing"))
    catch
      case e: InterruptedException => throw e
      case NonFatal(e)             => Left(s"the plugin failed: ${e.getClass.getSimpleName}: ${e.getMessage}")

  /** Pure: the record a plugin's event becomes under catalogue `provider`, or why it is refused: no id or label, a
    * historicity that is none of the host's, a year a `Year` refuses or a dating that runs backwards (named with the
    * catalogue and the value), or a record the host cannot read at all (a plugin built against another SPI). A position
    * off the globe (or half a position) and a Wikidata id that is no item are left out of the record, each with a
    * warning. Its kind is the host's spelling of it ([[EventTaxonomy.refine]]).
    */
  def record(provider: com.histmersion.pluginhost.PluginEvents.RecordProvider, e: CatalogueEvent): Either[com.histmersion.pluginhost.PluginEvents.RecordResultPart1, Kept[CatalogRecord]] =
    try checked(provider, e)
    catch
      case NonFatal(x) =>
        Left(
          s"${safe(e.id).getOrElse("a record")}: catalogue '$provider' handed over a record the host cannot read " +
            s"(${x.getClass.getSimpleName}: ${x.getMessage}) — is the plugin built against plugin SPI ${Spi.Version}?"
        )

  private def checked(provider: com.histmersion.pluginhost.PluginEvents.CheckedProvider, e: CatalogueEvent): Either[com.histmersion.pluginhost.PluginEvents.CheckedResultPart1, Kept[CatalogRecord]] =
    val id = Option(e.id).getOrElse("").trim
    val label = Option(e.label).getOrElse("").trim
    def stated[A, B](a: Option[A])(f: A => Either[com.histmersion.pluginhost.PluginEvents.CheckedStatedFResultPart1, B]): Either[com.histmersion.pluginhost.PluginEvents.CheckedStatedResultPart1, Option[B]] =
      SpiValues.optional(a)(f).left.map(why => s"$id: catalogue '$provider': $why")
    def left(why: com.histmersion.pluginhost.PluginEvents.CheckedLeftWhy) = s"$id: catalogue '$provider': $why, left out"
    val (lat0, latWhy) = SpiValues.kept(e.lat)(SpiValues.latitudeOf)
    val (lon0, lonWhy) = SpiValues.kept(e.lon)(SpiValues.longitudeOf)
    val (wikidata, wikidataWhy) = SpiValues.kept(e.wikidata)(SpiValues.wikidataOf)
    val half = Option.when(lat0.isDefined != lon0.isDefined)("a position needs both latitude and longitude")
    val (lat, lon) = if half.isDefined then (None, None) else (lat0, lon0)
    val warnings = List(latWhy, lonWhy, half, wikidataWhy).flatten.map(left)
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
        points = List(notBefore, beginBy, endFrom, notAfter).flatten.map(_.value)
        _ <- Either.cond(!points.zip(points.drop(1)).exists((a, b) => a > b), (), s"$id: its dating runs backwards")
      yield
        def long(y: Option[com.histmersion.Year]) = y.map(_.value.toLong)
        val record = CatalogRecord(
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
        Kept(record, warnings)
