package com.histmersion.server.events

import com.histmersion.core.{BBox, CatalogDating, CatalogProvider, CatalogProviderInfo, CatalogRecord, EventTaxonomy,
  Historicity, TimeSpan}
import com.histmersion.plugin.*
import com.histmersion.server.acquire.fetchers.Builtin
import com.histmersion.server.media.Polite

import scala.collection.immutable.ListMap
import scala.util.control.NonFatal

/** An event catalogue that is Histmersion's OWN code (GVP, NOAA, Wikidata), registered through the plugin
  * host like any other (docs/research-plugins.md §6, D2) but read by the host's own import with its
  * `events.<catalogue>.*` settings, unchanged. The SPI's `parts`/`read` are never called for it. */
final case class HostEventCatalogue(plugin: String, name: String) extends EventCatalogue:
  private def info: Option[CatalogProviderInfo] = CatalogProvider.info(name)
  def title: String    = info.fold(name)(_.title)
  def licence: String  = info.fold("")(_.licence)
  def citation: String = info.fold("")(_.citation)
  def home: String     = info.fold("")(_.home)
  def parts(query: CatalogueQuery): List[String] = Nil
  def read(part: String, query: CatalogueQuery, http: PluginHttp): Either[String, CataloguePage] =
    Left(s"$name is Histmersion's own import; it is read by the host, not through the SPI")

/** Today's event catalogues as BUILT-IN plugins: in every binary, each enabled by default and switched
  * off by `histmersion.plugins.events-<catalogue>.enabled = false`; their settings stay `events.<catalogue>.*`. */
object BuiltinCatalogues:

  val entries: List[HostEventCatalogue] = List(
    HostEventCatalogue("events-gvp", CatalogProvider.Gvp),
    HostEventCatalogue("events-noaa", CatalogProvider.Noaa),
    HostEventCatalogue("events-wikidata", CatalogProvider.Wikidata))

  val pluginIds: Set[String] = entries.map(_.plugin).toSet

  def plugins: List[Plugin] = entries.map(e => Builtin(e.plugin, _.eventCatalogues.add(e.name, e)))

  /** The other names `events import` takes for a built-in catalogue. */
  val aliases: Map[String, String] = Map(
    "smithsonian" -> CatalogProvider.Gvp, "volcano" -> CatalogProvider.Gvp,
    "ncei" -> CatalogProvider.Noaa, "wd" -> CatalogProvider.Wikidata)

  /** Names a plugin's catalogue may not take. */
  val reserved: Set[String] = CatalogProvider.all.toSet ++ aliases.keySet

  /** Pure: the catalogue an import names — an alias resolved, else as given (lower case). */
  def canonical(asked: String): String =
    val s = asked.trim.toLowerCase(java.util.Locale.ROOT)
    aliases.getOrElse(s, s)

  /** Pure: registered names, the built-ins first in their order, then the plugins' in registration order. */
  def ordered(registered: List[String]): List[String] =
    CatalogProvider.all.filter(registered.contains) ++ registered.filterNot(CatalogProvider.all.contains)

  /** The catalogue `asked` names among the registered ones, or why none: unknown, or its plugin off. */
  def resolve(asked: String, catalogues: ListMap[String, EventCatalogue]): Either[String, String] =
    val name = canonical(asked)
    if catalogues.contains(name) then Right(name)
    else entries.find(_.name == name) match
      case Some(e) => Left(s"${e.title} ($name) is switched off (histmersion.plugins.${e.plugin}.enabled = false)")
      case None    => Left(s"unknown event catalogue '${asked.trim}' (one of: ${ordered(catalogues.keys.toList).mkString(", ")})")

  /** A catalogue's terms as the card and the list show them: a registered one's, else a built-in's
    * (records of a catalogue switched off since are still on the map). */
  def info(name: String, catalogues: ListMap[String, EventCatalogue]): Option[CatalogProviderInfo] =
    catalogues.get(name) match
      case Some(_: HostEventCatalogue) | None => CatalogProvider.info(name)
      case Some(c) =>
        PluginEvents.safe(CatalogProviderInfo(name, c.title, c.licence, c.citation, c.home))

  def infos(catalogues: ListMap[String, EventCatalogue]): List[CatalogProviderInfo] =
    ordered(catalogues.keys.toList).flatMap(info(_, catalogues))

/** A plugin's catalogue as the host reads it: the query it is asked, its polite HTTP, and its records
  * checked and turned into the host's [[CatalogRecord]]s. Pure but for [[http]]. */
object PluginEvents:

  private val Qid = "^Q[1-9][0-9]*$"

  /** Pure: the limits of a plugin catalogue's dating — the present, and the catalogue's own
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
