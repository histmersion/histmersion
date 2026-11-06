package com.histmersion.domain

import com.histmersion.api.Plural
import com.histmersion.api.research.{ReviewApproval, ReviewBadge, ReviewFinding, ReviewRound, ReviewState}
import com.histmersion.domain.ReviewLedger.{Entry, Ledger}
import com.histmersion.domain.automation.ItemStatus

/** Where an article stands with the review panel, derived from what the review manager and the review ledger record
  * about it — pure; the server reads the facts (one cheap query for a whole list page, the ledger itself for one
  * article's history).
  */
object ArticleReviewStatus:
  type EntryKey = EntryKey.T
  object EntryKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OutcomeSuffix = OutcomeSuffix.T
  object OutcomeSuffix extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Progress = Progress.T
  object Progress extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunKey = RunKey.T
  object RunKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunOrder = RunOrder.T
  object RunOrder extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunStart = RunStart.T
  object RunStart extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatusLine = StatusLine.T
  object StatusLine extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The review manager's `article-review` item for the article: its status, its last outcome line and how many runs it
    * started.
    */
  type HistoryRunStartsPart1 = _root_.com.histmersion.domain.ArticleReviewStatus.RunKey
  val HistoryRunStartsPart1: _root_.com.histmersion.domain.ArticleReviewStatus.RunKey.type = _root_.com.histmersion.domain.ArticleReviewStatus.RunKey
  type HistoryRunStartsPart2 = _root_.com.histmersion.domain.ArticleReviewStatus.RunStart
  val HistoryRunStartsPart2: _root_.com.histmersion.domain.ArticleReviewStatus.RunStart.type = _root_.com.histmersion.domain.ArticleReviewStatus.RunStart

  final case class ItemFacts(status: ItemStatus, outcome: Option[ItemFacts.Outcome] = None, attempts: ItemFacts.Attempts = com.histmersion.domain.ArticleReviewStatus.ItemFacts.Attempts(0))

  object ItemFacts:
    type Outcome = Outcome.T
    object Outcome extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Attempts = Attempts.T
    object Attempts extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The ledger's findings counted: the open MAJOR and minor ones, all of them, and the runs that raised or closed one.
    */
  final case class LedgerFacts(openMajors: LedgerFacts.OpenMajors, openMinors: LedgerFacts.OpenMinors, findings: LedgerFacts.Findings, runs: LedgerFacts.Runs)

  object LedgerFacts:
    type OpenMajors = OpenMajors.T
    object OpenMajors extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenMinors = OpenMinors.T
    object OpenMinors extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Findings = Findings.T
    object Findings extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Runs = Runs.T
    object Runs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def of(l: Ledger): LedgerFacts =
      LedgerFacts(l.openMajors.size, l.open.count(!_.major), l.entries.size, runOrder(l, Map.empty).size)

  /** `round`: the pass the item's run is in now (its latest `snapshot-<n>` step), while it runs; `waiting`: the
    * approvals about the article that wait for a person.
    */
  final case class Facts(
      item: Option[ItemFacts] = None,
      ledger: Option[LedgerFacts] = None,
      round: Option[Facts.Round] = None,
      waiting: List[ReviewApproval] = Nil
  )

  object Facts:
    type Round = Round.T
    object Round extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The state, from the item first (a run going on, queued, stopped), then the ledger's open MAJOR findings. A
    * cancelled item counts as no item: what the ledger says stands.
    */
  def badge(f: Facts): ReviewBadge =
    val item = f.item.filter(_.status != ItemStatus.Cancelled)
    val counts = f.ledger.getOrElse(LedgerFacts(com.histmersion.domain.ArticleReviewStatus.LedgerFacts.OpenMajors(0), com.histmersion.domain.ArticleReviewStatus.LedgerFacts.OpenMinors(0), com.histmersion.domain.ArticleReviewStatus.LedgerFacts.Findings(0), com.histmersion.domain.ArticleReviewStatus.LedgerFacts.Runs(0)))
    val runNo = item.map(_.attempts).filter(_ > 0)
    val (state, label, detail) = item.map(_.status) match
      case Some(ItemStatus.Active) =>
        (
          ReviewState.InReview,
          f.round.fold("in review")(r => s"in review · round $r"),
          s"In review: the review panel is working on it now${f.round.fold("")(r => s" (round $r of this run)")}.${soFar(counts)}"
        )
      case Some(ItemStatus.Pending | ItemStatus.Held) =>
        val again = item
          .flatMap(i => Option.when(i.attempts > 0)(i))
          .fold(" The review manager starts the panel when a run slot is free.")(i =>
            s" Run ${i.attempts} ended without converging${outcomeSuffix(i.outcome)}"
          )
        (ReviewState.Queued, "queued", s"Queued for review.$again${soFar(counts)}")
      case Some(ItemStatus.Stopped | ItemStatus.Failed) =>
        val how = if item.exists(_.status == ItemStatus.Failed) then "failed" else "stopped"
        (
          ReviewState.Stopped,
          s"review $how",
          s"Review $how after ${Plural.count(math.max(runNo.getOrElse(0), counts.runs).toLong, com.histmersion.api.Plural.CountSingular("run"))}" +
            s"${outcomeSuffix(item.flatMap(_.outcome))} ${majorsLine(counts)} A person decides what happens next."
        )
      case _ if item.isEmpty && f.ledger.forall(_.findings == 0) =>
        (
          ReviewState.Unreviewed,
          "not reviewed",
          "Not reviewed yet: no review of this article is recorded and none is queued."
        )
      case _ if counts.openMajors > 0 =>
        (
          ReviewState.MajorsOpen,
          s"${Plural.count(counts.openMajors.toLong, com.histmersion.api.Plural.CountSingular("major"))} open",
          s"Reviewed: ${majorsLine(counts)} Found over ${Plural.count(counts.runs.toLong, com.histmersion.api.Plural.CountSingular("run"))}" +
            s"${outcomeSuffix(item.flatMap(_.outcome))}"
        )
      case _ =>
        val minors = if counts.openMinors > 0 then
          s" ${Plural.count(counts.openMinors.toLong, com.histmersion.api.Plural.CountSingular("minor finding"))} still open."
        else ""
        val total = if counts.findings > 0 then
          s" ${Plural.count(counts.findings.toLong, com.histmersion.api.Plural.CountSingular("finding"))} raised over ${Plural.count(counts.runs.toLong, com.histmersion.api.Plural.CountSingular("run"))}."
        else ""
        (
          ReviewState.Passed,
          "passed",
          s"Reviewed: passed — no MAJOR finding is open.$minors$total${outcomeSuffix(item.flatMap(_.outcome), "Review manager: ", none = "")}"
        )
    val waiting = if f.waiting.isEmpty then ""
    else " Waiting for a person: " + f.waiting.map(a => s"approval #${a.id} (${a.kind})").mkString(", ") + "."
    ReviewBadge(
      state,
      label,
      detail + waiting,
      counts.openMajors,
      counts.openMinors,
      counts.findings,
      counts.runs,
      f.round.filter(_ => state == ReviewState.InReview)
    )

  private def soFar(c: LedgerFacts): com.histmersion.domain.ArticleReviewStatus.Progress =
    if c.findings == 0 then com.histmersion.domain.ArticleReviewStatus.Progress("") else s" Open so far: ${c.openMajors} MAJOR, ${c.openMinors} minor."

  private def majorsLine(c: LedgerFacts): com.histmersion.domain.ArticleReviewStatus.StatusLine =
    s"${Plural.count(c.openMajors.toLong, com.histmersion.api.Plural.CountSingular("MAJOR finding"))} ${if c.openMajors == 1 then "is" else "are"} still open."

  /** `: <outcome>.` after a sentence still open, or ` <lead><outcome>.` after a finished one; `none` when the manager
    * recorded no outcome. The manager's own leading `stopped:`/`failed:` is dropped.
    */
  private def outcomeSuffix(outcome: Option[com.histmersion.domain.ArticleReviewStatus.ItemFacts.Outcome], lead: String = "", none: String = "."): com.histmersion.domain.ArticleReviewStatus.OutcomeSuffix =
    outcome
      .map(_.trim.stripPrefix("stopped:").stripPrefix("failed:").trim)
      .filter(_.nonEmpty)
      .fold(none)(o => if lead.isEmpty then s": $o." else s" $lead$o.")

  // ── the history ──────────────────────────────────────────────────────────────────────────────

  /** Every run the ledger names (raising a finding or closing one), oldest first: by the run's start when it is known
    * (`runStarts`, run id → UTC time), a run whose record is gone before those, and otherwise in the order the ledger
    * first names them (it only ever appends).
    */
  def runOrder(l: Ledger, runStarts: Map[com.histmersion.domain.ArticleReviewStatus.RunKey, com.histmersion.domain.ArticleReviewStatus.RunStart]): List[com.histmersion.domain.ArticleReviewStatus.RunOrder] =
    val named = l.entries.flatMap(e => e.run :: e.closed.map(_.run).toList).filter(_.nonEmpty).distinct
    named.zipWithIndex.sortBy((run, i) => (runStarts.get(run), i)).map(_._1)

  /** The findings grouped by the pass that first raised them, in time order: run by run ([[runOrder]]), pass by pass,
    * each with what became of it.
    */
  def history(l: Ledger, runStarts: Map[com.histmersion.domain.ArticleReviewStatus.HistoryRunStartsPart1, com.histmersion.domain.ArticleReviewStatus.HistoryRunStartsPart2]): List[ReviewRound] =
    val order = runOrder(l, runStarts)
    val number = order.zipWithIndex.map((r, i) => r -> (i + 1)).toMap
    l.entries
      .groupBy(e => (e.run, e.round))
      .toList
      .sortBy { case ((run, round), _) => (number.getOrElse(run, Int.MaxValue), round) }
      .map { case ((run, round), es) =>
        ReviewRound(run, number.getOrElse(run, 0), round, runStarts.get(run), es.map(finding(_, number)))
      }

  private def finding(e: Entry, number: Map[com.histmersion.domain.ArticleReviewStatus.EntryKey, Int]): ReviewFinding =
    val f = e.finding
    ReviewFinding(
      id = f.id,
      reviewer = f.reviewer.wire,
      severity = f.severity.wire,
      kind = f.kind,
      where = f.where,
      problem = f.problem,
      fix = f.fix,
      quote = f.quote,
      outcome = e.status.wire,
      note = e.closed.map(_.note).getOrElse(""),
      closedBy = e.closed.map(_.by),
      closedRound = e.closed.map(_.round),
      closedRunNumber = e.closed.flatMap(c => number.get(c.run)),
      reopened = e.reopened,
      deferred = e.deferred.isDefined && e.open
    )
