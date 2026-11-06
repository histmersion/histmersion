package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class FollowupsRequest(
    id: FollowupsRequest.Id,
    since: Option[FollowupsRequest.Since] = None,
    limit: Option[FollowupsRequest.Limit] = None,
    apply: FollowupsRequest.Apply = _root_.com.histmersion.api.library.FollowupsRequest.Apply(false)
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object FollowupsRequest:
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Since = Since.T
  object Since extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Limit = Limit.T
  object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Apply = Apply.T
  object Apply extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
