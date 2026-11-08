package com.histmersion
package api.checks
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

final case class FormatIssue(
    rule: FormatRule,
    severity: FormatSeverity,
    field: FormatField,
    line: FormatIssue.Line,
    column: FormatIssue.Column,
    excerpt: FormatIssue.Excerpt,
    message: FormatIssue.Message,
    fix: Option[FormatIssue.Fix] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object FormatIssue:
  type Line = Line.T
  object Line extends RefinedType[Int, Pure]
  type Column = Column.T
  object Column extends RefinedType[Int, Pure]
  type Excerpt = Excerpt.T
  object Excerpt extends RefinedType[String, Pure]:
    type Length = Length.T
    object Length extends RefinedType[Int, Pure]
  type Message = Message.T
  object Message extends RefinedType[String, Pure]
  type Fix = Fix.T
  object Fix extends RefinedType[String, Pure]
