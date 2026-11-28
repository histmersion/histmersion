package com.histmersion
package api.checks.lint

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.checks.Lint.IssueCount
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.checks.Lint.Markdown

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class LintReport(rules: List[LintRuleReport], fixed: List[LintReport.Fixed] = Nil)
    derives JsonCodec,
      KindlingsSchema,
      api.json.Schema:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def total: IssueCount = IssueCount(rules.map(r => (r.count).value).sum)

object LintReport:

  type Fixed = Fixed.T
  object Fixed extends Newtype[String, Pure]

  extension (rep: LintReport)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def markdown: Markdown =
      val fixed = if rep.fixed.isEmpty then "" else rep.fixed.map(f => s"- fixed: $f").mkString("", "\n", "\n\n")
      val (clean, dirty) = rep.rules.partition(_.count == LintRuleReport.Count(0))
      val head =
        if dirty.isEmpty then s"### Lint: clean (${rep.rules.size} rule(s) checked)"
        else s"### Lint: ${rep.total} finding(s) across ${dirty.size} of ${rep.rules.size} rule(s)"
      val blocks = dirty.map { r =>
        val items = r.items.map(i => s"- **${i.citekey}** — ${i.detail}")
        val more = if (r.count).value > r.items.size then s"\n- _… and ${(r.count).value - r.items.size} more_" else ""
        s"\n\n#### ${r.rule} (${r.count})\n_${r.description}_\n\n${items.mkString("\n")}$more"
      }
      val cleanLine =
        if clean.isEmpty then ""
        else s"\n\n_clean: ${clean.map(_.rule).mkString(", ")}_"
      Markdown(s"$fixed$head${blocks.mkString}$cleanLine")
