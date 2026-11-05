package com.histmersion.domain

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.jobs.JobState

import java.nio.charset.StandardCharsets
import java.time.{Instant, LocalDate, ZoneOffset}

/** The acquisition list's LEGAL RE-CHECK (docs/research-process-managers.md "As built: the acquisition list's
  * re-check"). An item on the acquisition list — the person's buy/borrow list — was put there because no legal copy was
  * found THEN; fetchers and keys added since may have one. A model-free pass of the managers' tick looks at every open
  * item that names a DOI: one the corpus holds with its original is closed; one checked within `every-days` under the
  * same fetcher generation is left alone; the others (a few a pass, a cap a day, within the shared legal ingest
  * allowance) are tried through the LEGAL chain only. The pure rules; the server reads the facts and writes the result.
  */
object AcquireRecheck:

  /** `acquire.recheck.*` as the rules read them: on or off, how many days an item's check holds, at most how many items
    * a day and a pass, the minutes between passes, the share (percent) of the shared daily legal ingest allowance the
    * re-check may take, and the hours after which a check that got no answer is due again.
    */
  type DueAtGeneration = DueAtGeneration.T
  object DueAtGeneration extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DueAtEveryDays = DueAtEveryDays.T
  object DueAtEveryDays extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DueAtRetryHours = DueAtRetryHours.T
  object DueAtRetryHours extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsDueResult = IsDueResult.T
  object IsDueResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsDueGeneration = IsDueGeneration.T
  object IsDueGeneration extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsDueEveryDays = IsDueEveryDays.T
  object IsDueEveryDays extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsDueRetryHours = IsDueRetryHours.T
  object IsDueRetryHours extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ActionDois = ActionDois.T
  object ActionDois extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ActionHeldArg1 = ActionHeldArg1.T
  object ActionHeldArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FailureMessage = FailureMessage.T
  object FailureMessage extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AnswersResult = AnswersResult.T
  object AnswersResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AnswersMessage = AnswersMessage.T
  object AnswersMessage extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReasonResult = ReasonResult.T
  object ReasonResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReasonDetail = ReasonDetail.T
  object ReasonDetail extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AllowanceResult = AllowanceResult.T
  object AllowanceResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AllowanceCheckedToday = AllowanceCheckedToday.T
  object AllowanceCheckedToday extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PassDueResult = PassDueResult.T
  object PassDueResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PassDuePassMinutes = PassDuePassMinutes.T
  object PassDuePassMinutes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GenerationResult = GenerationResult.T
  object GenerationResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GenerationParts = GenerationParts.T
  object GenerationParts extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NextDueGeneration = NextDueGeneration.T
  object NextDueGeneration extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NextDueEveryDays = NextDueEveryDays.T
  object NextDueEveryDays extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NextDueRetryHours = NextDueRetryHours.T
  object NextDueRetryHours extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckedSinceResult = CheckedSinceResult.T
  object CheckedSinceResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JobPicksTriedByJob = JobPicksTriedByJob.T
  object JobPicksTriedByJob extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JobPicksDueResult = JobPicksDueResult.T
  object JobPicksDueResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResumableResult = ResumableResult.T
  object ResumableResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResumableStatus = ResumableStatus.T
  object ResumableStatus extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResumableError = ResumableError.T
  object ResumableError extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProgressLineResult = ProgressLineResult.T
  object ProgressLineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Settings(
      enabled: Settings.Enabled,
      everyDays: Settings.EveryDays,
      perDay: Settings.PerDay,
      perPass: Settings.PerPass,
      passMinutes: Settings.PassMinutes,
      sharePercent: Settings.SharePercent,
      retryHours: Settings.RetryHours
  )

  object Settings:
    type Enabled = Enabled.T
    object Enabled extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EveryDays = EveryDays.T
    object EveryDays extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PerDay = PerDay.T
    object PerDay extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PerPass = PerPass.T
    object PerPass extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PassMinutes = PassMinutes.T
    object PassMinutes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SharePercent = SharePercent.T
    object SharePercent extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RetryHours = RetryHours.T
    object RetryHours extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** An item's last check: when, under which fetcher generation ([[generation]]), and whether it got no answer
    * ([[Outcome.Failed]]: a rate limit, an upstream down).
    */
  final case class LastCheck(at: Instant, generation: LastCheck.Generation, failed: LastCheck.Failed = false)

  object LastCheck:
    type Generation = Generation.T
    object Generation extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Failed = Failed.T
    object Failed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** When an item is next due: never checked, or checked under another fetcher generation (a fetcher or a key was added
    * since) = now (None); a check that got no answer `retryHours` after it; else `everyDays` after its check.
    */
  def dueAt(last: Option[LastCheck], generation: com.histmersion.domain.AcquireRecheck.DueAtGeneration, everyDays: com.histmersion.domain.AcquireRecheck.DueAtEveryDays, retryHours: com.histmersion.domain.AcquireRecheck.DueAtRetryHours): Option[Instant] =
    last
      .filter(_.generation == generation)
      .map(l =>
        if l.failed then l.at.plusSeconds(math.max(1, retryHours).toLong * 3600L)
        else l.at.plusSeconds(math.max(1, everyDays).toLong * 86400L)
      )

  def isDue(last: Option[LastCheck], generation: com.histmersion.domain.AcquireRecheck.IsDueGeneration, everyDays: com.histmersion.domain.AcquireRecheck.IsDueEveryDays, retryHours: com.histmersion.domain.AcquireRecheck.IsDueRetryHours, now: Instant): com.histmersion.domain.AcquireRecheck.IsDueResult =
    dueAt(last, generation, everyDays, retryHours).forall(!_.isAfter(now))

  /** What the corpus holds under an item's DOI: the source, and whether it has its original (a converted body or a
    * stored file).
    */
  final case class Held(citekey: com.histmersion.domain.library.Citekey, hasOriginal: Held.HasOriginal)

  object Held:
    type HasOriginal = HasOriginal.T
    object HasOriginal extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** What a pass does with one open item. */
  enum Action:
    /** No DOI in its text: nothing to look up. */
    case NoDoi

    /** The corpus has the work with its original: the item is done. */
    case Close(citekey: com.histmersion.domain.library.Citekey)

    /** Try the legal chain — into the metadata-only source `into` when there is one (its enrichment stays). */
    case Fetch(doi: com.histmersion.domain.library.Doi, into: Option[Fetch.Into])

  object Action:
    object Fetch:
      type Into = Into.T
      object Into extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The first DOI an item names decides (an item is one work). */
  def action(dois: List[com.histmersion.domain.AcquireRecheck.ActionDois], held: com.histmersion.domain.AcquireRecheck.ActionHeldArg1 => Option[Held]): Action =
    dois.headOption match
      case None      => Action.NoDoi
      case Some(doi) =>
        held(doi) match
          case Some(h) if h.hasOriginal => Action.Close(h.citekey)
          case other                    => Action.Fetch(doi, other.map(_.citekey))

  /** What a check found, as stored (`acquire_checks.outcome`). */
  enum Outcome(val wire: Outcome.Wire):
    /** In the corpus with its original: closed. */
    case Closed extends Outcome("closed")

    /** A legal copy found and handed to the conversion; the conversion closes the item. */
    case Fetched extends Outcome("fetched")

    /** The legal chain has no copy (yet). */
    case NoCopy extends Outcome("no-copy")

    /** The check did not get to an answer (an upstream down, the converter refused): tried again when due. */
    case Failed extends Outcome("failed")

  object Outcome:
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type ParseS = ParseS.T
    object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def parse(s: com.histmersion.domain.AcquireRecheck.Outcome.ParseS): Option[Outcome] = values.find(_.wire == s.trim)

  /** A failed legal ingest: no legal copy, or something else in the way. */
  def failure(message: com.histmersion.domain.AcquireRecheck.FailureMessage): Outcome =
    if IngestBacklog.isNoLegalCopy(message) then Outcome.NoCopy else Outcome.Failed

  /** What each fetcher answered, from the legal ingest's refusal (`no legal open-access copy of <doi>: <the chain's
    * message> — <fetcher>: <answer>; …`): the answers when there are any, else the message.
    */
  def answers(message: com.histmersion.domain.AcquireRecheck.AnswersMessage): com.histmersion.domain.AcquireRecheck.AnswersResult =
    val m = Option(message).map(_.trim).getOrElse("")
    val rest =
      if !IngestBacklog.isNoLegalCopy(m) then m
      else
        m.indexOf(": ") match
          case -1 => ""
          case i  => m.drop(i + 2).trim
    rest.indexOf(" — ") match
      case -1 => rest
      case i  => rest.drop(i + 3).trim

  /** The short reason an item carries: `checked 2026-10-27: no legal copy (unpaywall: no copy — none; …)`. */
  def reason(at: Instant, outcome: Outcome, detail: com.histmersion.domain.AcquireRecheck.ReasonDetail): com.histmersion.domain.AcquireRecheck.ReasonResult =
    val day = LocalDate.ofInstant(at, ZoneOffset.UTC)
    val d = Option(detail).map(_.trim).getOrElse("")
    val text = outcome match
      case Outcome.Closed  => s"checked $day: in the corpus with its original as [[$d]] — closed"
      case Outcome.Fetched => s"checked $day: a legal copy found ($d) — converting; the item closes once it is stored"
      case Outcome.NoCopy  =>
        val a = answers(d)
        s"checked $day: no legal copy" + (if a.isEmpty then "" else s" ($a)")
      case Outcome.Failed => s"checked $day: no answer (${if d.isEmpty then "the ingest failed" else d})"
    text.take(500)

  /** What the shared daily legal ingest allowance leaves the re-check (the autopilot's: absent when it is off or has no
    * cap): what is `left` of the `cap` today, the watchers' backlog waiting for it, and what the re-check fetched
    * today.
    */
  final case class Shared(left: Shared.Left, cap: Shared.Cap, backlogPending: Shared.BacklogPending, fetchedToday: Shared.FetchedToday)

  object Shared:
    type Left = Left.T
    object Left extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Cap = Cap.T
    object Cap extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BacklogPending = BacklogPending.T
    object BacklogPending extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FetchedToday = FetchedToday.T
    object FetchedToday extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** How many items this pass may try: within the day's cap (after `checkedToday`) and the pass's; and, under a shared
    * allowance, within what the watchers' backlog leaves of it (the backlog and the watchers' new finds come first) and
    * within the re-check's share of the cap — each try may fetch one work.
    */
  def allowance(s: Settings, checkedToday: com.histmersion.domain.AcquireRecheck.AllowanceCheckedToday, shared: Option[Shared]): com.histmersion.domain.AcquireRecheck.AllowanceResult =
    val own = math.min(math.max(0, s.perDay) - math.max(0, checkedToday), math.max(0, s.perPass))
    val room = shared.fold(Int.MaxValue) { sh =>
      val share = math.max(0, sh.cap) * math.max(0, math.min(100, s.sharePercent)) / 100
      math.min(sh.left - math.max(0, sh.backlogPending), share - math.max(0, sh.fetchedToday))
    }
    math.max(0, math.min(own, room))

  /** Is a pass due: never run (since the start), or `passMinutes` passed since the last. */
  def passDue(last: Option[Instant], passMinutes: com.histmersion.domain.AcquireRecheck.PassDuePassMinutes, now: Instant): com.histmersion.domain.AcquireRecheck.PassDueResult =
    last.forall(l => !l.plusSeconds(math.max(1, passMinutes).toLong * 60L).isAfter(now))

  /** The fetcher generation: one short digest of what the legal chain is — its fetchers in order, and each one's plugin
    * and settings (secrets as references) — so a fetcher or a key added changes it.
    */
  def generation(parts: List[com.histmersion.domain.AcquireRecheck.GenerationParts]): com.histmersion.domain.AcquireRecheck.GenerationResult =
    val md = java.security.MessageDigest.getInstance("SHA-256")
    md.digest(parts.mkString("\n").getBytes(StandardCharsets.UTF_8)).take(8).map(b => f"${b & 0xff}%02x").mkString

  /** The earliest time one of the checked items falls due again (None: none waits). */
  def nextDue(checks: List[LastCheck], generation: com.histmersion.domain.AcquireRecheck.NextDueGeneration, everyDays: com.histmersion.domain.AcquireRecheck.NextDueEveryDays, retryHours: com.histmersion.domain.AcquireRecheck.NextDueRetryHours): Option[Instant] =
    checks.flatMap(c => dueAt(Some(c), generation, everyDays, retryHours)).minOption

  // ── the re-check job ─────────────────────────────────────────────────────────────────────────────

  /** An open item that names a DOI, as a pass or a job orders it: its id and its last check. */
  final case class Candidate(item: Candidate.Item, last: Option[LastCheck])

  object Candidate:
    type Item = Item.T
    object Item extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The order items are tried in: the never-checked first, then the oldest check, then the item id. */
  def order(candidates: List[Candidate]): List[Candidate] =
    candidates.sortBy(c => (c.last.isDefined, c.last.fold(0L)(_.at.toEpochMilli), c.item))

  /** A re-check JOB: a person's pass over many items, run past the request. `all` = every open item that names a DOI,
    * due or not and past every cap (`upTo` unused); else at most `upTo` due ones (the caps as they stood when it
    * started). `since` = when it first started: an item checked since then was checked by it, so a job resumed after a
    * restart or a drain skips it.
    */
  final case class JobPlan(all: JobPlan.All, upTo: JobPlan.UpTo, since: Instant)

  object JobPlan:
    type All = All.T
    object All extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpTo = UpTo.T
    object UpTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Was this item checked by a job that started at `since` (or since then by anything else)? */
  def checkedSince(last: Option[LastCheck], since: Instant): com.histmersion.domain.AcquireRecheck.CheckedSinceResult = last.exists(l => !l.at.isBefore(since))

  /** What a job — or its resumption — still tries, in [[order]]: the items not checked since it started; for a capped
    * job only the `due` ones, and no more than its `upTo` less the `triedByJob` already.
    */
  def jobPicks(
      plan: JobPlan,
      candidates: List[Candidate],
      triedByJob: com.histmersion.domain.AcquireRecheck.JobPicksTriedByJob,
      due: Candidate => com.histmersion.domain.AcquireRecheck.JobPicksDueResult
  ): List[Candidate] =
    val left = order(candidates.filterNot(c => checkedSince(c.last, plan.since)))
    if plan.all then left else left.filter(due).take(math.max(0, plan.upTo - math.max(0, triedByJob)))

  /** The end of a job stopped while the autopilot drains: a later managers' tick resumes it. */
  val StoppedDraining = "stopped while the autopilot drains: it resumes once the drain is lifted"

  /** Does a later job take up where this one stopped? Only one a restart (`interrupted`) or the drain stopped; one that
    * finished, or failed for another reason, is over.
    */
  def resumable(status: com.histmersion.domain.AcquireRecheck.ResumableStatus, error: Option[com.histmersion.domain.AcquireRecheck.ResumableError]): com.histmersion.domain.AcquireRecheck.ResumableResult =
    status == JobState.Failed.wire &&
      error.exists(e => e == JobState.Interrupted || e == StoppedDraining)

  /** What a job did so far: of the `total` items it tries, how many it `tried`, and what they found; the last item that
    * got no answer, why (the job goes on with the next).
    */
  final case class Tally(
      total: Tally.Total,
      tried: Tally.Tried = 0,
      closed: Tally.Closed = 0,
      fetched: Tally.Fetched = 0,
      noCopy: Tally.NoCopy = 0,
      failed: Tally.Failed = 0,
      lastFailure: Option[Tally.LastFailure] = None
  ):
    def add(outcome: Outcome, detail: com.histmersion.domain.AcquireRecheck.Tally.AddDetail): Tally = outcome match
      case Outcome.Closed  => this.modify(_.closed).setTo(closed + 1)
      case Outcome.Fetched => this.modify(_.tried).setTo(tried + 1).modify(_.fetched).setTo(fetched + 1)
      case Outcome.NoCopy  => this.modify(_.tried).setTo(tried + 1).modify(_.noCopy).setTo(noCopy + 1)
      case Outcome.Failed  => this.modify(_.tried).setTo(tried + 1).modify(_.failed).setTo(failed + 1).modify(_.lastFailure).setTo(Some(detail))

  object Tally:
    type AddDetail = AddDetail.T
    object AddDetail extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Total = Total.T
    object Total extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tried = Tried.T
    object Tried extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Closed = Closed.T
    object Closed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Fetched = Fetched.T
    object Fetched extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NoCopy = NoCopy.T
    object NoCopy extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Failed = Failed.T
    object Failed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LastFailure = LastFailure.T
    object LastFailure extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The job's progress: `120/453 items · 3 closed · 9 fetched · 98 no legal copy · 10 failed (HTTP 429 …)`. */
  def progressLine(t: Tally): com.histmersion.domain.AcquireRecheck.ProgressLineResult =
    val why = t.lastFailure.map(_.trim).filter(_.nonEmpty).map(w => if w.length > 60 then w.take(60).trim + " …" else w)
    s"${t.tried}/${t.total} items · ${t.closed} closed · ${t.fetched} fetched · ${t.noCopy} no legal copy · " +
      s"${t.failed} failed" + why.fold("")(w => s" ($w)")
