package com.histmersion
package api.library
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.automation.Creation
import com.histmersion.api.json.TwoStates

final case class SeedRequest(
    dir: SeedRequest.Dir,
    force: SeedRequest.Force = SeedRequest.Force.Safe,
    createdBy: Option[Creation.By] = None,
    via: Option[Creation.Via] = None
) extends Creation.Requested derives JsonCodec, KindlingsSchema, api.json.Schema

object SeedRequest:
  type Dir = Dir.T
  object Dir extends Newtype[String, Pure]
  enum Force derives CanEqual:
    case Force, Safe
  object Force extends TwoStates[Force]:
    extension (force: Force) def force: Boolean = force == Force
