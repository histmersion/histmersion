package com.histmersion.api.automation
import com.histmersion.api.{OffsetPage, YearFmt}
import com.histmersion.api.Markdown.*

object GoalsMarkdown:

  type GoalLineResult = GoalLineResult.T
  object GoalLineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalListResult = GoalListResult.T
  object GoalListResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalResult = GoalResult.T
  object GoalResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private[api] def goalLine(g: Goal): com.histmersion.api.automation.GoalsMarkdown.GoalLineResult =
    val period = YearFmt.openPeriod(g.periodStart, g.periodEnd, _root_.com.histmersion.api.YearFmt.OpenPeriodNone("")) match
      case "" => ""
      case p  => s" ($p)"
    val parent = g.parentSlug.map(p => s" ⊂ $p").getOrElse("")
    _root_.com.histmersion.api.automation.GoalsMarkdown.GoalLineResult(s"- **${g.title}** `${g.slug}` _(${g.status})_$period$parent")

  def goalList(result: Goal.List): com.histmersion.api.automation.GoalsMarkdown.GoalListResult =
    OffsetPage.markdown(result.page, _root_.com.histmersion.api.OffsetPage.MarkdownNoun("goal(s)"), _root_.com.histmersion.api.OffsetPage.MarkdownEmpty("_No matching goals._"), result.goals)(goalLine)

  def goal(g: Goal): com.histmersion.api.automation.GoalsMarkdown.GoalResult =
    val header = s"# ${g.title}\n\n_`${g.slug}` · ${g.status}_"
    val period = YearFmt.openPeriod(g.periodStart, g.periodEnd, _root_.com.histmersion.api.YearFmt.OpenPeriodNone("")) match
      case "" => ""
      case p  => s"  \n**Period:** $p"
    val parent = g.parentSlug.map(p => s"  \n**Parent:** `$p`").getOrElse("")
    val tags = if g.tags.isEmpty then "" else s"  \n**Tags:** ${g.tags.mkString(", ")}"
    val regions = if g.regions.isEmpty then "" else s"  \n**Regions:** ${g.regions.mkString(", ")}"
    val notes = g.notes.map(n => s"\n\n$n").getOrElse("")
    val upd = g.updatedAt.map(u => s"  \n_Updated: ${u}_").getOrElse("")
    _root_.com.histmersion.api.automation.GoalsMarkdown.GoalResult(header + period + parent + tags + regions + notes + upd)
