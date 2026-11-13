package com.histmersion
package api.checks
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class FormatReport(
    subject: FormatReport.Subject,
    issues: List[FormatIssue],
    fixed: List[FormatReport.Fixed] = Nil,
    text: Option[FormatReport.Text] = None
) derives JsonCodec,
      KindlingsSchema,
      com.histmersion.api.json.Schema:
  def errors: FormatReport.ErrorCount = FormatReport.ErrorCount(issues.count(_.severity == FormatSeverity.Error))
  def warnings: FormatReport.WarningCount =
    FormatReport.WarningCount(issues.count(_.severity == FormatSeverity.Warning))

object FormatReport:
  type ErrorCount = ErrorCount.T
  object ErrorCount extends Newtype[Int, Pure]
  type IssueCount = IssueCount.T
  object IssueCount extends Newtype[Int, Pure]
  type WarningCount = WarningCount.T
  object WarningCount extends Newtype[Int, Pure]

  type Subject = Subject.T
  object Subject extends Newtype[String, Pure]
  type Fixed = Fixed.T
  object Fixed extends Newtype[String, Pure]
  type Text = Text.T
  object Text extends Newtype[String, Pure]
