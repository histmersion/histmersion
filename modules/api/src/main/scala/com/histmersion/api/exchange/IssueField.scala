package com.histmersion
package api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class IssueField(field: IssueField.Field, left: Option[IssueField.Value], right: Option[IssueField.Value])
    derives KindlingsCodecAsObject,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object IssueField:
  type Field = Field.T
  object Field extends Newtype[String, Pure]

  /** One field's value on one side of a merge issue. */
  type Value = Value.T
  object Value extends Newtype[String, Pure]
