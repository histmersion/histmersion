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
  type AvailableRegisteredPart1 = _root_.com.histmersion.domain.catalogue.places.BuiltinGazetteers.Entry.Name
  val AvailableRegisteredPart1: _root_.com.histmersion.domain.catalogue.places.BuiltinGazetteers.Entry.Name.type = _root_.com.histmersion.domain.catalogue.places.BuiltinGazetteers.Entry.Name

  final case class Entry(plugin: Entry.Plugin, name: Entry.Name, title: Entry.Title)

  object Entry:
    type Plugin = Plugin.T
    object Plugin extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val entries: List[Entry] = List(
    Entry(com.histmersion.domain.catalogue.places.BuiltinGazetteers.Entry.Plugin("gazetteer-cliopatria"), com.histmersion.domain.catalogue.places.BuiltinGazetteers.Entry.Name("cliopatria"), com.histmersion.domain.catalogue.places.BuiltinGazetteers.Entry.Title("Seshat Cliopatria polity polygons")),
    Entry(com.histmersion.domain.catalogue.places.BuiltinGazetteers.Entry.Plugin("gazetteer-awmc"), com.histmersion.domain.catalogue.places.BuiltinGazetteers.Entry.Name("awmc"), com.histmersion.domain.catalogue.places.BuiltinGazetteers.Entry.Title("AWMC classical-world regional names and features")),
    Entry(com.histmersion.domain.catalogue.places.BuiltinGazetteers.Entry.Plugin("gazetteer-chronontology"), com.histmersion.domain.catalogue.places.BuiltinGazetteers.Entry.Name("chronontology"), com.histmersion.domain.catalogue.places.BuiltinGazetteers.Entry.Title("ChronOntology named periods"))
  )

  val pluginIds: Set[com.histmersion.util.plugins.Plugin.Id] = entries.map(_.plugin).toSet

  /** The first-party names, in the order they are listed. */
  val names: List[com.histmersion.domain.catalogue.places.BuiltinGazetteers.Entry.Name] = entries.map(_.name)

  /** Names a plugin's source may not take: these, and the other names `places fetch` and `init.fetch-sources` take (the
    * coastline, PeriodO, the bundled extracts).
    */
  val reserved: Set[com.histmersion.domain.catalogue.places.BuiltinGazetteers.Entry.Name] =
    names.toSet ++ Set(NaturalEarthLand.name, PeriodOFetch.name) ++ Gazetteer.Kind.values.map(_.wire)

  /** Pure: the registered sources that are not first-party, in registration order. */
  def thirdParty(registered: ListMap[com.histmersion.domain.catalogue.places.BuiltinGazetteers.Entry.Name, GazetteerSource]): List[com.histmersion.domain.catalogue.places.BuiltinGazetteers.Entry.Name] =
    registered.keys.toList.filterNot(names.contains)

  /** Pure: the names a fetch may ask for — the first-party ones in their order (one switched off says so when asked),
    * then the plugins' in registration order.
    */
  def available(registered: ListMap[com.histmersion.domain.catalogue.places.BuiltinGazetteers.AvailableRegisteredPart1, GazetteerSource]): List[com.histmersion.domain.catalogue.places.BuiltinGazetteers.Entry.Name] = names ++ thirdParty(registered)

  /** The registered source `name` (trimmed, lower case) names, or why none: a first-party one switched off, else
    * `unknown`.
    */
  def resolve(
      name: com.histmersion.domain.catalogue.places.BuiltinGazetteers.Entry.Name,
      registered: ListMap[com.histmersion.domain.catalogue.places.BuiltinGazetteers.Entry.Name, GazetteerSource],
      unknown: => String
  ): Either[com.histmersion.domain.catalogue.places.BuiltinGazetteers.Entry.Name, GazetteerSource] =
    registered.get(name).toRight {
      entries
        .find(_.name == name)
        .fold(unknown)(e => s"${e.title} ($name) is switched off (histmersion.plugins.${e.plugin}.enabled = false)")
    }
