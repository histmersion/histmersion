package com.histmersion.api.automation
import com.histmersion.api.automation.Activity.{Entry, EntryKind, Page}
import com.histmersion.api.Wording
import com.histmersion.api.jobs.{JobKinds, JobState}
import com.histmersion.api.given

object ActivityMarkdown:

  export com.histmersion.api.automation.Activity.Entry.{At as MinuteAt}

  def minute(at: com.histmersion.api.automation.ActivityMarkdown.MinuteAt): String = ((at).value.take(16).replace('T', ' '))

  def headline(e: Entry): String = e.entryKind match
    case Some(EntryKind.Run) =>
      val what = Agents.Pipeline.parse(Pipeline.Wire(e.what.value)).fold(Wording.humanize(Wording.Id(e.what.value)))(Wording.pipeline)
      what + e.target.map(_.value).filter(_.nonEmpty).fold("")(t => s" — $t")
    case Some(EntryKind.Job) =>
      Wording.jobKindOf(JobKinds.Wire(e.what.value)) + e.citekey.map(_.value).orElse(e.target.map(_.value)).filter(_.nonEmpty).fold("")(t => s" — $t")
    case Some(EntryKind.Decision) =>
      val outcome = Agents.ApprovalState.parse(Approval.State.Wire(e.state.value)).fold(e.state.value)(Wording.approvalState).toLowerCase
      s"${Agents.ApprovalKind.parse(Approval.Kind.Wire(e.what.value)).fold(Wording.humanize(Wording.Id(e.what.value)))(Wording.approvalKind)} ($outcome)" +
        Option(e.text.value).filter(_.nonEmpty).fold("")(t => s" — $t")
    case Some(EntryKind.Log) | None => e.text.value

  def stateOf(e: Entry): String = e.entryKind match
    case Some(EntryKind.Run) => Agents.RunState.parse(Run.State.Wire(e.state.value)).fold(e.state.value)(Wording.runState)
    case Some(EntryKind.Job) => JobState.parse(JobState.Wire(e.state.value)).fold(e.state.value)(Wording.jobState)
    case _                   => ("")

  def refs(e: Entry): List[String] =
    e.goal.map(g => s"goal $g").toList ++
      e.job
        .filter(_ => !e.entryKind.exists(k => k == EntryKind.Run || k == EntryKind.Job))
        .map(j => s"run/job ${j.value.take(8)}") ++
      e.citekey.map(c => s"[[$c]]") ++ e.article.map(a => s"article $a") ++
      e.queueItem
        .filter(_ => !e.entryKind.contains(EntryKind.Log))
        .map(i => s"${e.queueList.getOrElse("queue")} item #$i") ++
      e.approval.map(a => s"approval #$a")

  def line(e: Entry): String =
    val kind = e.entryKind.fold(e.kind.value)(_.wording)
    val state = stateOf(e)
    val id = e.entryKind
      .filter(k => k == EntryKind.Run || k == EntryKind.Job)
      .flatMap(_ => e.job)
      .fold("")(j => s" `${j.value.take(8)}`")
    val refsText = refs(e)
    s"- ${minute(e.at)} · **$kind**$id · ${(headline(e)).replace('\n', ' ').take(200)}" +
      (if (state).nonEmpty then s" · $state" else "") +
      e.error.map(_.value).filter(_.nonEmpty).fold("")(err => s" — ${err.take(160)}") +
      (if refsText.nonEmpty then s" _(${refsText.mkString(", ")})_" else "")

  def page(p: Page, olderHint: com.histmersion.api.Paging.Cursor => String): String =
    val head = s"**Activity** — ${p.counts.running} running now · ${p.counts.problemsWeek} problem(s) this week"
    val body = if p.entries.isEmpty then "_Nothing here._" else p.entries.map(line).mkString("\n")
    (List(head, "", body) ++ p.next.map(c => s"\n${olderHint(c)}").toList).mkString("\n")
