package com.histmersion.core

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class TimeConfig(min: Long, max: Long, defaultFrom: Long, defaultTo: Long)
    derives KindlingsCodecAsObject
object TimeConfig:
  given Schema[TimeConfig] = KindlingsSchema.derived[TimeConfig].schema

final case class DebounceConfig(suggestMs: Int, searchMs: Int, reloadMs: Int)
    derives KindlingsCodecAsObject
object DebounceConfig:
  given Schema[DebounceConfig] = KindlingsSchema.derived[DebounceConfig].schema

final case class UiConfig(
    tiles: String,
    tilesMaxZoom: Int,
    markerRadius: Int,
    viewportDebounceMs: Int,
    pageSize: Int,
    facetSuggestions: Int,
    treeDepth: Int,
    treeSamples: Int,
    maxBuckets: Int,
    debounce: DebounceConfig,
    time: TimeConfig
) derives KindlingsCodecAsObject
object UiConfig:
  given Schema[UiConfig] = KindlingsSchema.derived[UiConfig].schema
