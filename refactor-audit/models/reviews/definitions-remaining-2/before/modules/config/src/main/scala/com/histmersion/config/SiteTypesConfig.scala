package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** Refining Wikidata sites' types from their items' classes (`events.site-types`). */
final case class SiteTypesConfig(batch: Int, superclassDepth: Int) derives ConfigReader
