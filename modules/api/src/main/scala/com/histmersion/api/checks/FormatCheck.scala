package com.histmersion.api.checks
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class FormatCheck(text: FormatCheck.Text, field: FormatField = FormatField.Body, fix: FormatCheck.Fix = _root_.com.histmersion.api.checks.FormatCheck.Fix(false))
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object FormatCheck:
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Fix = Fix.T
  object Fix extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
