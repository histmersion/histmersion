package com.histmersion.api.checks
import com.histmersion.api.WireEnumValues
import com.histmersion.api.Plural
import com.histmersion.api.given

object FormatMarkdown:

  type ProblemsResult = ProblemsResult.T
  object ProblemsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProblemsN = ProblemsN.T
  object ProblemsN extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LineResult = LineResult.T
  object LineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NoticeResult = NoticeResult.T
  object NoticeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReportResult = ReportResult.T
  object ReportResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def problems(n: com.histmersion.api.checks.FormatMarkdown.ProblemsN): com.histmersion.api.checks.FormatMarkdown.ProblemsResult = Plural.count(n, _root_.com.histmersion.api.Plural.CountSingular("formatting problem"))

  def line(i: FormatIssue): com.histmersion.api.checks.FormatMarkdown.LineResult =
    val sev = summon[WireEnumValues[FormatSeverity]].wire(i.severity)
    val fld = summon[WireEnumValues[FormatField]].wire(i.field)
    val ex = if i.excerpt.isEmpty then "" else s" · `${i.excerpt.replace("`", "'")}`"
    val fx = i.fix.fold("")(f => s" · fix: $f")
    _root_.com.histmersion.api.checks.FormatMarkdown.LineResult(s"$fld L${i.line}:${i.column} $sev ${i.rule.wire} — ${i.message}$ex$fx")

  def notice(issues: List[FormatIssue]): com.histmersion.api.checks.FormatMarkdown.NoticeResult =
    if issues.isEmpty then _root_.com.histmersion.api.checks.FormatMarkdown.NoticeResult("")
    else
      val errs = issues.count(_.severity == FormatSeverity.Error)
      val head = s"## Formatting: ${problems(_root_.com.histmersion.api.checks.FormatMarkdown.ProblemsN(issues.size))}" +
        (if errs > 0 then s" ($errs will not display as intended)" else "")
      val tip = if issues.exists(_.fix.nonEmpty) then
        "\n\n_Repairs marked `fix:` are applied by `histmersion article lint <slug> --fix` / `lint --source <citekey> --fix`._"
      else ""
      _root_.com.histmersion.api.checks.FormatMarkdown.NoticeResult(s"\n\n$head\n\n${issues.map(i => s"- ${line(i)}").mkString("\n")}$tip")

  def report(r: FormatReport): com.histmersion.api.checks.FormatMarkdown.ReportResult =
    val fixed = if r.fixed.isEmpty then "" else r.fixed.map(f => s"- fixed: $f").mkString("", "\n", "\n\n")
    if r.issues.isEmpty then _root_.com.histmersion.api.checks.FormatMarkdown.ReportResult(s"$fixed### ${r.subject}: no formatting problems")
    else
      val head = s"### ${r.subject}: ${problems(_root_.com.histmersion.api.checks.FormatMarkdown.ProblemsN(r.issues.size))} (${r.errors} error(s), ${r.warnings} warning(s))"
      _root_.com.histmersion.api.checks.FormatMarkdown.ReportResult(s"$fixed$head\n\n${r.issues.map(i => s"- ${line(i)}").mkString("\n")}")
