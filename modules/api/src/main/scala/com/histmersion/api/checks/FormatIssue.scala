package com.histmersion
package api.checks
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

final case class FormatIssue(
    rule: FormatRule,
    severity: FormatSeverity,
    field: FormatField,
    line: FormatIssue.Line,
    column: FormatIssue.Column,
    excerpt: FormatIssue.Excerpt,
    message: FormatIssue.Message,
    fix: Option[FormatIssue.Fix] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object FormatIssue:
  type Line = Line.T
  object Line extends Newtype[Int, Pure]
  type Column = Column.T
  object Column extends Newtype[Int, Pure]
  type Excerpt = Excerpt.T
  object Excerpt extends Newtype[String, Pure]:
    type Length = Length.T
    object Length extends Newtype[Int, Pure]
  type Message = Message.T
  @tolerated(LintRule.G5Problem, "pending G60 (one typed Problem ADT)")
  object Message extends Newtype[String, Pure]
  type Fix = Fix.T
  object Fix extends Newtype[String, Pure]
