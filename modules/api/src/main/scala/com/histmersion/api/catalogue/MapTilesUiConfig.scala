package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

/** `maxZoom` is in MapLibre's zoom: the app's minus one. */
final case class MapTilesUiConfig(maxZoom: MapTilesUiConfig.MaxZoom = _root_.com.histmersion.api.catalogue.MapTilesUiConfig.MaxZoom(11), playbackBucketSeconds: MapTilesUiConfig.PlaybackBucketSeconds = _root_.com.histmersion.api.catalogue.MapTilesUiConfig.PlaybackBucketSeconds(1.0))
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object MapTilesUiConfig:
  type MaxZoom = MaxZoom.T
  object MaxZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaybackBucketSeconds = PlaybackBucketSeconds.T
  object PlaybackBucketSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
