package com.histmersion.api.library

import com.histmersion.api.Plural
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.given
object AcquireChecksMarkdown:

  import com.histmersion.api.library.Acquire.Checks.*

  def line(s: RecheckStatus): String =
    val job = s.job.fold("")(j => s" — $j")
    if !((s.enabled) == _root_.com.histmersion.api.library.Acquire.Checks.RecheckStatus.Enabled(true)) then (s"acquire re-check: off (acquire.recheck.enabled = false)$job")
    else
      s"acquire re-check: ${s.due} of ${Plural.count(s.withDoi, _root_.com.histmersion.api.Plural.CountSingular("item"))} with a DOI due, " +
        s"${s.closedWeek} closed and ${s.fetchedWeek} found this week, ${s.triedToday} of ${s.perDay} tried today" +
        s.nextPass.fold("")(n => s" — next pass $n") + s.note.fold("")(n => s" ($n)") + job

  def result(r: RecheckResult): com.histmersion.api.Markdown.Text = r.job.fold(pass(r))(j => started(r, j))

  private def started(r: RecheckResult, j: JobInfo): com.histmersion.api.Markdown.Text =
    s"# Acquisition list re-check\n\n" +
      s"- ${Plural.count(r.open, _root_.com.histmersion.api.Plural.CountSingular("open item"))}, ${r.withDoi} with a DOI; ${r.allowance} to try\n" +
      s"- started as job `${j.id}`: ${j.detail.flatMap(RecheckJob.Detail.of).fold("the acquisition list's legal re-check")(RecheckJob.describe)} " +
      s"— `histmersion jobs ${j.id}` follows it" + r.note.fold("")(n => s"\n- _${n}_")

  private def pass(r: RecheckResult): com.histmersion.api.Markdown.Text =
    val verb = if ((r.dryRun) == _root_.com.histmersion.api.library.Acquire.Checks.RecheckResult.DryRun(true)) then "would " else ""
    val head =
      s"# Acquisition list re-check${if ((r.dryRun) == _root_.com.histmersion.api.library.Acquire.Checks.RecheckResult.DryRun(true)) then " (dry run: no fetcher asked, nothing stored)" else ""}\n\n" +
        s"- ${Plural.count(r.open, _root_.com.histmersion.api.Plural.CountSingular("open item"))}, ${r.withDoi} with a DOI; ${r.due} due for a try, ${r.allowance} this pass may try\n" +
        (if r.dryRun then
           s"- ${verb}close ${Plural.count(r.rows.count(_.action == WouldClose), "item")} the corpus holds with its text\n" +
             s"- ${verb}try ${Plural.count(r.rows.count(_.action == WouldTry), "item")} through the legal chain"
         else
           s"- closed ${Plural.count(r.closed, "item")} the corpus holds with its text\n" +
             s"- a legal copy found for ${r.fetched} (converting: the conversion closes the item), " +
             s"no legal copy for ${r.noCopy}, no answer for ${r.failed}") +
        r.note.fold("")(n => s"\n- _${n}_")
    val rows = r.rows.map { row =>
      s"- #${row.item}${row.doi.fold("")(d => s" https://doi.org/$d")} · ${row.action}" +
        row.citekey.fold("")(ck => s" [[$ck]]") + (if row.detail.nonEmpty then s": ${row.detail}" else "")
    }
    if rows.isEmpty then head + "\n\n_Nothing to do._" else head + "\n\n## Items\n\n" + rows.mkString("\n")
