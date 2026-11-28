package com.histmersion
package api.exchange
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.TwoStates

final case class IssueHunk(
    conflict: IssueHunk.Conflict,
    base: List[IssueHunk.Base] = Nil,
    left: List[IssueHunk.Left] = Nil,
    right: List[IssueHunk.Right] = Nil
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object IssueHunk:
  enum Conflict derives CanEqual:
    case Conflict, Clean
  object Conflict extends TwoStates[Conflict]:
    extension (conflict: Conflict) def conflict: Boolean = conflict == Conflict
  type Base = Base.T
  object Base extends Newtype[String, Pure]
  type Left = Left.T
  object Left extends Newtype[String, Pure]
  type Right = Right.T
  object Right extends Newtype[String, Pure]
