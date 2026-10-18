package com.histmersion.core

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class TimeConfig(min: Long, max: Long, defaultFrom: Long, defaultTo: Long)
    derives KindlingsCodecAsObject
object TimeConfig:
  given Schema[TimeConfig] = KindlingsSchema.derived[TimeConfig].schema

/** `previewMs`: the quiet period before a HOVERED suggestion's preview reloads the lists — shorter
  * than `reloadMs`, because a preview is read while the pointer rests and the answer takes ~20 ms. */
final case class DebounceConfig(suggestMs: Int, searchMs: Int, reloadMs: Int, previewMs: Int)
    derives KindlingsCodecAsObject
object DebounceConfig:
  given Schema[DebounceConfig] = KindlingsSchema.derived[DebounceConfig].schema

/** The events on the map in playback (#1440). A LOCAL event shows while the playhead is inside its
  * span and fades out over `localFadeSeconds` of playback after it ends; a REGIONAL one shows its
  * label and the region's faint outline for its span and fades over `regionalFadeSeconds`. Seconds of
  * PLAYBACK (at the speed playing), so a fade takes as long at 1× over 3000 years as over 300 —
  * paused, it holds. The feed returns at most `mapLimit` events, each area outline simplified to
  * `outlineToleranceDeg` degrees. */
final case class EventsUiConfig(
    localFadeSeconds: Double,
    regionalFadeSeconds: Double,
    mapLimit: Int,
    outlineToleranceDeg: Double
) derives KindlingsCodecAsObject
object EventsUiConfig:
  given Schema[EventsUiConfig] = KindlingsSchema.derived[EventsUiConfig].schema

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
    /** The events layer of the map's playback (#1440). */
    events: EventsUiConfig,
    /** How often the page re-reads the toolbar's counts and "something new" marks (`GET
      * /api/ui/counters`), in seconds; it also re-reads them after its own changes and when the tab
      * comes back into view. */
    countersPollSeconds: Int = 30,
    /** Whether the server gates writes behind `Authorization: Bearer <key>` (`auth.api-key` is set).
      * NOT a configuration key of its own: the server fills it in from `auth` when it answers
      * `/api/config`, so the page can hide its write controls until the reader has entered a key. */
    writeKeyRequired: Boolean = false,
    /** Whether the server runs embedded agents (`agents.enabled`); like `writeKeyRequired`, filled in
      * by the server, so the page offers the agent runs and approvals windows only when they work. */
    agentsEnabled: Boolean = false,
    /** Whether the configured `tiles` are a light or a dark map (`ui.tiles-tone`: "light" or "dark"):
      * the map's place names are drawn dark on a light halo over light tiles, and the inverse over dark. */
    tilesTone: String = "light",
    /** The server's plugins (`histmersion.plugins`), filled in by the server like `writeKeyRequired` —
      * NOT a configuration key of its own. Secret settings show their reference, never the value. */
    plugins: PluginsReport = PluginsReport()
) derives KindlingsCodecAsObject
object UiConfig:
  given Schema[UiConfig] = KindlingsSchema.derived[UiConfig].schema
