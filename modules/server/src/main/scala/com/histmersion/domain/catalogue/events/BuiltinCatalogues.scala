package com.histmersion.domain.catalogue.events

import com.histmersion.pluginhost.{PluginEvents, SpiValues}

import com.histmersion.api.CatalogProviderInfo
import com.histmersion.plugin.*

import scala.collection.immutable.ListMap

/** Histmersion's own event catalogues — first-party plugins (`events-gvp`, `events-noaa`, `events-wikidata`), each
  * enabled by default and switched off by `histmersion.plugins.events-<catalogue>.enabled = false`, its settings
  * Histmersion's own `events.<catalogue>.*` — by the names they are imported under, which a plugin's catalogue may not
  * take. The records of one switched off since are still on the map.
  */
object BuiltinCatalogues:

  /** A first-party catalogue: its plugin and the name it registers. */
  final case class Entry(plugin: String, name: String):
    def title: String = CatalogProvider.info(name).fold(name)(_.title)

  val entries: List[Entry] = List(
    Entry("events-gvp", CatalogProvider.Gvp),
    Entry("events-noaa", CatalogProvider.Noaa),
    Entry("events-wikidata", CatalogProvider.Wikidata)
  )

  val pluginIds: Set[String] = entries.map(_.plugin).toSet

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

  /** A catalogue's terms as the card and the list show them: a registered one's, else a first-party one's (records of a
    * catalogue switched off since are still on the map).
    */
  def info(name: String, catalogues: ListMap[String, EventCatalogue]): Option[CatalogProviderInfo] =
    catalogues.get(name) match
      case None    => CatalogProvider.info(name)
      case Some(c) =>
        PluginEvents.safe(CatalogProviderInfo(name, c.title, SpiValues.licenceOf(c.licence), c.citation, c.home))

  def infos(catalogues: ListMap[String, EventCatalogue]): List[CatalogProviderInfo] =
    ordered(catalogues.keys.toList).flatMap(info(_, catalogues))
