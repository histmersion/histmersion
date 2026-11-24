package com.histmersion
package api.library
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.automation.Creation

final case class SeedRequest(
    dir: SeedRequest.Dir,
    force: SeedRequest.Force = SeedRequest.Force(false),
    createdBy: Option[Creation.By] = None,
    via: Option[Creation.Via] = None
) extends Creation.Requested derives JsonCodec, KindlingsSchema, api.json.Schema

object SeedRequest:
  type Dir = Dir.T
  object Dir extends Newtype[String, Pure]
  type Force = Force.T
  object Force extends Newtype[Boolean, Pure]
