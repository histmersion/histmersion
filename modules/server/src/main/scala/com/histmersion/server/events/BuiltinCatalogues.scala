package com.histmersion.server.events

import com.histmersion.pluginhost.{PluginEvents, SpiValues}

import com.histmersion.api.CatalogProviderInfo
import com.histmersion.domain.CatalogProvider
import com.histmersion.plugin.*
import com.histmersion.util.plugins.HostProvided

import scala.collection.immutable.ListMap

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

  def plugins: List[Plugin] = entries.map(e => HostProvided(e.plugin, _.eventCatalogues.add(e.name, e)))

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
