package com.histmersion.domain.catalogue.places

import com.histmersion.plugin.GazetteerSource
import com.histmersion.domain.catalogue.periods.PeriodOFetch

import scala.collection.immutable.ListMap
import com.histmersion.api.catalogue.Gazetteer

/** Histmersion's own network gazetteers — first-party plugins (`gazetteer-cliopatria`, `gazetteer-awmc`,
  * `gazetteer-chronontology`), each enabled by default and switched off by
  * `histmersion.plugins.gazetteer-<source>.enabled = false` — by the names `places fetch` and `init.fetch-sources`
  * take, which a plugin's source may not take. The places of one switched off since stay on the map.
  */
object BuiltinGazetteers:

  /** A first-party gazetteer: its plugin, the name it registers, and what to call it. */
  type PluginIds = PluginIds.T
  object PluginIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Names = Names.T
  object Names extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Reserved = Reserved.T
  object Reserved extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThirdPartyResult = ThirdPartyResult.T
  object ThirdPartyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThirdPartyRegisteredPart1 = ThirdPartyRegisteredPart1.T
  object ThirdPartyRegisteredPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AvailableResult = AvailableResult.T
  object AvailableResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AvailableRegisteredPart1 = _root_.com.histmersion.domain.catalogue.places.BuiltinGazetteers.ThirdPartyRegisteredPart1
  val AvailableRegisteredPart1: _root_.com.histmersion.domain.catalogue.places.BuiltinGazetteers.ThirdPartyRegisteredPart1.type = _root_.com.histmersion.domain.catalogue.places.BuiltinGazetteers.ThirdPartyRegisteredPart1
  type ResolveResultPart1 = ResolveResultPart1.T
  object ResolveResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolveName = ResolveName.T
  object ResolveName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolveRegisteredPart1 = ResolveRegisteredPart1.T
  object ResolveRegisteredPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Entry(plugin: Entry.Plugin, name: Entry.Name, title: Entry.Title)

  object Entry:
    type Plugin = Plugin.T
    object Plugin extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val entries: List[Entry] = List(
    Entry("gazetteer-cliopatria", "cliopatria", "Seshat Cliopatria polity polygons"),
    Entry("gazetteer-awmc", "awmc", "AWMC classical-world regional names and features"),
    Entry("gazetteer-chronontology", "chronontology", "ChronOntology named periods")
  )

  val pluginIds: Set[com.histmersion.domain.catalogue.places.BuiltinGazetteers.PluginIds] = entries.map(_.plugin).toSet

  /** The first-party names, in the order they are listed. */
  val names: List[com.histmersion.domain.catalogue.places.BuiltinGazetteers.Names] = entries.map(_.name)

  /** Names a plugin's source may not take: these, and the other names `places fetch` and `init.fetch-sources` take (the
    * coastline, PeriodO, the bundled extracts).
    */
  val reserved: Set[com.histmersion.domain.catalogue.places.BuiltinGazetteers.Reserved] =
    names.toSet ++ Set(NaturalEarthLand.name, PeriodOFetch.name) ++ Gazetteer.Kind.values.map(_.wire)

  /** Pure: the registered sources that are not first-party, in registration order. */
  def thirdParty(registered: ListMap[com.histmersion.domain.catalogue.places.BuiltinGazetteers.ThirdPartyRegisteredPart1, GazetteerSource]): List[com.histmersion.domain.catalogue.places.BuiltinGazetteers.ThirdPartyResult] =
    registered.keys.toList.filterNot(names.contains)

  /** Pure: the names a fetch may ask for — the first-party ones in their order (one switched off says so when asked),
    * then the plugins' in registration order.
    */
  def available(registered: ListMap[com.histmersion.domain.catalogue.places.BuiltinGazetteers.AvailableRegisteredPart1, GazetteerSource]): List[com.histmersion.domain.catalogue.places.BuiltinGazetteers.AvailableResult] = names ++ thirdParty(registered)

  /** The registered source `name` (trimmed, lower case) names, or why none: a first-party one switched off, else
    * `unknown`.
    */
  def resolve(
      name: com.histmersion.domain.catalogue.places.BuiltinGazetteers.ResolveName,
      registered: ListMap[com.histmersion.domain.catalogue.places.BuiltinGazetteers.ResolveRegisteredPart1, GazetteerSource],
      unknown: => String
  ): Either[com.histmersion.domain.catalogue.places.BuiltinGazetteers.ResolveResultPart1, GazetteerSource] =
    registered.get(name).toRight {
      entries
        .find(_.name == name)
        .fold(unknown)(e => s"${e.title} ($name) is switched off (histmersion.plugins.${e.plugin}.enabled = false)")
    }
