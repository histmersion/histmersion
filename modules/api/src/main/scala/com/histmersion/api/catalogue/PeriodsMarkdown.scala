package com.histmersion
package api.catalogue

import com.histmersion.api.{Plural, YearFmt}
import com.histmersion.api.given
object PeriodsMarkdown:

  private def years(p: Period): String = YearFmt.period(Some(p.start), Some(p.end))

  def list(ps: Vector[HistoricalPeriod]): String =
    if ps.isEmpty then ("_No periods._")
    else
      val rows = ps.map(p =>
        s"| `${p.id}` | ${p.label} | ${years(p.period)} | ${p.region.getOrElse("")} | " +
          s"${p.parentId.fold("")(x => s"`$x`")} | ${p.preset.fold("")(_.label)} |"
      )
      ((List(
        s"### ${ps.size} period(s)",
        "",
        "| id | label | years | region | part of | timeline preset |",
        "|---|---|---|---|---|---|"
      ) ++ rows).mkString("\n"))

  def detail(d: Period.Detail): String =
    val p = d.period
    val head = List(
      Some(s"### ${p.label} `${p.id}`"),
      Some(""),
      Some(s"- **years:** ${years(p.period)} (signed: `${p.period.start}` to `${p.period.end}`, BCE negative)"),
      p.region.map(r => s"- **region:** $r"),
      p.authority.map(a => s"- **authority:** $a"),
      d.parent.map(x => s"- **part of:** ${x.label} `${x.id}` (${years(x.period)})"),
      p.preset.map(r => s"- **timeline preset:** under ${r.label}")
    ).flatten
    val kids =
      if d.children.isEmpty then Nil
      else
        "" :: "**Divided into:**" :: "" ::
          d.children.map(c => s"- ${c.label} `${c.id}` — ${years(c.period)}${c.region.fold("")(r => s" · $r")}")
    ((head ++ kids).mkString("\n"))

  def imported(r: Period.ImportResult): String =
    val head = s"imported ${Plural.count(Plural.CountN(r.periods.value.toLong), Plural.CountSingular("period"))}" +
      (if r.roots.value > 0 then
         s", ${r.roots} of them ${Plural.noun(Plural.CountN(r.roots.value.toLong), Plural.CountSingular("authority node"))}"
       else "") +
      s" — ${Plural.count(Plural.CountN(r.presets.value.toLong), Plural.CountSingular("timeline preset"))} in the store" +
      (if r.removed.value > 0 then
         s"; removed ${Plural.count(Plural.CountN(r.removed.value.toLong), Plural.CountSingular("earlier seed period"))} the seed no longer has"
       else "")
    if r.skippedCount == Period.ImportResult.SkippedCount(0) then head
    else
      val more = (r.skippedCount).value - r.skipped.size
      s"$head\n\nskipped ${Plural.count(Plural.CountN(r.skippedCount.value.toLong), Plural.CountSingular("definition"))} (not imported):\n" +
        skippedList(r.skipped) + (if more > 0 then s"\n- …and $more more" else "")

  def skippedList(ss: List[SkippedPeriod]): String =
    (ss.map(s => s"- `${s.id}` ${s.label}: ${s.detail}").mkString("\n"))
