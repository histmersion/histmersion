package com.histmersion
package api.checks
import com.histmersion.api.WireEnumValues
import com.histmersion.api.Plural
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

object FormatMarkdown:
  type IssueLine = IssueLine.T
  object IssueLine extends Newtype[String, Pure]

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def problems(n: FormatReport.IssueCount): String =
    Plural.count(Plural.CountN(n.value.toLong), Plural.CountSingular("formatting problem")).value

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def line(i: FormatIssue): IssueLine =
    val sev = summon[WireEnumValues[FormatSeverity]].wire(i.severity)
    val fld = summon[WireEnumValues[FormatField]].wire(i.field)
    val ex = if i.excerpt.value.isEmpty then "" else s" · `${i.excerpt.value.replace("`", "'")}`"
    val fx = i.fix.fold("")(f => s" · fix: $f")
    IssueLine(s"$fld L${i.line}:${i.column} $sev ${i.rule.wire} — ${i.message}$ex$fx")

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def notice(issues: List[FormatIssue]): String =
    if issues.isEmpty then ("")
    else
      val errs = issues.count(_.severity == FormatSeverity.Error)
      val head = s"## Formatting: ${problems(FormatReport.IssueCount(issues.size))}" +
        (if errs > 0 then s" ($errs will not display as intended)" else "")
      val tip = if issues.exists(_.fix.nonEmpty) then
        "\n\n_Repairs marked `fix:` are applied by `histmersion article lint <slug> --fix` / `lint --source <citekey> --fix`._"
      else ""
      (s"\n\n$head\n\n${issues.map(i => s"- ${line(i)}").mkString("\n")}$tip")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def report(r: FormatReport): String =
    val fixed = if r.fixed.isEmpty then "" else r.fixed.map(f => s"- fixed: $f").mkString("", "\n", "\n\n")
    if r.issues.isEmpty then (s"$fixed### ${r.subject}: no formatting problems")
    else
      val head =
        s"### ${r.subject}: ${problems(FormatReport.IssueCount(r.issues.size))} (${r.errors} error(s), ${r.warnings} warning(s))"
      (s"$fixed$head\n\n${r.issues.map(i => s"- ${line(i)}").mkString("\n")}")
