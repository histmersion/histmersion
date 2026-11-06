package com.histmersion.api.automation
import com.histmersion.api.automation.Activity.{Entry, EntryKind, Page}
import com.histmersion.api.Wording
import com.histmersion.api.jobs.JobState
import com.histmersion.api.given

object ActivityMarkdown:

  type MinuteResult = MinuteResult.T
  object MinuteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MinuteAt = MinuteAt.T
  object MinuteAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeadlineResult = HeadlineResult.T
  object HeadlineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StateOfResult = StateOfResult.T
  object StateOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefsResult = RefsResult.T
  object RefsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LineResult = LineResult.T
  object LineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageResult = PageResult.T
  object PageResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageOlderHintArg1 = PageOlderHintArg1.T
  object PageOlderHintArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageOlderHintResult = PageOlderHintResult.T
  object PageOlderHintResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def minute(at: com.histmersion.api.automation.ActivityMarkdown.MinuteAt): com.histmersion.api.automation.ActivityMarkdown.MinuteResult = at.take(16).replace('T', ' ')

  def headline(e: Entry): com.histmersion.api.automation.ActivityMarkdown.HeadlineResult = e.entryKind match
    case Some(EntryKind.Run) =>
      val what = Agents.Pipeline.parse(e.what).fold(Wording.humanize(e.what))(Wording.pipeline)
      what + e.target.filter(_.nonEmpty).fold("")(t => s" — $t")
    case Some(EntryKind.Job) =>
      Wording.jobKindOf(e.what) + e.citekey.orElse(e.target).filter(_.nonEmpty).fold("")(t => s" — $t")
    case Some(EntryKind.Decision) =>
      val outcome = Agents.ApprovalState.parse(e.state).fold(e.state)(Wording.approvalState).toLowerCase
      s"${Agents.ApprovalKind.parse(e.what).fold(Wording.humanize(e.what))(Wording.approvalKind)} ($outcome)" +
        Option(e.text).filter(_.nonEmpty).fold("")(t => s" — $t")
    case Some(EntryKind.Log) | None => e.text

  def stateOf(e: Entry): com.histmersion.api.automation.ActivityMarkdown.StateOfResult = e.entryKind match
    case Some(EntryKind.Run) => Agents.RunState.parse(e.state).fold(e.state)(Wording.runState)
    case Some(EntryKind.Job) => JobState.parse(e.state).fold(e.state)(Wording.jobState)
    case _                   => _root_.com.histmersion.api.automation.ActivityMarkdown.StateOfResult("")

  def refs(e: Entry): List[com.histmersion.api.automation.ActivityMarkdown.RefsResult] =
    e.goal.map(g => s"goal $g").toList ++
      e.job
        .filter(_ => !e.entryKind.exists(k => k == EntryKind.Run || k == EntryKind.Job))
        .map(j => s"run/job ${j.take(8)}") ++
      e.citekey.map(c => s"[[$c]]") ++ e.article.map(a => s"article $a") ++
      e.queueItem
        .filter(_ => !e.entryKind.contains(EntryKind.Log))
        .map(i => s"${e.queueList.getOrElse("queue")} item #$i") ++
      e.approval.map(a => s"approval #$a")

  def line(e: Entry): com.histmersion.api.automation.ActivityMarkdown.LineResult =
    val kind = e.entryKind.fold(e.kind)(_.wording)
    val state = stateOf(e)
    val id = e.entryKind
      .filter(k => k == EntryKind.Run || k == EntryKind.Job)
      .flatMap(_ => e.job)
      .fold("")(j => s" `${j.take(8)}`")
    val refsText = refs(e)
    s"- ${minute(e.at)} · **$kind**$id · ${headline(e).replace('\n', ' ').take(200)}" +
      (if state.nonEmpty then s" · $state" else "") +
      e.error.filter(_.nonEmpty).fold("")(err => s" — ${err.take(160)}") +
      (if refsText.nonEmpty then s" _(${refsText.mkString(", ")})_" else "")

  def page(p: Page, olderHint: com.histmersion.api.automation.ActivityMarkdown.PageOlderHintArg1 => com.histmersion.api.automation.ActivityMarkdown.PageOlderHintResult): com.histmersion.api.automation.ActivityMarkdown.PageResult =
    val head = s"**Activity** — ${p.counts.running} running now · ${p.counts.problemsWeek} problem(s) this week"
    val body = if p.entries.isEmpty then "_Nothing here._" else p.entries.map(line).mkString("\n")
    (List(head, "", body) ++ p.next.map(c => s"\n${olderHint(c)}").toList).mkString("\n")
