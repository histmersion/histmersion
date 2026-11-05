package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** The physical names (seas, ranges, deserts, lakes, rivers, peaks …): Natural Earth's label layers, `url` with
  * `{file}` for each of `files`, cut to the region and written as a small vector archive of zooms 0…`maxZoom`;
  * `language` is the names a style shows unless it says otherwise.
  */
final case class BasemapLabelsConfig(
    url: String,
    files: List[String],
    maxZoom: Int,
    language: String,
    attribution: String
) derives ConfigReader
