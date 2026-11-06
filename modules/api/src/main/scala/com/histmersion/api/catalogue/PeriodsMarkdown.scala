package com.histmersion.api.catalogue

import com.histmersion.api.{Plural, YearFmt}
import com.histmersion.api.given
object PeriodsMarkdown:

  type YearsResult = YearsResult.T
  object YearsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListResult = ListResult.T
  object ListResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DetailResult = DetailResult.T
  object DetailResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImportedResult = ImportedResult.T
  object ImportedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SkippedListResult = SkippedListResult.T
  object SkippedListResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private def years(p: Period): com.histmersion.api.catalogue.PeriodsMarkdown.YearsResult = YearFmt.period(Some(p.start), Some(p.end))

  def list(ps: Vector[HistoricalPeriod]): com.histmersion.api.catalogue.PeriodsMarkdown.ListResult =
    if ps.isEmpty then _root_.com.histmersion.api.catalogue.PeriodsMarkdown.ListResult("_No periods._")
    else
      val rows = ps.map(p =>
        s"| `${p.id}` | ${p.label} | ${years(p.period)} | ${p.region.getOrElse("")} | " +
          s"${p.parentId.fold("")(x => s"`$x`")} | ${p.preset.fold("")(_.label)} |"
      )
      _root_.com.histmersion.api.catalogue.PeriodsMarkdown.ListResult((List(
        s"### ${ps.size} period(s)",
        "",
        "| id | label | years | region | part of | timeline preset |",
        "|---|---|---|---|---|---|"
      ) ++ rows).mkString("\n"))

  def detail(d: Period.Detail): com.histmersion.api.catalogue.PeriodsMarkdown.DetailResult =
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
    _root_.com.histmersion.api.catalogue.PeriodsMarkdown.DetailResult((head ++ kids).mkString("\n"))

  def imported(r: Period.ImportResult): com.histmersion.api.catalogue.PeriodsMarkdown.ImportedResult =
    val head = s"imported ${Plural.count(r.periods, _root_.com.histmersion.api.Plural.CountSingular("period"))}" +
      (if r.roots > 0 then s", ${r.roots} of them ${Plural.noun(r.roots, "authority node")}" else "") +
      s" — ${Plural.count(r.presets, "timeline preset")} in the store" +
      (if r.removed > 0 then s"; removed ${Plural.count(r.removed, "earlier seed period")} the seed no longer has"
       else "")
    if r.skippedCount == 0 then head
    else
      val more = r.skippedCount - r.skipped.size
      s"$head\n\nskipped ${Plural.count(r.skippedCount, _root_.com.histmersion.api.Plural.CountSingular("definition"))} (not imported):\n" +
        skippedList(r.skipped) + (if more > 0 then s"\n- …and $more more" else "")

  def skippedList(ss: List[SkippedPeriod]): com.histmersion.api.catalogue.PeriodsMarkdown.SkippedListResult =
    _root_.com.histmersion.api.catalogue.PeriodsMarkdown.SkippedListResult(ss.map(s => s"- `${s.id}` ${s.label}: ${s.detail}").mkString("\n"))
