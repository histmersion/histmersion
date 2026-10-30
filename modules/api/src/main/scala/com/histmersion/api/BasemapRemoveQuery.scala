package com.histmersion.api

final case class BasemapRemoveQuery(layer: String = "all", focus: Option[String] = None, cache: Boolean = false)
