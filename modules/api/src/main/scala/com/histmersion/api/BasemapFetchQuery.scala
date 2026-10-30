package com.histmersion.api

final case class BasemapFetchQuery(
    layer: String = "all",
    maxZoom: Option[Int] = None,
    terrainMaxZoom: Option[Int] = None,
    bbox: Option[String] = None,
    dryRun: Boolean = false,
    keepAll: Boolean = false,
    focus: Option[String] = None
)
