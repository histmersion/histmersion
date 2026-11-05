package com.histmersion.plugins.events

import com.histmersion.plugin.Plugin.Config.given
import hearth.kindlings.sconfigderivation.ConfigReader

/** `histmersion.events.wikidata`: the query service, the pace, the classes read (`Q178561:battle,…`, the most specific
  * first) and the label languages.
  */
final case class WikidataSettings(sparqlUrl: String, requestIntervalMs: Int, classes: String, languages: String)
    derives ConfigReader:
  /** The label languages, English always among them. */
  def labelLanguages: String =
    (languages.split(",").map(_.trim).filter(_.nonEmpty).toList :+ "en").distinct.mkString(",")
