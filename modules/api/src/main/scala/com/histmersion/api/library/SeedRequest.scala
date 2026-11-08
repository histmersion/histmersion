package com.histmersion
package api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

final case class SeedRequest(dir: SeedRequest.Dir, force: SeedRequest.Force = SeedRequest.Force(false)) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

object SeedRequest:
  type Dir = Dir.T
  object Dir extends RefinedType[String, Pure]
  type Force = Force.T
  object Force extends RefinedType[Boolean, Pure]
