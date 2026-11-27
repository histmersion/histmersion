package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

@movesTo(FeaturePlace.CatalogueEvents)
final case class EventsWikidataConfig(
    sparqlUrl: EventsWikidataConfig.SparqlUrl,
    apiUrl: EventsWikidataConfig.ApiUrl,
    requestIntervalMs: EventsWikidataConfig.RequestIntervalMs,
    apiIntervalMs: EventsWikidataConfig.ApiIntervalMs,
    classes: EventsWikidataConfig.Classes,
    languages: EventsWikidataConfig.Languages,
    batch: EventsWikidataConfig.Batch
) derives ConfigReader:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def languageList: List[EventsWikidataConfig.Language] =
    languages.value.split(",").map(_.trim).filter(_.nonEmpty).toList.map(EventsWikidataConfig.Language(_))

object EventsWikidataConfig:
  type Language = Language.T
  object Language extends Newtype[String, Pure]

  type SparqlUrl = SparqlUrl.T
  object SparqlUrl extends Newtype[String, Pure]
  type ApiUrl = ApiUrl.T
  object ApiUrl extends Newtype[String, Pure]
  type RequestIntervalMs = RequestIntervalMs.T
  object RequestIntervalMs extends Newtype[Int, Pure]
  type ApiIntervalMs = ApiIntervalMs.T
  object ApiIntervalMs extends Newtype[Int, Pure]
  type Classes = Classes.T
  object Classes extends Newtype[String, Pure]
  type Languages = Languages.T
  object Languages extends Newtype[String, Pure]
  type Batch = Batch.T
  object Batch extends Newtype[Int, Pure]
