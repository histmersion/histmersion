package com.histmersion
package api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class IssueField(field: IssueField.Field, left: Option[IssueField.Left], right: Option[IssueField.Right])
    derives KindlingsCodecAsObject,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object IssueField:
  type Field = Field.T
  object Field extends Newtype[String, Pure]
  type Left = Left.T
  object Left extends Newtype[String, Pure]
  type Right = Right.T
  object Right extends Newtype[String, Pure]
