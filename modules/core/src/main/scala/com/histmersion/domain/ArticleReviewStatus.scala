package com.histmersion
package domain

import com.histmersion.domain.reviewledger.Ledger
import com.histmersion.domain.reviewledger.Entry

import com.histmersion.api.Plural
import com.histmersion.api.research.{ReviewApproval, ReviewBadge, ReviewFinding, ReviewRound, ReviewState}
import com.histmersion.domain.automation.ItemStatus
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.automation.Run
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

/** Where an article stands with the review panel, derived from what the review manager and the review ledger record
  * about it — pure; the server reads the facts (one cheap query for a whole list page, the ledger itself for one
  * article's history).
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@movesTo(FeaturePlace.ResearchArticlesReview)
object ArticleReviewStatus:
  type OutcomeSuffix = OutcomeSuffix.T
  object OutcomeSuffix extends Newtype[String, Pure]
  type Progress = Progress.T
  object Progress extends Newtype[String, Pure]
  export ReviewRound.StartedAt as RunStart
  type StatusLine = StatusLine.T
  object StatusLine extends Newtype[String, Pure]

  /** The review manager's `article-review` item for the article: its status, its last outcome line and how many runs it
    * started.
    */
  final case class ItemFacts(
      status: ItemStatus,
      outcome: Option[ItemFacts.Outcome] = None,
      attempts: ItemFacts.Attempts = ItemFacts.Attempts(0)
  )

  object ItemFacts:
    type Outcome = Outcome.T
    object Outcome extends Newtype[String, Pure]
    type Attempts = Attempts.T
    object Attempts extends Newtype[Int, Pure]

  /** The ledger's findings counted: the open MAJOR and minor ones, all of them, and the runs that raised or closed one.
    */
  final case class LedgerFacts(
      openMajors: LedgerFacts.OpenMajors,
      openMinors: LedgerFacts.OpenMinors,
      findings: LedgerFacts.Findings,
      runs: LedgerFacts.Runs
  )

  object LedgerFacts:
    export ReviewBadge.{Findings, OpenMajors, OpenMinors, Runs}

    def of(l: Ledger): LedgerFacts =
      LedgerFacts(
        OpenMajors(l.openMajors.size),
        OpenMinors(l.open.count(!_.major)),
        Findings(l.entries.size),
        Runs(runOrder(l, Map.empty).size)
      )

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
    export ReviewRound.Round

  /** The state, from the item first (a run going on, queued, stopped), then the ledger's open MAJOR findings. A
    * cancelled item counts as no item: what the ledger says stands.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def badge(f: Facts): ReviewBadge =
    val item = f.item.filter(_.status != ItemStatus.Cancelled)
    val counts = f.ledger.getOrElse(
      LedgerFacts(LedgerFacts.OpenMajors(0), LedgerFacts.OpenMinors(0), LedgerFacts.Findings(0), LedgerFacts.Runs(0))
    )
    val runNo = item.map(_.attempts).filter(_.value > 0)
    val (state, label, detail) = item.map(_.status) match
      case Some(ItemStatus.Active) =>
        (
          ReviewState.InReview,
          f.round.fold("in review")(r => s"in review · round $r"),
          s"In review: the review panel is working on it now${f.round.fold("")(r => s" (round $r of this run)")}.${soFar(counts)}"
        )
      case Some(ItemStatus.Pending | ItemStatus.Held) =>
        val again = item
          .flatMap(i => Option.when(i.attempts.value > 0)(i))
          .fold(" The review manager starts the panel when a run slot is free.")(i =>
            s" Run ${i.attempts} ended without converging${outcomeSuffix(i.outcome)}"
          )
        (ReviewState.Queued, "queued", s"Queued for review.$again${soFar(counts)}")
      case Some(ItemStatus.Stopped | ItemStatus.Failed) =>
        val how = if item.exists(_.status == ItemStatus.Failed) then "failed" else "stopped"
        (
          ReviewState.Stopped,
          s"review $how",
          s"Review $how after ${Plural.count(Plural.CountN(math.max(runNo.map(_.value).getOrElse(0), counts.runs.value).toLong), Plural.CountSingular("run"))}" +
            s"${outcomeSuffix(item.flatMap(_.outcome))} ${majorsLine(counts)} A person decides what happens next."
        )
      case _ if item.isEmpty && f.ledger.forall(_.findings.value == 0) =>
        (
          ReviewState.Unreviewed,
          "not reviewed",
          "Not reviewed yet: no review of this article is recorded and none is queued."
        )
      case _ if counts.openMajors.value > 0 =>
        (
          ReviewState.MajorsOpen,
          s"${Plural.count(Plural.CountN(counts.openMajors.value.toLong), Plural.CountSingular("major"))} open",
          s"Reviewed: ${majorsLine(counts)} Found over ${Plural.count(Plural.CountN(counts.runs.value.toLong), Plural.CountSingular("run"))}" +
            s"${outcomeSuffix(item.flatMap(_.outcome))}"
        )
      case _ =>
        val minors = if counts.openMinors.value > 0 then
          s" ${Plural.count(Plural.CountN(counts.openMinors.value.toLong), Plural.CountSingular("minor finding"))} still open."
        else ""
        val total = if counts.findings.value > 0 then
          s" ${Plural.count(Plural.CountN(counts.findings.value.toLong), Plural.CountSingular("finding"))} raised over ${Plural.count(Plural.CountN(counts.runs.value.toLong), Plural.CountSingular("run"))}."
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
      ReviewBadge.Label(label),
      ReviewBadge.Detail(detail + waiting),
      counts.openMajors,
      counts.openMinors,
      counts.findings,
      counts.runs,
      f.round.filter(_ => state == ReviewState.InReview)
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def soFar(c: LedgerFacts): Progress =
    if c.findings.value == 0 then Progress("")
    else Progress(s" Open so far: ${c.openMajors} MAJOR, ${c.openMinors} minor.")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def majorsLine(c: LedgerFacts): StatusLine =
    StatusLine(s"${Plural.count(Plural.CountN(c.openMajors.value.toLong), Plural.CountSingular("MAJOR finding"))} ${
        if c.openMajors.value == 1 then "is" else "are"
      } still open.")

  /** `: <outcome>.` after a sentence still open, or ` <lead><outcome>.` after a finished one; `none` when the manager
    * recorded no outcome. The manager's own leading `stopped:`/`failed:` is dropped.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def outcomeSuffix(outcome: Option[ItemFacts.Outcome], lead: String = "", none: String = "."): OutcomeSuffix =
    OutcomeSuffix(
      outcome
        .map(_.value.trim.stripPrefix("stopped:").stripPrefix("failed:").trim)
        .filter(_.nonEmpty)
        .fold(none)(o => if lead.isEmpty then s": $o." else s" $lead$o.")
    )

  // ── the history ──────────────────────────────────────────────────────────────────────────────

  /** Every run the ledger names (raising a finding or closing one), oldest first: by the run's start when it is known
    * (`runStarts`, run id → UTC time), a run whose record is gone before those, and otherwise in the order the ledger
    * first names them (it only ever appends).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
  def runOrder(l: Ledger, runStarts: Map[Run.Id, ArticleReviewStatus.RunStart]): List[Run.Id] =
    val named = l.entries.flatMap(e => e.run :: e.closed.map(_.run).toList).filter(_.value.nonEmpty).distinct
    named.zipWithIndex.sortBy((run, i) => (runStarts.get(run).map(_.value), i)).map(_._1)

  /** The findings grouped by the pass that first raised them, in time order: run by run ([[runOrder]]), pass by pass,
    * each with what became of it.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
  def history(l: Ledger, runStarts: Map[Run.Id, ArticleReviewStatus.RunStart]): List[ReviewRound] =
    val order = runOrder(l, runStarts)
    val number = order.zipWithIndex.map((r, i) => r -> (i + 1)).toMap
    l.entries
      .groupBy(e => (e.run, e.round))
      .toList
      .sortBy { case ((run, round), _) => (number.getOrElse(run, Int.MaxValue), round.value: Int) }
      .map { case ((run, round), es) =>
        ReviewRound(
          ReviewRound.Run(run.value),
          ReviewRound.RunNumber(number.getOrElse(run, 0)),
          ReviewRound.Round(round.value),
          runStarts.get(run),
          es.map(finding(_, number))
        )
      }

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def finding(e: Entry, number: Map[Run.Id, Int]): ReviewFinding =
    val f = e.finding
    ReviewFinding(
      id = ReviewFinding.Id(f.id.value),
      reviewer = ReviewFinding.Reviewer(f.reviewer.wire.value),
      severity = ReviewFinding.Severity(f.severity.wire.value),
      kind = ReviewFinding.Kind(f.kind.value),
      where = ReviewFinding.Where(f.where.value),
      problem = ReviewFinding.Problem(f.problem.value),
      fix = ReviewFinding.Fix(f.fix.value),
      quote = ReviewFinding.Quote(f.quote.value),
      outcome = ReviewFinding.Outcome(e.status.wire.value),
      note = ReviewFinding.Note(e.closed.map(_.note.value).getOrElse("")),
      closedBy = e.closed.map(c => ReviewFinding.ClosedBy(c.by.value)),
      closedRound = e.closed.map(c => ReviewFinding.ClosedRound(c.round.value)),
      closedRunNumber = e.closed.flatMap(c => number.get(c.run).map(ReviewFinding.ClosedRunNumber(_))),
      reopened = ReviewFinding.Reopened(e.reopened.value),
      deferred =
        (if e.deferred.isDefined && e.open then ReviewFinding.Deferred.Deferred else ReviewFinding.Deferred.Open)
    )
