package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

final case class EventsWikidataConfig(
    sparqlUrl: EventsWikidataConfig.SparqlUrl,
    apiUrl: EventsWikidataConfig.ApiUrl,
    requestIntervalMs: EventsWikidataConfig.RequestIntervalMs,
    apiIntervalMs: EventsWikidataConfig.ApiIntervalMs,
    classes: EventsWikidataConfig.Classes,
    languages: EventsWikidataConfig.Languages,
    batch: EventsWikidataConfig.Batch
) derives ConfigReader:
  def languageList: List[EventsWikidataConfig.Language] = languages.value.split(",").map(_.trim).filter(_.nonEmpty).toList.map(EventsWikidataConfig.Language(_))

object EventsWikidataConfig:
  type Language = Language.T
  object Language extends RefinedType[String, Pure]

  type SparqlUrl = SparqlUrl.T
  object SparqlUrl extends RefinedType[String, Pure]
  type ApiUrl = ApiUrl.T
  object ApiUrl extends RefinedType[String, Pure]
  type RequestIntervalMs = RequestIntervalMs.T
  object RequestIntervalMs extends RefinedType[Int, Pure]
  type ApiIntervalMs = ApiIntervalMs.T
  object ApiIntervalMs extends RefinedType[Int, Pure]
  type Classes = Classes.T
  object Classes extends RefinedType[String, Pure]
  type Languages = Languages.T
  object Languages extends RefinedType[String, Pure]
  type Batch = Batch.T
  object Batch extends RefinedType[Int, Pure]
