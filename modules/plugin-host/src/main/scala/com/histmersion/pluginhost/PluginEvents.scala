package com.histmersion.pluginhost

import com.histmersion.api.{BBox, CatalogRecord, EventTaxonomy, TimeSpan}
import com.histmersion.domain.CatalogDating
import com.histmersion.config.MediaConfig
import com.histmersion.plugin.*

import scala.util.control.NonFatal
import com.histmersion.values.Historicity

/** A plugin's catalogue as the host reads it: the query it is asked, its polite HTTP, and its records
  * checked and turned into the host's [[CatalogRecord]]s. Pure but for [[http]]. */
object PluginEvents:

  private val Qid = "^Q[1-9][0-9]*$"

  /** The limits of a plugin catalogue's dating (pure but for asking the plugin) — the present, and the catalogue's own
    * [[EventCatalogue.floorYear]] (`None` when it states none, throws, or is not registered now: a
    * dating stored under a floor is already held at it, so a looser limit changes nothing). */
  def limits(catalogue: Option[EventCatalogue], present: Long): CatalogDating.Limits =
    CatalogDating.Limits(present, catalogue.flatMap(c => safe(c.floorYear).flatten))

  /** Pure: a plugin catalogue's record dated as the host dates its own: a record that gives no point of
    * its span but a stated year is `central ± plusMinus` ([[CatalogDating.year]], as the GVP's and NOAA's
    * stated years are), then held inside `limits` ([[CatalogDating.clamp]]). The ONE derivation of the
    * import and of `events rederive` (which re-reads the stored record — the central year and ± are
    * kept as stated — so a record derived at the import derives to itself again). */
  def derive(r: CatalogRecord, limits: CatalogDating.Limits): CatalogRecord =
    val ts = r.timeSpan
    val undated = List(ts.notBefore, ts.beginBy, ts.endFrom, ts.notAfter).forall(_.isEmpty)
    val stated = r.central.filter(_ => undated).fold(r)(c =>
      r.copy(timeSpan = CatalogDating.span(Some(CatalogDating.year(c, r.plusMinus)), None)))
    CatalogDating.clamp(stated, limits)

  def query(from: Option[Long], to: Long, area: Option[BBox]): CatalogueQuery =
    CatalogueQuery(from, to, area.map(b => (b.minLon, b.minLat, b.maxLon, b.maxLat)))

  /** A catalogue run's polite client at `intervalMs` between requests to one host; the media politeness
    * otherwise (User-Agent, retries) — and up to two minutes' Retry-After, which a query service asks for. */
  def polite(http: HttpTransport, media: MediaConfig, intervalMs: Int, sleep: Long => Unit = ms => Thread.sleep(ms),
      log: HostLog = HostLog.scribe): Polite =
    Polite(http, media.copy(requestIntervalMs = intervalMs, maxRetryAfterSeconds = media.maxRetryAfterSeconds.max(120)),
      sleep = sleep, log = log)

  /** One part as the host reads it: the part, then each `next` page it names, until a page names none,
    * names itself or a part `done`, or `full` says the run has what it asked for. `each` gets every
    * page read (or why it could not be). */
  def readPart(cat: EventCatalogue, first: String, query: CatalogueQuery, http: PluginHttp, done: String => Boolean,
      full: => Boolean)(each: (String, Either[String, CataloguePage]) => Unit): Unit =
    @annotation.tailrec
    def from(part: Option[String]): Unit = part match
      case Some(p) if !full =>
        val got = guard(cat.read(p, query, http))
        each(p, got)
        from(got.toOption.flatMap(_.next).filterNot(n => done(n) || n == p))
      case _ => ()
    from(Option(first).filter(p => !done(p)))

  def http(fetch: Polite): PluginHttp = new PluginHttp:
    def get(url: String, headers: Map[String, String]) = fetch.get(url, headers).map(_.body)

  def safe[A](a: => A): Option[A] =
    try Option(a)
    catch case NonFatal(_) => None

  /** A plugin's answer, or what it threw as a failure of that call. */
  def guard[A](a: => Either[String, A]): Either[String, A] =
    try Option(a).getOrElse(Left("the plugin answered nothing"))
    catch
      case e: InterruptedException => throw e
      case NonFatal(e)             => Left(s"the plugin failed: ${e.getClass.getSimpleName}: ${e.getMessage}")

  /** Pure: the record a plugin's event becomes under catalogue `provider`, or why it is refused: no id or
    * label, a historicity that is none of the host's, a dating that runs backwards, a position off the
    * globe. Its kind is the host's spelling of it ([[EventTaxonomy.refine]]); a Wikidata item that is no
    * item id is dropped (the event keeps its own record). */
  def record(provider: String, e: CatalogueEvent): Either[String, CatalogRecord] =
    val id    = Option(e.id).getOrElse("").trim
    val label = Option(e.label).getOrElse("").trim
    val points = List(e.notBefore, e.beginBy, e.endFrom, e.notAfter).flatten
    if id.isEmpty then Left("a record without an id")
    else if label.isEmpty then Left(s"$id: no label")
    else if !Historicity.all.contains(e.historicity) then
      Left(s"$id: historicity '${e.historicity}' is none of ${Historicity.all.mkString(", ")}")
    else if points.zip(points.drop(1)).exists((a, b) => a > b) then Left(s"$id: its dating runs backwards")
    else if e.lat.exists(l => l < -90 || l > 90) || e.lon.exists(l => l < -180 || l > 180) then
      Left(s"$id: its position is not on the globe")
    else if e.lat.isDefined != e.lon.isDefined then Left(s"$id: a position needs both latitude and longitude")
    else Right(CatalogRecord(provider, id, Option(e.url).getOrElse("").trim, label, EventTaxonomy.refine(e.kind, label),
      TimeSpan(e.notBefore, e.beginBy, e.endFrom, e.notAfter), e.lat, e.lon, e.historicity, e.vei, e.magnitude,
      e.deaths, e.wikidata.map(_.trim.toUpperCase(java.util.Locale.ROOT)).filter(_.matches(Qid)),
      e.names.map(_.trim).filter(n => n.nonEmpty && n != label).distinct, e.description.map(_.trim).filter(_.nonEmpty),
      e.group.map(_.trim).filter(_.nonEmpty), e.central, e.plusMinus.filter(_ >= 0), e.datingMethod, e.raw))
