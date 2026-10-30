package com.histmersion.api.personal

import io.circe.{Decoder, Encoder}
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.Interval
import sttp.tapir.Schema

type MapZoom = MapZoom.T

object MapZoom extends RefinedType[Double, Interval.Closed[0.0, 24.0]]:
  given Encoder[MapZoom] = Encoder.encodeDouble.contramap(_.value)
  given Decoder[MapZoom] = Decoder.decodeDouble.emap(either)
  given Schema[MapZoom] = Schema.schemaForDouble.as[MapZoom]
