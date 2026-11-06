package com.histmersion.api.checks
import com.histmersion.api.WireEnumValues
import com.histmersion.api.Plural
import com.histmersion.api.given

object FormatMarkdown:
  type IssueLine = IssueLine.T
  object IssueLine extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  def problems(n: com.histmersion.api.checks.FormatReport.IssueCount): String = Plural.count(n, _root_.com.histmersion.api.Plural.CountSingular("formatting problem"))

  def line(i: FormatIssue): com.histmersion.api.checks.FormatMarkdown.IssueLine =
    val sev = summon[WireEnumValues[FormatSeverity]].wire(i.severity)
    val fld = summon[WireEnumValues[FormatField]].wire(i.field)
    val ex = if i.excerpt.isEmpty then "" else s" · `${i.excerpt.replace("`", "'")}`"
    val fx = i.fix.fold("")(f => s" · fix: $f")
    _root_.com.histmersion.api.checks.FormatMarkdown.IssueLine(s"$fld L${i.line}:${i.column} $sev ${i.rule.wire} — ${i.message}$ex$fx")

  def notice(issues: List[FormatIssue]): String =
    if issues.isEmpty then ("")
    else
      val errs = issues.count(_.severity == FormatSeverity.Error)
      val head = s"## Formatting: ${problems(_root_.com.histmersion.api.checks.FormatReport.IssueCount(issues.size))}" +
        (if errs > 0 then s" ($errs will not display as intended)" else "")
      val tip = if issues.exists(_.fix.nonEmpty) then
        "\n\n_Repairs marked `fix:` are applied by `histmersion article lint <slug> --fix` / `lint --source <citekey> --fix`._"
      else ""
      (s"\n\n$head\n\n${issues.map(i => s"- ${line(i)}").mkString("\n")}$tip")

  def report(r: FormatReport): String =
    val fixed = if r.fixed.isEmpty then "" else r.fixed.map(f => s"- fixed: $f").mkString("", "\n", "\n\n")
    if r.issues.isEmpty then (s"$fixed### ${r.subject}: no formatting problems")
    else
      val head = s"### ${r.subject}: ${problems(_root_.com.histmersion.api.checks.FormatReport.IssueCount(r.issues.size))} (${r.errors} error(s), ${r.warnings} warning(s))"
      (s"$fixed$head\n\n${r.issues.map(i => s"- ${line(i)}").mkString("\n")}")
