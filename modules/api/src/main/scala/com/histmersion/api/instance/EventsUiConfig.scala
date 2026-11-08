package com.histmersion
package api.instance
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.RefinedType
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
  object LocalFadeSeconds extends RefinedType[Double, Pure]
  type RegionalFadeSeconds = RegionalFadeSeconds.T
  object RegionalFadeSeconds extends RefinedType[Double, Pure]
  type MapLimit = MapLimit.T
  object MapLimit extends RefinedType[Int, Pure]
  type OutlineToleranceDeg = OutlineToleranceDeg.T
  object OutlineToleranceDeg extends RefinedType[Double, Pure]
