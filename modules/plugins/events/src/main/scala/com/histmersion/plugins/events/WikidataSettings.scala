package com.histmersion
package plugins.events

import com.histmersion.plugin.Plugin.Config.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

/** `histmersion.events.wikidata`: the query service, the pace, the classes read (`Q178561:battle,…`, the most specific
  * first) and the label languages.
  */
final case class WikidataSettings(sparqlUrl: WikidataSettings.SparqlUrl, requestIntervalMs: WikidataSettings.RequestIntervalMs, classes: WikidataSettings.Classes, languages: WikidataSettings.Languages)
    derives ConfigReader:
  /** The label languages, English always among them. */
  def labelLanguages: WikidataSettings.LabelLanguage =
    WikidataSettings.LabelLanguage((languages.value.split(",").map(_.trim).filter(_.nonEmpty).toList :+ "en").distinct.mkString(","))

object WikidataSettings:
  type LabelLanguage = LabelLanguage.T
  object LabelLanguage extends Newtype[String, Pure]

  type SparqlUrl = SparqlUrl.T
  object SparqlUrl extends Newtype[String, Pure]
  type RequestIntervalMs = RequestIntervalMs.T
  object RequestIntervalMs extends Newtype[Int, Pure]
  type Classes = Classes.T
  object Classes extends Newtype[String, Pure]
  type Languages = Languages.T
  object Languages extends Newtype[String, Pure]
