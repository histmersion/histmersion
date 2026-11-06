package com.histmersion.api.instance
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class EventsUiConfig(
    localFadeSeconds: EventsUiConfig.LocalFadeSeconds,
    regionalFadeSeconds: EventsUiConfig.RegionalFadeSeconds,
    mapLimit: EventsUiConfig.MapLimit,
    outlineToleranceDeg: EventsUiConfig.OutlineToleranceDeg
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object EventsUiConfig:
  type LocalFadeSeconds = LocalFadeSeconds.T
  object LocalFadeSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RegionalFadeSeconds = RegionalFadeSeconds.T
  object RegionalFadeSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MapLimit = MapLimit.T
  object MapLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OutlineToleranceDeg = OutlineToleranceDeg.T
  object OutlineToleranceDeg extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
