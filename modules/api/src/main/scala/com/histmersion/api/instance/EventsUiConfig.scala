package com.histmersion
package api.instance
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class EventsUiConfig(
    localFadeSeconds: EventsUiConfig.LocalFadeSeconds,
    regionalFadeSeconds: EventsUiConfig.RegionalFadeSeconds,
    mapLimit: EventsUiConfig.MapLimit,
    outlineToleranceDeg: EventsUiConfig.OutlineToleranceDeg
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object EventsUiConfig:
  type LocalFadeSeconds = LocalFadeSeconds.T
  object LocalFadeSeconds extends Newtype[Double, Pure]
  type RegionalFadeSeconds = RegionalFadeSeconds.T
  object RegionalFadeSeconds extends Newtype[Double, Pure]
  type MapLimit = MapLimit.T
  object MapLimit extends Newtype[Int, Pure]
  type OutlineToleranceDeg = OutlineToleranceDeg.T
  object OutlineToleranceDeg extends Newtype[Double, Pure]
