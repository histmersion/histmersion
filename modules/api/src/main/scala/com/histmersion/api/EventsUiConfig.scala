package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** The events on the map in playback (#1440). A LOCAL event shows while the playhead is inside its span and fades out
  * over `localFadeSeconds` of playback after it ends; a REGIONAL one shows its label and the region's faint outline for
  * its span and fades over `regionalFadeSeconds`. Seconds of PLAYBACK (at the speed playing), so a fade takes as long
  * at 1× over 3000 years as over 300 — paused, it holds. The feed returns at most `mapLimit` events, each area outline
  * simplified to `outlineToleranceDeg` degrees.
  */
final case class EventsUiConfig(
    localFadeSeconds: Double,
    regionalFadeSeconds: Double,
    mapLimit: Int,
    outlineToleranceDeg: Double
) derives KindlingsCodecAsObject,
      KindlingsSchema
object EventsUiConfig:
  given Schema[EventsUiConfig] = summon[KindlingsSchema[EventsUiConfig]].schema
