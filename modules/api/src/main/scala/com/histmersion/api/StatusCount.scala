package com.histmersion
package api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.library.Source
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

final case class StatusCount(status: Source.Status, count: StatusCount.Count) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

object StatusCount:
  type Count = Count.T
  object Count extends RefinedType[Int, Pure]
