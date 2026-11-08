package com.histmersion
package api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class FollowupsRequest(
    id: FollowupsRequest.Id,
    since: Option[FollowupsRequest.Since] = None,
    limit: Option[FollowupsRequest.Limit] = None,
    apply: FollowupsRequest.Apply = FollowupsRequest.Apply(false)
) derives KindlingsCodecAsObject,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object FollowupsRequest:
  type Id = Id.T
  object Id extends Newtype[String, Pure]
  type Since = Since.T
  object Since extends Newtype[Int, Pure]
  type Limit = Limit.T
  object Limit extends Newtype[Int, Pure]
  type Apply = Apply.T
  object Apply extends Newtype[Boolean, Pure]
