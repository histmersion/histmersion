package com.histmersion.api.instance
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class EventsUiConfig(
    localFadeSeconds: Double,
    regionalFadeSeconds: Double,
    mapLimit: Int,
    outlineToleranceDeg: Double
) derives KindlingsCodecAsObject,
      KindlingsSchema
object EventsUiConfig:
  given Schema[EventsUiConfig] = summon[KindlingsSchema[EventsUiConfig]].schema
