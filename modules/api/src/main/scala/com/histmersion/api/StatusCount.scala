package com.histmersion
package api

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.library.Source
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class StatusCount(status: Source.Status, count: StatusCount.Count)
    derives JsonCodec,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object StatusCount:
  type Count = Count.T
  object Count extends Newtype[Int, Pure]
