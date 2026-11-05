package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

final case class EventsWikidataConfig(
    sparqlUrl: EventsWikidataConfig.SparqlUrl,
    apiUrl: EventsWikidataConfig.ApiUrl,
    requestIntervalMs: EventsWikidataConfig.RequestIntervalMs,
    apiIntervalMs: EventsWikidataConfig.ApiIntervalMs,
    classes: EventsWikidataConfig.Classes,
    languages: EventsWikidataConfig.Languages,
    batch: EventsWikidataConfig.Batch
) derives ConfigReader:
  def languageList: List[com.histmersion.config.EventsWikidataConfig.LanguageListResult] = languages.split(",").map(_.trim).filter(_.nonEmpty).toList

object EventsWikidataConfig:
  type LanguageListResult = LanguageListResult.T
  object LanguageListResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type SparqlUrl = SparqlUrl.T
  object SparqlUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApiUrl = ApiUrl.T
  object ApiUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RequestIntervalMs = RequestIntervalMs.T
  object RequestIntervalMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApiIntervalMs = ApiIntervalMs.T
  object ApiIntervalMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Classes = Classes.T
  object Classes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Languages = Languages.T
  object Languages extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Batch = Batch.T
  object Batch extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
