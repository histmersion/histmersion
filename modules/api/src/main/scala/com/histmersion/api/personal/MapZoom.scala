package com.histmersion.api.personal

import io.circe.{Decoder, Encoder}
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure
import sttp.tapir.Schema

type MapZoom = MapZoom.T

object MapZoom extends RefinedType[Double, Pure]:
  given Encoder[MapZoom] = Encoder.encodeDouble.contramap(_.value)
  given Decoder[MapZoom] = Decoder.decodeDouble.map(MapZoom(_))
  given Schema[MapZoom] = Schema.schemaForDouble.as[MapZoom]
