package com.histmersion.api.basemap
import com.histmersion.api.given
final case class BasemapRemoveQuery(layer: String = "all", focus: Option[String] = None, cache: Boolean = false)
