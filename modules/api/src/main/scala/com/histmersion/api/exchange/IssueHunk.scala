package com.histmersion
package api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

final case class IssueHunk(
    conflict: IssueHunk.Conflict,
    base: List[IssueHunk.Base] = Nil,
    left: List[IssueHunk.Left] = Nil,
    right: List[IssueHunk.Right] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object IssueHunk:
  type Conflict = Conflict.T
  object Conflict extends RefinedType[Boolean, Pure]
  type Base = Base.T
  object Base extends RefinedType[String, Pure]
  type Left = Left.T
  object Left extends RefinedType[String, Pure]
  type Right = Right.T
  object Right extends RefinedType[String, Pure]
