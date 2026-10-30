package com.histmersion.api

/** `DELETE /api/basemap`: the layers' files (`all`, a layer, or `none`), one focus region (`all`: every one) and the
  * on-demand relief cache.
  */
final case class BasemapRemoveQuery(layer: String = "all", focus: Option[String] = None, cache: Boolean = false)
