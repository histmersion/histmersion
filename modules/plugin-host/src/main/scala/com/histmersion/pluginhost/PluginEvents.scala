package com.histmersion
package pluginhost

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.{BBox, TimeSpan}
import com.histmersion.api.catalogue.{CatalogRecord, EventTaxonomy}
import com.histmersion.upstreams.dating.CatalogDating
import com.histmersion.config.MediaConfig
import com.histmersion.plugin.*

import scala.util.control.NonFatal
import com.histmersion.domain.catalogue.Historicity
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.DeepTime
import com.histmersion.plugin.Latitude
import com.histmersion.plugin.Longitude
import com.histmersion.domain.catalogue.places.Wikidata
import com.histmersion.domain.CoreQuota
import com.histmersion.api.catalogue.EventSummary
import com.histmersion.api.catalogue.EventEntry

/** A plugin's catalogue as the host reads it: the query it is asked, its polite HTTP, and its records checked and
  * turned into the host's [[CatalogRecord]]s. Pure but for [[http]].
  */
object PluginEvents:
  export MediaConfig.{RequestIntervalMs as IntervalMs}
  type PartName = PartName.T
  object PartName extends Newtype[String, Pure]
  type Problem = Problem.T
  object Problem extends Newtype[String, Pure]
  type Provider = CatalogRecord.Provider
  val Provider: CatalogRecord.Provider.type = CatalogRecord.Provider
  type Reason = Reason.T
  object Reason extends Newtype[String, Pure]
  export Polite.{IntervalMs as SleepMs}

  /** The limits of a plugin catalogue's dating (pure but for asking the plugin) — the present, and the catalogue's own
    * [[EventCatalogue.floorYear]] (`None` when it states none, throws, or is not registered now: a dating stored under
    * a floor is already held at it, so a looser limit changes nothing).
    */
  type RereadProvider = Provider
  val RereadProvider: Provider.type = Provider

  def limits(catalogue: Option[EventCatalogue], present: DeepTime.SignedYear): CatalogDating.Limits =
    CatalogDating.Limits(
      CatalogDating.HistoricalYear(present.value),
      catalogue
        .flatMap(c => safe(c.floorYear.flatMap(SpiValues.yearOf(_).toOption)).flatten)
        .map(y => CatalogDating.HistoricalYear(y.value.toLong))
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
      .fold(r)(c =>
        r.copy(timeSpan =
          timeSpan(
            CatalogDating.span(
              Some(CatalogDating.year(CatalogDating.HistoricalYear(c.value), r.plusMinus.map(m => CatalogDating.YearMargin(m.value)))),
              None
            )
          )
        )
      )
    stated.modify(_.timeSpan).setTo(timeSpan(CatalogDating.clamp(span(stated.timeSpan), limits)))

  /** Pure: a span as the dating rules read it. */
  def span(ts: TimeSpan): CatalogDating.Span =
    def historical(y: Option[com.histmersion.api.catalogue.Year]) = y.map(v => CatalogDating.HistoricalYear(v.value))
    CatalogDating.Span(historical(ts.notBefore), historical(ts.beginBy), historical(ts.endFrom), historical(ts.notAfter))

  /** Pure: a span as the host stores it. */
  def timeSpan(s: CatalogDating.Span): TimeSpan =
    def year(y: Option[CatalogDating.HistoricalYear]) = y.map(v => com.histmersion.api.catalogue.Year(v.value))
    TimeSpan(year(s.notBefore), year(s.beginBy), year(s.endFrom), year(s.notAfter))

  /** Pure: a stored record as its catalogue's event, for the catalogue to read again ([[EventCatalogue.reread]]);
    * `None` when a stored value is one the SPI's types do not hold (a year beyond an `Int`, a position off the globe).
    */
  def event(r: CatalogRecord): Option[CatalogueEvent] =
    def opt[A, B](a: Option[A])(f: A => Option[B]): Option[Option[B]] = a.fold(Some(None))(v => f(v).map(Some(_)))
    def year(y: Long) = SpiValues.year(DeepTime.SignedYear(y))
    for
      notBefore <- opt(r.timeSpan.notBefore)(y => year(y.value))
      beginBy <- opt(r.timeSpan.beginBy)(y => year(y.value))
      endFrom <- opt(r.timeSpan.endFrom)(y => year(y.value))
      notAfter <- opt(r.timeSpan.notAfter)(y => year(y.value))
      central <- opt(r.central)(y => year(y.value))
      lat <- opt(r.lat)(l => SpiValues.latitude(Latitude(l.value)))
      lon <- opt(r.lon)(l => SpiValues.longitude(Longitude(l.value)))
      wikidata <- opt(r.wikidata)(q => Wikidata.Id.option(q.value).flatMap(SpiValues.wikidata))
    yield CatalogueEvent(
      CatalogueEvent.Id(r.providerId.value),
      CatalogueEvent.Url(r.url.value),
      CatalogueEvent.Label(r.label.value),
      CatalogueEvent.Kind(r.kind.value),
      notBefore,
      notAfter,
      beginBy,
      endFrom,
      lat,
      lon,
      CatalogueEvent.Historicity(r.historicity.value),
      r.vei.map(v => CatalogueEvent.Vei(v.value)),
      r.magnitude.map(m => CatalogueEvent.Magnitude(m.value)),
      r.deaths.map(d => CatalogueEvent.Deaths(d.value)),
      wikidata,
      r.names.map(n => CatalogueEvent.Names(n.value)),
      r.description.map(d => CatalogueEvent.Description(d.value)),
      r.group.map(g => CatalogueEvent.Group(g.value)),
      central,
      r.plusMinus.map(m => CatalogueEvent.PlusMinus(m.value)),
      r.datingMethod.map(m => CatalogueEvent.DatingMethod(m.value)),
      r.raw.map(x => CatalogueEvent.Raw(x.value))
    )

  /** A stored record read again by its catalogue, without the network: the catalogue's own reading of it as the host
    * stores a record ([[record]]), or `None` when the catalogue does not re-read (or not this record).
    */
  def reread(catalogue: EventCatalogue, provider: RereadProvider, r: CatalogRecord): Option[CatalogRecord] =
    event(r)
      .flatMap(e => guard(Right(catalogue.reread(e))).toOption.flatten)
      .flatMap(record(provider, _).toOption.map(_.record))

  /** The query a plugin catalogue is asked: the import's years (held at what a `Year` holds) and its area (left out
    * when a corner is off the globe: the host applies the area itself anyway).
    */
  def query(from: Option[DeepTime.SignedYear], to: DeepTime.SignedYear, area: Option[BBox]): CatalogueQuery =
    CatalogueQuery(
      from.map(SpiValues.yearBound),
      SpiValues.yearBound(to),
      area.flatMap(b =>
        for
          a <- SpiValues.area(
            Longitude(b.minLon.value),
            Latitude(b.minLat.value),
            Longitude(b.maxLon.value),
            Latitude(b.maxLat.value)
          )
        yield a
      )
    )

  /** A catalogue run's polite client at `intervalMs` between requests to one host; the media politeness otherwise
    * (User-Agent, retries) — and up to two minutes' Retry-After, which a query service asks for.
    */
  def polite(
      http: HttpTransport,
      media: MediaConfig,
      intervalMs: PluginEvents.IntervalMs,
      sleep: Polite.IntervalMs => Unit = ms => Thread.sleep(ms.value),
      log: HostLog = HostLog.scribe
  ): Polite =
    Polite(
      http,
      media
        .modify(_.requestIntervalMs)
        .setTo(intervalMs)
        .modify(_.maxRetryAfterSeconds)
        .setTo(MediaConfig.MaxRetryAfterSeconds(media.maxRetryAfterSeconds.value.max(120))),
      sleep = sleep,
      log = log
    )

  /** One part as the host reads it: the part, then each `next` page it names, until a page names none, names itself or
    * a part `done`, or `full` says the run has what it asked for. `each` gets every page read (or why it could not be).
    */
  def readPart(
      cat: EventCatalogue,
      first: PartName,
      query: CatalogueQuery,
      http: Plugin.Http,
      done: PartName => Boolean,
      full: => Boolean
  )(each: (PartName, Either[Problem, CataloguePage]) => Unit): Unit =
    @annotation.tailrec
    def from(part: Option[PartName]): Unit = part match
      case Some(p) if !full =>
        val got = guard(cat.read(EventCatalogue.ReadPart(p.value), query, http))
        each(p, got)
        from(got.toOption.flatMap(_.next).map(n => PartName(n.value)).filterNot(n => done(n) || n == p))
      case _ => ()
    from(Option(first).filter(p => !done(p)))

  def http(polite: Polite): Plugin.Http = new Plugin.Http:
    def get(url: Plugin.Http.GetUrl, headers: Map[Plugin.Http.GetHeadersPart1, Plugin.Http.GetHeadersPart2]) =
      polite
        .get(
          HttpTransport.Url(url.value),
          headers.map((k, v) => CoreQuota.HeaderName(k.value) -> CoreQuota.HeaderValue(v.value))
        )
        .left
        .map(e => Plugin.Http.GetResultPart1(e.value))
        .map(a => Plugin.HttpResponse.Body(a.body.value))

  def safe[A](a: => A): Option[A] =
    try Option(a)
    catch case NonFatal(_) => None

  /** A plugin's answer, or what it threw as a failure of that call. */
  def guard[A](a: => Either[?, A]): Either[Problem, A] =
    try Option(a).getOrElse(Left(Problem("the plugin answered nothing"))).left.map(e => Problem(s"$e"))
    catch
      case e: InterruptedException => throw e
      case NonFatal(e)             => Left(Problem(s"the plugin failed: ${e.getClass.getSimpleName}: ${e.getMessage}"))

  /** Pure: the record a plugin's event becomes under catalogue `provider`, or why it is refused: no id or label, a
    * historicity that is none of the host's, a year a `Year` refuses or a dating that runs backwards (named with the
    * catalogue and the value), or a record the host cannot read at all (a plugin built against another SPI). A position
    * off the globe (or half a position) and a Wikidata id that is no item are left out of the record, each with a
    * warning. Its kind is the host's spelling of it ([[EventTaxonomy.refine]]).
    */
  def record(provider: CatalogRecord.Provider, e: CatalogueEvent): Either[Problem, Kept[CatalogRecord]] =
    try checked(provider, e)
    catch
      case NonFatal(x) =>
        Left(
          Problem(
            s"${safe(e.id.value).getOrElse("a record")}: catalogue '${provider.value}' handed over a record the host cannot read " +
              s"(${x.getClass.getSimpleName}: ${x.getMessage}) — is the plugin built against plugin SPI ${Spi.Version}?"
          )
        )

  private def checked(provider: CatalogRecord.Provider, e: CatalogueEvent): Either[Problem, Kept[CatalogRecord]] =
    val id = Option(e.id).map(_.value).getOrElse("").trim
    val label = Option(e.label).map(_.value).getOrElse("").trim
    def stated[A, B](a: Option[A])(f: A => Either[SpiValues.Problem, B]): Either[Problem, Option[B]] =
      SpiValues.optional(a)(f).left.map(why => Problem(s"$id: catalogue '${provider.value}': ${why.value}"))
    def left(why: String) = Kept.Warnings(s"$id: catalogue '${provider.value}': $why, left out")
    val (lat0, latWhy) = SpiValues.kept(e.lat)(SpiValues.latitudeOf)
    val (lon0, lonWhy) = SpiValues.kept(e.lon)(SpiValues.longitudeOf)
    val (wikidata, wikidataWhy) = SpiValues.kept(e.wikidata)(SpiValues.wikidataOf)
    val half = Option.when(lat0.isDefined != lon0.isDefined)("a position needs both latitude and longitude")
    val (lat, lon) = if half.isDefined then (None, None) else (lat0, lon0)
    val warnings = List(latWhy.map(_.value), lonWhy.map(_.value), half, wikidataWhy.map(_.value)).flatten.map(left)
    if id.isEmpty then Left(Problem("a record without an id"))
    else if label.isEmpty then Left(Problem(s"$id: no label"))
    else if !Historicity.all.exists(_.value == e.historicity.value) then
      Left(Problem(s"$id: historicity '${e.historicity.value}' is none of ${Historicity.all.map(_.value).mkString(", ")}"))
    else
      for
        notBefore <- stated(e.notBefore)(SpiValues.yearOf)
        beginBy <- stated(e.beginBy)(SpiValues.yearOf)
        endFrom <- stated(e.endFrom)(SpiValues.yearOf)
        notAfter <- stated(e.notAfter)(SpiValues.yearOf)
        central <- stated(e.central)(SpiValues.yearOf)
        points = List(notBefore, beginBy, endFrom, notAfter).flatten.map(_.value)
        _ <- Either.cond(!points.zip(points.drop(1)).exists((a, b) => a > b), (), Problem(s"$id: its dating runs backwards"))
      yield
        def long(y: Option[com.histmersion.Year]) = y.map(v => com.histmersion.api.catalogue.Year(v.value.toLong))
        val record = CatalogRecord(
          provider,
          CatalogRecord.ProviderId(id),
          CatalogRecord.Url(Option(e.url).map(_.value).getOrElse("").trim),
          CatalogRecord.Label(label),
          CatalogRecord.Kind(
            EventTaxonomy.refine(EventSummary.Kind(e.kind.value), EventSummary.Label(label)).value
          ),
          TimeSpan(long(notBefore), long(beginBy), long(endFrom), long(notAfter)),
          lat.map(l => com.histmersion.api.catalogue.Latitude(l.value)),
          lon.map(l => com.histmersion.api.catalogue.Longitude(l.value)),
          CatalogRecord.Historicity(e.historicity.value),
          e.vei.map(v => CatalogRecord.Vei(v.value)),
          e.magnitude.map(m => CatalogRecord.Magnitude(m.value)),
          e.deaths.map(d => CatalogRecord.Deaths(d.value)),
          wikidata.map(w => EventEntry.Wikidata(w.value)),
          e.names.map(_.value.trim).filter(n => n.nonEmpty && n != label).distinct.map(CatalogRecord.Names(_)),
          e.description.map(_.value.trim).filter(_.nonEmpty).map(CatalogRecord.Description(_)),
          e.group.map(_.value.trim).filter(_.nonEmpty).map(CatalogRecord.Group(_)),
          long(central).map(y => CatalogRecord.Central(y.value)),
          e.plusMinus.filter(_.value >= 0).map(m => CatalogRecord.PlusMinus(m.value)),
          e.datingMethod.map(m => CatalogRecord.DatingMethod(m.value)),
          e.raw.map(x => CatalogRecord.Raw(x.value))
        )
        Kept(record, warnings)
