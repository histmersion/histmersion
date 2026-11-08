package com.histmersion
package api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class IssueHunk(
    conflict: IssueHunk.Conflict,
    base: List[IssueHunk.Base] = Nil,
    left: List[IssueHunk.Left] = Nil,
    right: List[IssueHunk.Right] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object IssueHunk:
  type Conflict = Conflict.T
  object Conflict extends Newtype[Boolean, Pure]
  type Base = Base.T
  object Base extends Newtype[String, Pure]
  type Left = Left.T
  object Left extends Newtype[String, Pure]
  type Right = Right.T
  object Right extends Newtype[String, Pure]
