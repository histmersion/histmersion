package com.histmersion.domain.catalogue.events

import com.histmersion.pluginhost.{PluginEvents, SpiValues}

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
  type AliasesPart1 = AliasesPart1.T
  object AliasesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AliasesPart2 = AliasesPart2.T
  object AliasesPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Reserved = Reserved.T
  object Reserved extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CanonicalResult = CanonicalResult.T
  object CanonicalResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CanonicalAsked = CanonicalAsked.T
  object CanonicalAsked extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OrderedResult = OrderedResult.T
  object OrderedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OrderedRegistered = OrderedRegistered.T
  object OrderedRegistered extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolveResultPart1 = ResolveResultPart1.T
  object ResolveResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolveResultPart2 = ResolveResultPart2.T
  object ResolveResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolveAsked = _root_.com.histmersion.domain.catalogue.events.BuiltinCatalogues.CanonicalAsked
  val ResolveAsked: _root_.com.histmersion.domain.catalogue.events.BuiltinCatalogues.CanonicalAsked.type = _root_.com.histmersion.domain.catalogue.events.BuiltinCatalogues.CanonicalAsked
  type ResolveCataloguesPart1 = ResolveCataloguesPart1.T
  object ResolveCataloguesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InfoName = InfoName.T
  object InfoName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InfoCataloguesPart1 = InfoCataloguesPart1.T
  object InfoCataloguesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InfosCataloguesPart1 = _root_.com.histmersion.domain.catalogue.events.BuiltinCatalogues.InfoCataloguesPart1
  val InfosCataloguesPart1: _root_.com.histmersion.domain.catalogue.events.BuiltinCatalogues.InfoCataloguesPart1.type = _root_.com.histmersion.domain.catalogue.events.BuiltinCatalogues.InfoCataloguesPart1

  final case class Entry(plugin: Entry.Plugin, name: Entry.Name):
    def title: com.histmersion.domain.catalogue.events.BuiltinCatalogues.Entry.TitleResult = CatalogProvider.info(name).fold(name)(_.title)

  object Entry:
    type TitleResult = TitleResult.T
    object TitleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Plugin = Plugin.T
    object Plugin extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val entries: List[Entry] = List(
    Entry("events-gvp", CatalogProvider.Gvp),
    Entry("events-noaa", CatalogProvider.Noaa),
    Entry("events-wikidata", CatalogProvider.Wikidata)
  )

  val pluginIds: Set[com.histmersion.util.plugins.Plugin.Id] = entries.map(_.plugin).toSet

  /** The other names `events import` takes for a built-in catalogue. */
  val aliases: Map[com.histmersion.domain.catalogue.events.BuiltinCatalogues.AliasesPart1, com.histmersion.domain.catalogue.events.BuiltinCatalogues.AliasesPart2] = Map(
    "smithsonian" -> CatalogProvider.Gvp,
    "volcano" -> CatalogProvider.Gvp,
    "ncei" -> CatalogProvider.Noaa,
    "wd" -> CatalogProvider.Wikidata
  )

  /** Names a plugin's catalogue may not take. */
  val reserved: Set[com.histmersion.domain.catalogue.events.BuiltinCatalogues.Reserved] = CatalogProvider.all.toSet ++ aliases.keySet

  /** Pure: the catalogue an import names — an alias resolved, else as given (lower case). */
  def canonical(asked: com.histmersion.domain.catalogue.events.BuiltinCatalogues.CanonicalAsked): com.histmersion.domain.catalogue.events.BuiltinCatalogues.CanonicalResult =
    val s = asked.trim.toLowerCase(java.util.Locale.ROOT)
    aliases.getOrElse(s, s)

  /** Pure: registered names, the built-ins first in their order, then the plugins' in registration order. */
  def ordered(registered: List[com.histmersion.domain.catalogue.events.BuiltinCatalogues.OrderedRegistered]): List[com.histmersion.domain.catalogue.events.BuiltinCatalogues.OrderedResult] =
    CatalogProvider.all.filter(registered.contains) ++ registered.filterNot(CatalogProvider.all.contains)

  /** The catalogue `asked` names among the registered ones, or why none: unknown, or its plugin off. */
  def resolve(asked: com.histmersion.domain.catalogue.events.BuiltinCatalogues.ResolveAsked, catalogues: ListMap[com.histmersion.domain.catalogue.events.BuiltinCatalogues.ResolveCataloguesPart1, EventCatalogue]): Either[com.histmersion.domain.catalogue.events.BuiltinCatalogues.ResolveResultPart1, com.histmersion.domain.catalogue.events.BuiltinCatalogues.ResolveResultPart2] =
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
  def info(name: com.histmersion.domain.catalogue.events.BuiltinCatalogues.InfoName, catalogues: ListMap[com.histmersion.domain.catalogue.events.BuiltinCatalogues.InfoCataloguesPart1, EventCatalogue]): Option[CatalogProviderInfo] =
    catalogues.get(name) match
      case None    => CatalogProvider.info(name)
      case Some(c) =>
        PluginEvents.safe(CatalogProviderInfo(name, c.title, SpiValues.licenceOf(c.licence), c.citation, c.home))

  def infos(catalogues: ListMap[com.histmersion.domain.catalogue.events.BuiltinCatalogues.InfosCataloguesPart1, EventCatalogue]): List[CatalogProviderInfo] =
    ordered(catalogues.keys.toList).flatMap(info(_, catalogues))
