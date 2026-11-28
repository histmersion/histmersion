package com.histmersion
package api.library
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.automation.Creation
import com.histmersion.api.TwoStates

final case class FollowupsRequest(
    id: FollowupsRequest.Id,
    since: Option[FollowupsRequest.Since] = None,
    limit: Option[FollowupsRequest.Limit] = None,
    apply: FollowupsRequest.Apply = FollowupsRequest.Apply.DryRun,
    createdBy: Option[Creation.By] = None,
    via: Option[Creation.Via] = None
) extends Creation.Requested derives JsonCodec, KindlingsSchema, api.json.Schema

object FollowupsRequest:
  type Id = Id.T
  object Id extends Newtype[String, Pure]
  type Since = Since.T
  object Since extends Newtype[Int, Pure]
  type Limit = Limit.T
  object Limit extends Newtype[Int, Pure]
  enum Apply derives CanEqual:
    case Apply, DryRun
  object Apply extends TwoStates[Apply]:
    extension (apply: Apply) def applies: Boolean = apply == Apply
