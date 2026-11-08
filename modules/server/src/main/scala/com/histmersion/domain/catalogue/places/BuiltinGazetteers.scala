package com.histmersion
package domain.catalogue.places

import com.histmersion.plugin.GazetteerSource
import com.histmersion.pluginhost.Registries
import com.histmersion.util.plugins.Plugin
import com.histmersion.domain.catalogue.periods.PeriodOFetch

import scala.collection.immutable.ListMap
import com.histmersion.api.catalogue.Gazetteer
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** Histmersion's own network gazetteers — first-party plugins (`gazetteer-cliopatria`, `gazetteer-awmc`,
  * `gazetteer-chronontology`), each enabled by default and switched off by
  * `histmersion.plugins.gazetteer-<source>.enabled = false` — by the names `places fetch` and `init.fetch-sources`
  * take, which a plugin's source may not take. The places of one switched off since stay on the map.
  */
object BuiltinGazetteers:

  /** A first-party gazetteer: its plugin, the name it registers, and what to call it. */
  final case class Entry(plugin: Plugin.Id, name: Entry.Name, title: Entry.Title)

  object Entry:
    type Name = Name.T
    object Name extends RefinedType[String, Pure]
    type Title = Title.T
    object Title extends RefinedType[String, Pure]

  val entries: List[Entry] = List(
    Entry(Plugin.Id("gazetteer-cliopatria"), Entry.Name("cliopatria"), Entry.Title("Seshat Cliopatria polity polygons")),
    Entry(Plugin.Id("gazetteer-awmc"), Entry.Name("awmc"), Entry.Title("AWMC classical-world regional names and features")),
    Entry(Plugin.Id("gazetteer-chronontology"), Entry.Name("chronontology"), Entry.Title("ChronOntology named periods"))
  )

  val pluginIds: Set[Plugin.Id] = entries.map(_.plugin).toSet

  /** The first-party names, in the order they are listed. */
  val names: List[Entry.Name] = entries.map(_.name)

  /** Names a plugin's source may not take: these, and the other names `places fetch` and `init.fetch-sources` take (the
    * coastline, PeriodO, the bundled extracts).
    */
  val reserved: Set[Entry.Name] =
    names.toSet ++ Set(NaturalEarthLand.name, PeriodOFetch.name).map(Entry.Name(_)) ++
      Gazetteer.Kind.values.map(k => Entry.Name(k.wire.value))

  /** Pure: the registered sources that are not first-party, in registration order. */
  def thirdParty(registered: ListMap[Registries.GazetteerSourcesKey, GazetteerSource]): List[Entry.Name] =
    registered.keys.toList.map(k => Entry.Name(k.value)).filterNot(names.contains)

  /** Pure: the names a fetch may ask for — the first-party ones in their order (one switched off says so when asked),
    * then the plugins' in registration order.
    */
  def available(registered: ListMap[Registries.GazetteerSourcesKey, GazetteerSource]): List[Entry.Name] = names ++ thirdParty(registered)

  /** The registered source `name` (trimmed, lower case) names, or why none: a first-party one switched off, else
    * `unknown`.
    */
  def resolve(
      name: Entry.Name,
      registered: ListMap[Registries.GazetteerSourcesKey, GazetteerSource],
      unknown: => GazetteerImports.Problem
  ): Either[GazetteerImports.Problem, GazetteerSource] =
    registered.get(Registries.GazetteerSourcesKey(name.value)).toRight {
      entries
        .find(_.name == name)
        .fold(unknown)(e =>
          GazetteerImports.Problem(
            s"${e.title} ($name) is switched off (histmersion.plugins.${e.plugin}.enabled = false)"
          )
        )
    }
