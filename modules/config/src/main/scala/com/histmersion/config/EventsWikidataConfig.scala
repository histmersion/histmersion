package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

final case class EventsWikidataConfig(
    sparqlUrl: String,
    apiUrl: String,
    requestIntervalMs: Int,
    apiIntervalMs: Int,
    classes: String,
    languages: String,
    batch: Int
) derives ConfigReader:
  def languageList: List[String] = languages.split(",").map(_.trim).filter(_.nonEmpty).toList
