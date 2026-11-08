package com.histmersion
package api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

/** `maxZoom` is in MapLibre's zoom: the app's minus one. */
final case class MapTilesUiConfig(
    maxZoom: MapTilesUiConfig.MaxZoom = MapTilesUiConfig.MaxZoom(11),
    playbackBucketSeconds: MapTilesUiConfig.PlaybackBucketSeconds = MapTilesUiConfig.PlaybackBucketSeconds(1.0)
) derives KindlingsCodecAsObject,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object MapTilesUiConfig:
  type MaxZoom = MaxZoom.T
  object MaxZoom extends Newtype[Int, Pure]
  type PlaybackBucketSeconds = PlaybackBucketSeconds.T
  object PlaybackBucketSeconds extends Newtype[Double, Pure]
