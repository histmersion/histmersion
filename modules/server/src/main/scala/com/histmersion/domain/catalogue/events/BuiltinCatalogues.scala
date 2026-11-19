package com.histmersion
package domain.catalogue.events

import com.histmersion.pluginhost.{PluginEvents, SpiValues}
import com.histmersion.util.problem.Problem

import com.histmersion.api.catalogue.CatalogProviderInfo
import com.histmersion.plugin.*

import scala.collection.immutable.ListMap

/** Histmersion's own event catalogues — first-party plugins (`events-gvp`, `events-noaa`, `events-wikidata`), each
  * enabled by default and switched off by `histmersion.plugins.events-<catalogue>.enabled = false`, its settings
  * Histmersion's own `events.<catalogue>.*` — by the names they are imported under, which a plugin's catalogue may not
  * take. The records of one switched off since are still on the map.
  */
object BuiltinCatalogues:

  /** A first-party catalogue: its plugin and the name it registers. */
  final case class Entry(plugin: util.plugins.Plugin.Id, name: CatalogProviderInfo.Id):
    def title: String = CatalogProvider.info(name).fold(name.value)(_.title.value)

  val entries: List[Entry] = List(
    Entry(util.plugins.Plugin.Id("events-gvp"), CatalogProvider.Gvp),
    Entry(util.plugins.Plugin.Id("events-noaa"), CatalogProvider.Noaa),
    Entry(util.plugins.Plugin.Id("events-wikidata"), CatalogProvider.Wikidata)
  )

  val pluginIds: Set[util.plugins.Plugin.Id] = entries.map(_.plugin).toSet

  /** The other names `events import` takes for a built-in catalogue. */
  val aliases: Map[CatalogProviderInfo.Id, CatalogProviderInfo.Id] = Map(
    CatalogProviderInfo.Id("smithsonian") -> CatalogProvider.Gvp,
    CatalogProviderInfo.Id("volcano") -> CatalogProvider.Gvp,
    CatalogProviderInfo.Id("ncei") -> CatalogProvider.Noaa,
    CatalogProviderInfo.Id("wd") -> CatalogProvider.Wikidata
  )

  /** Names a plugin's catalogue may not take. */
  val reserved: Set[CatalogProviderInfo.Id] = CatalogProvider.all.toSet ++ aliases.keySet

  /** Pure: the catalogue an import names — an alias resolved, else as given (lower case). */
  def canonical(asked: CatalogProviderInfo.Id): CatalogProviderInfo.Id =
    val s = CatalogProviderInfo.Id(asked.value.trim.toLowerCase(java.util.Locale.ROOT))
    aliases.getOrElse(s, s)

  /** Pure: registered names, the built-ins first in their order, then the plugins' in registration order. */
  def ordered(registered: List[CatalogProviderInfo.Id]): List[CatalogProviderInfo.Id] =
    CatalogProvider.all.filter(registered.contains) ++ registered.filterNot(CatalogProvider.all.contains)

  /** The catalogue `asked` names among the registered ones, or why none: unknown, or its plugin off. */
  def resolve(
      asked: CatalogProviderInfo.Id,
      catalogues: ListMap[CatalogProviderInfo.Id, EventCatalogue]
  ): Either[Problem, CatalogProviderInfo.Id] =
    val name = canonical(asked)
    if catalogues.contains(name) then Right(name)
    else
      entries.find(_.name == name) match
        case Some(e) =>
          Left(
            Problem.Refused.Disabled(
              Problem.Refused.Feature(s"${e.title} ($name)"),
              Problem.SettingKey(s"histmersion.plugins.${e.plugin}.enabled"),
              None,
              None
            )
          )
        case None =>
          Left(
            Problem.Invalid.Broken.here(
              Problem.Invalid.Rule
                .EventCatalogue(
                  Problem.Raw(asked.value.trim),
                  ordered(catalogues.keys.toList).toVector.map(Problem.Raw.of(_))
                )
            )
          )

  /** A catalogue's terms as the card and the list show them: a registered one's, else a first-party one's (records of a
    * catalogue switched off since are still on the map).
    */
  def info(
      name: CatalogProviderInfo.Id,
      catalogues: ListMap[CatalogProviderInfo.Id, EventCatalogue]
  ): Option[CatalogProviderInfo] =
    catalogues.get(name) match
      case None    => CatalogProvider.info(name)
      case Some(c) =>
        PluginEvents.safe(
          CatalogProviderInfo(
            name,
            CatalogProviderInfo.Title(c.title.value),
            CatalogProviderInfo.Licence(SpiValues.licenceOf(c.licence).value),
            CatalogProviderInfo.Citation(c.citation.value),
            CatalogProviderInfo.Home(c.home.value)
          )
        )

  def infos(catalogues: ListMap[CatalogProviderInfo.Id, EventCatalogue]): List[CatalogProviderInfo] =
    ordered(catalogues.keys.toList).flatMap(info(_, catalogues))
