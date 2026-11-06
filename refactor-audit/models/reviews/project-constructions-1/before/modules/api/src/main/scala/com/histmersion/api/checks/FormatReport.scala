package com.histmersion.api.checks
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class FormatReport(
    subject: FormatReport.Subject,
    issues: List[FormatIssue],
    fixed: List[FormatReport.Fixed] = Nil,
    text: Option[FormatReport.Text] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema:
  def errors: com.histmersion.api.checks.FormatReport.ErrorsResult = issues.count(_.severity == FormatSeverity.Error)
  def warnings: com.histmersion.api.checks.FormatReport.WarningsResult = issues.count(_.severity == FormatSeverity.Warning)

object FormatReport:
  type ErrorsResult = ErrorsResult.T
  object ErrorsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WarningsResult = WarningsResult.T
  object WarningsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Subject = Subject.T
  object Subject extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Fixed = Fixed.T
  object Fixed extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
