package com.histmersion.domain.catalogue.places

import com.histmersion.api.GazetteerKind
import com.histmersion.plugin.GazetteerSource
import com.histmersion.server.importers.{NaturalEarthLand, PeriodOFetch}

import scala.collection.immutable.ListMap

/** Histmersion's own network gazetteers — first-party plugins (`gazetteer-cliopatria`, `gazetteer-awmc`,
  * `gazetteer-chronontology`), each enabled by default and switched off by
  * `histmersion.plugins.gazetteer-<source>.enabled = false` — by the names `places fetch` and `init.fetch-sources` take,
  * which a plugin's source may not take. The places of one switched off since stay on the map.
  */
object BuiltinGazetteers:

  /** A first-party gazetteer: its plugin, the name it registers, and what to call it. */
  final case class Entry(plugin: String, name: String, title: String)

  val entries: List[Entry] = List(
    Entry("gazetteer-cliopatria", "cliopatria", "Seshat Cliopatria polity polygons"),
    Entry("gazetteer-awmc", "awmc", "AWMC classical-world regional names and features"),
    Entry("gazetteer-chronontology", "chronontology", "ChronOntology named periods")
  )

  val pluginIds: Set[String] = entries.map(_.plugin).toSet

  /** The first-party names, in the order they are listed. */
  val names: List[String] = entries.map(_.name)

  /** Names a plugin's source may not take: these, and the other names `places fetch` and `init.fetch-sources` take
    * (the coastline, PeriodO, the bundled extracts).
    */
  val reserved: Set[String] =
    names.toSet ++ Set(NaturalEarthLand.name, PeriodOFetch.name) ++ GazetteerKind.values.map(_.wire)

  /** Pure: the registered sources that are not first-party, in registration order. */
  def thirdParty(registered: ListMap[String, GazetteerSource]): List[String] =
    registered.keys.toList.filterNot(names.contains)

  /** Pure: the names a fetch may ask for — the first-party ones in their order (one switched off says so when asked),
    * then the plugins' in registration order.
    */
  def available(registered: ListMap[String, GazetteerSource]): List[String] = names ++ thirdParty(registered)

  /** The registered source `name` (trimmed, lower case) names, or why none: a first-party one switched off, else
    * `unknown`.
    */
  def resolve(
      name: String,
      registered: ListMap[String, GazetteerSource],
      unknown: => String
  ): Either[String, GazetteerSource] =
    registered.get(name).toRight {
      entries
        .find(_.name == name)
        .fold(unknown)(e => s"${e.title} ($name) is switched off (histmersion.plugins.${e.plugin}.enabled = false)")
    }
