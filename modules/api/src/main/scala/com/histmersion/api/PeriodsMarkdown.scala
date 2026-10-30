package com.histmersion.api

/** Rendering of the periods surface — its own object so this stream does not grow [[Markdown]]. */
object PeriodsMarkdown:

  private def years(p: Period): String = YearFmt.period(Some(p.start), Some(p.end))

  def list(ps: Vector[HistoricalPeriod]): String =
    if ps.isEmpty then "_No periods._"
    else
      val rows = ps.map(p =>
        s"| `${p.id}` | ${p.label} | ${years(p.period)} | ${p.region.getOrElse("")} | " +
          s"${p.parentId.fold("")(x => s"`$x`")} | ${p.preset.fold("")(_.label)} |"
      )
      (List(
        s"### ${ps.size} period(s)",
        "",
        "| id | label | years | region | part of | timeline preset |",
        "|---|---|---|---|---|---|"
      ) ++ rows).mkString("\n")

  def detail(d: PeriodDetail): String =
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
    (head ++ kids).mkString("\n")

  def imported(r: PeriodImportResult): String =
    val head = s"imported ${Plural.count(r.periods, "period")}" +
      (if r.roots > 0 then s", ${r.roots} of them ${Plural.noun(r.roots, "authority node")}" else "") +
      s" — ${Plural.count(r.presets, "timeline preset")} in the store" +
      (if r.removed > 0 then s"; removed ${Plural.count(r.removed, "earlier seed period")} the seed no longer has"
       else "")
    if r.skippedCount == 0 then head
    else
      val more = r.skippedCount - r.skipped.size
      s"$head\n\nskipped ${Plural.count(r.skippedCount, "definition")} (not imported):\n" +
        skippedList(r.skipped) + (if more > 0 then s"\n- …and $more more" else "")

  /** One line per skipped definition: id, label, why. */
  def skippedList(ss: List[SkippedPeriod]): String =
    ss.map(s => s"- `${s.id}` ${s.label}: ${s.detail}").mkString("\n")
