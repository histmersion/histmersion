package com.histmersion
package api.checks
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class FormatCheck(text: FormatCheck.Text, field: FormatField = FormatField.Body, fix: FormatCheck.Fix = FormatCheck.Fix(false))
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object FormatCheck:
  type Text = Text.T
  object Text extends Newtype[String, Pure]
  type Fix = Fix.T
  object Fix extends Newtype[Boolean, Pure]
