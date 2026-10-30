package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** The map's point layers as vector tiles (`ui.map-tiles`, [[TilesApi]]): the deepest tile the server cuts (MapLibre's
  * zoom: the app's minus one), and how many seconds of playback one time bucket of the layers covers.
  */
final case class MapTilesUiConfig(maxZoom: Int = 11, playbackBucketSeconds: Double = 1.0)
    derives KindlingsCodecAsObject,
      KindlingsSchema
object MapTilesUiConfig:
  given Schema[MapTilesUiConfig] = summon[KindlingsSchema[MapTilesUiConfig]].schema
