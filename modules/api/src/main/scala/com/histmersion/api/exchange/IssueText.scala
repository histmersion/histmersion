package com.histmersion
package api.exchange
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class IssueText(
    base: Option[IssueText.Base],
    left: IssueText.Left,
    right: IssueText.Right,
    hunks: List[IssueHunk] = Nil
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object IssueText:
  type Base = Base.T
  object Base extends Newtype[String, Pure]
  type Left = Left.T
  object Left extends Newtype[String, Pure]
  type Right = Right.T
  object Right extends Newtype[String, Pure]
