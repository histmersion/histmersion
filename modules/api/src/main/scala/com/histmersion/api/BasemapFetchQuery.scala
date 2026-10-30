package com.histmersion.api

/** The fetch's options: which layers, how deep, where; a dry run downloads only the directories and reports what the
  * extract would take. Empty values fall back to the configuration.
  */
final case class BasemapFetchQuery(
    layer: String = "all",
    maxZoom: Option[Int] = None,
    terrainMaxZoom: Option[Int] = None,
    bbox: Option[String] = None,
    dryRun: Boolean = false,
    keepAll: Boolean = false,
    focus: Option[String] = None
)
