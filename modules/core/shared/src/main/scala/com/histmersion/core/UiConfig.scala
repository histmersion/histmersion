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
    time: TimeConfig,
    /** At or below this viewport width (CSS px) the web UI uses its narrow, phone layout. */
    narrowMaxWidthPx: Int,
    /** …or, on a touch screen, at or below this height (a phone held sideways). */
    narrowMaxHeightPx: Int,
    /** Whether the server gates writes behind `Authorization: Bearer <key>` (`auth.api-key` is set).
      * NOT a configuration key of its own: the server fills it in from `auth` when it answers
      * `/api/config`, so the page can hide its write controls until the reader has entered a key. */
    writeKeyRequired: Boolean = false
) derives KindlingsCodecAsObject
object UiConfig:
  given Schema[UiConfig] = KindlingsSchema.derived[UiConfig].schema
