package com.histmersion.api.automation
import com.histmersion.api.{OffsetPage, YearFmt}
import com.histmersion.api.Markdown.*

object GoalsMarkdown:


  private[api] def goalLine(g: Goal): String =
    val period = YearFmt.openPeriod(g.periodStart, g.periodEnd, ("")) match
      case "" => ""
      case p  => s" ($p)"
    val parent = g.parentSlug.map(p => s" ⊂ $p").getOrElse("")
    (s"- **${g.title}** `${g.slug}` _(${g.status})_$period$parent")

  def goalList(result: Goal.List): String =
    OffsetPage.markdown(result.page, ("goal(s)"), ("_No matching goals._"), result.goals)(goalLine).value

  def goal(g: Goal): String =
    val header = s"# ${g.title}\n\n_`${g.slug}` · ${g.status}_"
    val period = YearFmt.openPeriod(g.periodStart, g.periodEnd, ("")) match
      case "" => ""
      case p  => s"  \n**Period:** $p"
    val parent = g.parentSlug.map(p => s"  \n**Parent:** `$p`").getOrElse("")
    val tags = if g.tags.isEmpty then "" else s"  \n**Tags:** ${g.tags.mkString(", ")}"
    val regions = if g.regions.isEmpty then "" else s"  \n**Regions:** ${g.regions.mkString(", ")}"
    val notes = g.notes.map(n => s"\n\n$n").getOrElse("")
    val upd = g.updatedAt.map(u => s"  \n_Updated: ${u}_").getOrElse("")
    (header + period + parent + tags + regions + notes + upd)
