package com.histmersion.plugins.events

import com.histmersion.plugin.Plugin.Config.given
import hearth.kindlings.sconfigderivation.ConfigReader

/** `histmersion.events.wikidata`: the query service, the pace, the classes read (`Q178561:battle,…`, the most specific
  * first) and the label languages.
  */
final case class WikidataSettings(sparqlUrl: WikidataSettings.SparqlUrl, requestIntervalMs: WikidataSettings.RequestIntervalMs, classes: WikidataSettings.Classes, languages: WikidataSettings.Languages)
    derives ConfigReader:
  /** The label languages, English always among them. */
  def labelLanguages: String =
    (languages.split(",").map(_.trim).filter(_.nonEmpty).toList :+ "en").distinct.mkString(",")

object WikidataSettings:
  type SparqlUrl = SparqlUrl.T
  object SparqlUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RequestIntervalMs = RequestIntervalMs.T
  object RequestIntervalMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Classes = Classes.T
  object Classes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Languages = Languages.T
  object Languages extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
