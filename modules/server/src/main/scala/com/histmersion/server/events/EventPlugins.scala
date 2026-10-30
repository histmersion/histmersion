package com.histmersion.server.events

import com.histmersion.pluginhost.{HostOwned, PluginEvents, SpiValues}

import com.histmersion.api.{BBox, CatalogProviderInfo, CatalogRecord, EventTaxonomy, TimeSpan}
import com.histmersion.domain.{CatalogDating, CatalogProvider}
import com.histmersion.plugin.*
import com.histmersion.server.acquire.fetchers.Builtin

import scala.collection.immutable.ListMap
import scala.util.control.NonFatal
import com.histmersion.values.Historicity

/** An event catalogue that is Histmersion's OWN code (GVP, NOAA, Wikidata), registered through the plugin host like any
  * other (docs/research-plugins.md §6, D2) but read by the host's own import with its `events.<catalogue>.*` settings,
  * unchanged. The SPI's `parts`/`read` are never called for it.
  */
final case class HostEventCatalogue(plugin: String, name: String) extends EventCatalogue, HostOwned:
  private def info: Option[CatalogProviderInfo] = CatalogProvider.info(name)
  def title: String = info.fold(name)(_.title)
  def licence: Licence = Licence(info.fold("")(_.licence))
  def citation: String = info.fold("")(_.citation)
  def home: String = info.fold("")(_.home)
  def parts(query: CatalogueQuery): List[String] = Nil
  def read(part: String, query: CatalogueQuery, http: PluginHttp): Either[String, CataloguePage] =
    Left(s"$name is Histmersion's own import; it is read by the host, not through the SPI")

/** Today's event catalogues as BUILT-IN plugins: in every binary, each enabled by default and switched off by
  * `histmersion.plugins.events-<catalogue>.enabled = false`; their settings stay `events.<catalogue>.*`.
  */
object BuiltinCatalogues:

  val entries: List[HostEventCatalogue] = List(
    HostEventCatalogue("events-gvp", CatalogProvider.Gvp),
    HostEventCatalogue("events-noaa", CatalogProvider.Noaa),
    HostEventCatalogue("events-wikidata", CatalogProvider.Wikidata)
  )

  val pluginIds: Set[String] = entries.map(_.plugin).toSet

  def plugins: List[Plugin] = entries.map(e => Builtin(e.plugin, _.eventCatalogues.add(e.name, e)))

  /** The other names `events import` takes for a built-in catalogue. */
  val aliases: Map[String, String] = Map(
    "smithsonian" -> CatalogProvider.Gvp,
    "volcano" -> CatalogProvider.Gvp,
    "ncei" -> CatalogProvider.Noaa,
    "wd" -> CatalogProvider.Wikidata
  )

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
    else
      entries.find(_.name == name) match
        case Some(e) => Left(s"${e.title} ($name) is switched off (histmersion.plugins.${e.plugin}.enabled = false)")
        case None    =>
          Left(s"unknown event catalogue '${asked.trim}' (one of: ${ordered(catalogues.keys.toList).mkString(", ")})")

  /** A catalogue's terms as the card and the list show them: a registered one's, else a built-in's (records of a
    * catalogue switched off since are still on the map).
    */
  def info(name: String, catalogues: ListMap[String, EventCatalogue]): Option[CatalogProviderInfo] =
    catalogues.get(name) match
      case Some(_: HostEventCatalogue) | None => CatalogProvider.info(name)
      case Some(c)                            =>
        PluginEvents.safe(CatalogProviderInfo(name, c.title, SpiValues.licenceOf(c.licence), c.citation, c.home))

  def infos(catalogues: ListMap[String, EventCatalogue]): List[CatalogProviderInfo] =
    ordered(catalogues.keys.toList).flatMap(info(_, catalogues))
