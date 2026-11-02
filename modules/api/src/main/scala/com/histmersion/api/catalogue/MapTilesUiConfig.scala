package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

/** `maxZoom` is in MapLibre's zoom: the app's minus one. */
final case class MapTilesUiConfig(maxZoom: Int = 11, playbackBucketSeconds: Double = 1.0)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object MapTilesUiConfig:
  given Schema[MapTilesUiConfig] = summon[KindlingsSchema[MapTilesUiConfig]].schema
