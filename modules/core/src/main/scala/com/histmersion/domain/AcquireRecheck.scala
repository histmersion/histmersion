package com.histmersion
package domain

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.jobs.JobState

import java.nio.charset.StandardCharsets
import java.time.{Instant, LocalDate, ZoneOffset}
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.library.Doi

/** The acquisition list's LEGAL RE-CHECK (docs/research-process-managers.md "As built: the acquisition list's
  * re-check"). An item on the acquisition list — the person's buy/borrow list — was put there because no legal copy was
  * found THEN; fetchers and keys added since may have one. A model-free pass of the managers' tick looks at every open
  * item that names a DOI: one the corpus holds with its original is closed; one checked within `every-days` under the
  * same fetcher generation is left alone; the others (a few a pass, a cap a day, within the shared legal ingest
  * allowance) are tried through the LEGAL chain only. The pure rules; the server reads the facts and writes the result.
  */
object AcquireRecheck:
  type Allowance = Allowance.T
  object Allowance extends Newtype[Int, Pure]
  type CheckedToday = CheckedToday.T
  object CheckedToday extends Newtype[Int, Pure]
  type Detail = Detail.T
  object Detail extends Newtype[String, Pure]
  type GenerationPart = GenerationPart.T
  object GenerationPart extends Newtype[String, Pure]
  type ProgressLine = ProgressLine.T
  object ProgressLine extends Newtype[String, Pure]
  type ReplyText = ReplyText.T
  object ReplyText extends Newtype[String, Pure]

  /** `acquire.recheck.*` as the rules read them: on or off, how many days an item's check holds, at most how many items
    * a day and a pass, the minutes between passes, the share (percent) of the shared daily legal ingest allowance the
    * re-check may take, and the hours after which a check that got no answer is due again.
    */
  export LastCheck.Generation as IsDueGeneration
  export Settings.{EveryDays as IsDueEveryDays, RetryHours as IsDueRetryHours}
  export LastCheck.Generation as NextDueGeneration
  export Settings.{EveryDays as NextDueEveryDays, RetryHours as NextDueRetryHours}

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
    object Enabled extends Newtype[Boolean, Pure]
    type EveryDays = EveryDays.T
    object EveryDays extends Newtype[Int, Pure]
    type PerDay = PerDay.T
    object PerDay extends Newtype[Int, Pure]
    type PerPass = PerPass.T
    object PerPass extends Newtype[Int, Pure]
    type PassMinutes = PassMinutes.T
    object PassMinutes extends Newtype[Int, Pure]
    type SharePercent = SharePercent.T
    object SharePercent extends Newtype[Int, Pure]
    type RetryHours = RetryHours.T
    object RetryHours extends Newtype[Int, Pure]

  /** An item's last check: when, under which fetcher generation ([[generation]]), and whether it got no answer
    * ([[Outcome.Failed]]: a rate limit, an upstream down).
    */
  final case class LastCheck(
      at: Instant,
      generation: LastCheck.Generation,
      failed: LastCheck.Failed = LastCheck.Failed(false)
  )

  object LastCheck:
    type Generation = Generation.T
    object Generation extends Newtype[String, Pure]
    type Failed = Failed.T
    object Failed extends Newtype[Boolean, Pure]

  /** When an item is next due: never checked, or checked under another fetcher generation (a fetcher or a key was added
    * since) = now (None); a check that got no answer `retryHours` after it; else `everyDays` after its check.
    */
  def dueAt(
      last: Option[LastCheck],
      generation: LastCheck.Generation,
      everyDays: Settings.EveryDays,
      retryHours: Settings.RetryHours
  ): Option[Instant] =
    last
      .filter(_.generation == generation)
      .map(l =>
        if l.failed.value then l.at.plusSeconds(math.max(1, retryHours.value).toLong * 3600L)
        else l.at.plusSeconds(math.max(1, everyDays.value).toLong * 86400L)
      )

  def isDue(
      last: Option[LastCheck],
      generation: AcquireRecheck.IsDueGeneration,
      everyDays: AcquireRecheck.IsDueEveryDays,
      retryHours: AcquireRecheck.IsDueRetryHours,
      now: Instant
  ): Boolean =
    dueAt(last, generation, everyDays, retryHours).forall(!_.isAfter(now))

  /** What the corpus holds under an item's DOI: the source, and whether it has its original (a converted body or a
    * stored file).
    */
  final case class Held(citekey: Citekey, hasOriginal: Held.HasOriginal)

  object Held:
    type HasOriginal = HasOriginal.T
    object HasOriginal extends Newtype[Boolean, Pure]

  /** What a pass does with one open item. */
  enum Action derives CanEqual:
    /** No DOI in its text: nothing to look up. */
    case NoDoi

    /** The corpus has the work with its original: the item is done. */
    case Close(citekey: Citekey)

    /** Try the legal chain — into the metadata-only source `into` when there is one (its enrichment stays). */
    case Fetch(doi: Doi, into: Option[Citekey])

  /** The first DOI an item names decides (an item is one work). */
  def action(dois: List[Doi], held: Doi => Option[Held]): Action =
    dois.headOption match
      case None      => Action.NoDoi
      case Some(doi) =>
        held(doi) match
          case Some(h) if h.hasOriginal.value => Action.Close(h.citekey)
          case other                          => Action.Fetch(doi, other.map(_.citekey))

  /** What a check found, as stored (`acquire_checks.outcome`). */
  enum Outcome(val wire: Outcome.Wire) derives CanEqual:
    /** In the corpus with its original: closed. */
    case Closed extends Outcome(Outcome.Wire("closed"))

    /** A legal copy found and handed to the conversion; the conversion closes the item. */
    case Fetched extends Outcome(Outcome.Wire("fetched"))

    /** The legal chain has no copy (yet). */
    case NoCopy extends Outcome(Outcome.Wire("no-copy"))

    /** The check did not get to an answer (an upstream down, the converter refused): tried again when due. */
    case Failed extends Outcome(Outcome.Wire("failed"))

  object Outcome:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    def parse(s: Wire): Option[Outcome] = values.find(_.wire.value == s.value.trim)

  /** A failed legal ingest: no legal copy, or something else in the way. */
  def failure(message: Problem): Outcome =
    if IngestBacklog.isNoLegalCopy(message) then Outcome.NoCopy else Outcome.Failed

  /** What each fetcher answered, from the legal ingest's refusal (`no legal open-access copy of <doi>: <the chain's
    * message> — <fetcher>: <answer>; …`): the answers when there are any, else the message.
    */
  def answers(message: Problem): ReplyText =
    val m = Option(message).fold("")(_.value.trim)
    val rest =
      if !IngestBacklog.isNoLegalCopy(message) then m
      else
        m.indexOf(": ") match
          case -1 => ""
          case i  => m.drop(i + 2).trim
    val replies = rest.indexOf(" — ") match
      case -1 => rest
      case i  => rest.drop(i + 3).trim
    ReplyText(replies)

  /** The short reason an item carries: `checked 2026-10-27: no legal copy (unpaywall: no copy — none; …)`. */
  def reason(at: Instant, outcome: Outcome, detail: Detail): Problem =
    val day = LocalDate.ofInstant(at, ZoneOffset.UTC)
    val d = Option(detail).fold("")(_.value.trim)
    val text = outcome match
      case Outcome.Closed  => s"checked $day: in the corpus with its original as [[$d]] — closed"
      case Outcome.Fetched => s"checked $day: a legal copy found ($d) — converting; the item closes once it is stored"
      case Outcome.NoCopy  =>
        val a = answers(Problem(d))
        s"checked $day: no legal copy" + (if a.value.isEmpty then "" else s" ($a)")
      case Outcome.Failed => s"checked $day: no answer (${if d.isEmpty then "the ingest failed" else d})"
    Problem(text.take(500))

  /** What the shared daily legal ingest allowance leaves the re-check (the autopilot's: absent when it is off or has no
    * cap): what is `left` of the `cap` today, the watchers' backlog waiting for it, and what the re-check fetched
    * today.
    */
  final case class Shared(
      left: Shared.Left,
      cap: Shared.Cap,
      backlogPending: Shared.BacklogPending,
      fetchedToday: Shared.FetchedToday
  )

  object Shared:
    type Left = Left.T
    object Left extends Newtype[Int, Pure]
    type Cap = Cap.T
    object Cap extends Newtype[Int, Pure]
    type BacklogPending = BacklogPending.T
    object BacklogPending extends Newtype[Int, Pure]
    type FetchedToday = FetchedToday.T
    object FetchedToday extends Newtype[Int, Pure]

  /** How many items this pass may try: within the day's cap (after `checkedToday`) and the pass's; and, under a shared
    * allowance, within what the watchers' backlog leaves of it (the backlog and the watchers' new finds come first) and
    * within the re-check's share of the cap — each try may fetch one work.
    */
  def allowance(s: Settings, checkedToday: CheckedToday, shared: Option[Shared]): Allowance =
    val own = math.min(math.max(0, s.perDay.value) - math.max(0, checkedToday.value), math.max(0, s.perPass.value))
    val room = shared.fold(Int.MaxValue) { sh =>
      val share = math.max(0, sh.cap.value) * math.max(0, math.min(100, s.sharePercent.value)) / 100
      math.min(sh.left.value - math.max(0, sh.backlogPending.value), share - math.max(0, sh.fetchedToday.value))
    }
    Allowance(math.max(0, math.min(own, room)))

  /** Is a pass due: never run (since the start), or `passMinutes` passed since the last. */
  def passDue(last: Option[Instant], passMinutes: Settings.PassMinutes, now: Instant): Boolean =
    last.forall(l => !l.plusSeconds(math.max(1, passMinutes.value).toLong * 60L).isAfter(now))

  /** The fetcher generation: one short digest of what the legal chain is — its fetchers in order, and each one's plugin
    * and settings (secrets as references) — so a fetcher or a key added changes it.
    */
  def generation(parts: List[GenerationPart]): LastCheck.Generation =
    val md = java.security.MessageDigest.getInstance("SHA-256")
    LastCheck.Generation(
      md.digest(parts.mkString("\n").getBytes(StandardCharsets.UTF_8)).take(8).map(b => f"${b & 0xff}%02x").mkString
    )

  /** The earliest time one of the checked items falls due again (None: none waits). */
  def nextDue(
      checks: List[LastCheck],
      generation: AcquireRecheck.NextDueGeneration,
      everyDays: AcquireRecheck.NextDueEveryDays,
      retryHours: AcquireRecheck.NextDueRetryHours
  ): Option[Instant] =
    checks.flatMap(c => dueAt(Some(c), generation, everyDays, retryHours)).minOption

  // ── the re-check job ─────────────────────────────────────────────────────────────────────────────

  /** An open item that names a DOI, as a pass or a job orders it: its id and its last check. */
  final case class Candidate(item: Candidate.Item, last: Option[LastCheck])

  object Candidate:
    type Item = Item.T
    object Item extends Newtype[Int, Pure]

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
    object All extends Newtype[Boolean, Pure]
    type UpTo = UpTo.T
    object UpTo extends Newtype[Int, Pure]

  /** Was this item checked by a job that started at `since` (or since then by anything else)? */
  def checkedSince(last: Option[LastCheck], since: Instant): Boolean = last.exists(l => !l.at.isBefore(since))

  /** What a job — or its resumption — still tries, in [[order]]: the items not checked since it started; for a capped
    * job only the `due` ones, and no more than its `upTo` less the `triedByJob` already.
    */
  def jobPicks(
      plan: JobPlan,
      candidates: List[Candidate],
      triedByJob: Tally.Tried,
      due: Candidate => Boolean
  ): List[Candidate] =
    val left = order(candidates.filterNot(c => checkedSince(c.last, plan.since)))
    if plan.all.value then left else left.filter(due).take(math.max(0, plan.upTo.value - math.max(0, triedByJob.value)))

  /** The end of a job stopped while the autopilot drains: a later managers' tick resumes it. */
  val StoppedDraining = "stopped while the autopilot drains: it resumes once the drain is lifted"

  /** Does a later job take up where this one stopped? Only one a restart (`interrupted`) or the drain stopped; one that
    * finished, or failed for another reason, is over.
    */
  def resumable(status: JobState.Wire, error: Option[Problem]): Boolean =
    status == JobState.Failed.wire &&
      error.exists(e => e.value == JobState.Interrupted || e.value == StoppedDraining)

  /** What a job did so far: of the `total` items it tries, how many it `tried`, and what they found; the last item that
    * got no answer, why (the job goes on with the next).
    */
  final case class Tally(
      total: Tally.Total,
      tried: Tally.Tried = Tally.Tried(0),
      closed: Tally.Closed = Tally.Closed(0),
      fetched: Tally.Fetched = Tally.Fetched(0),
      noCopy: Tally.NoCopy = Tally.NoCopy(0),
      failed: Tally.Failed = Tally.Failed(0),
      lastFailure: Option[Detail] = None
  ):
    def add(outcome: Outcome, detail: Detail): Tally = outcome match
      case Outcome.Closed  => this.modify(_.closed).setTo(Tally.Closed(closed.value + 1))
      case Outcome.Fetched =>
        this
          .modify(_.tried)
          .setTo(Tally.Tried(tried.value + 1))
          .modify(_.fetched)
          .setTo(Tally.Fetched(fetched.value + 1))
      case Outcome.NoCopy =>
        this.modify(_.tried).setTo(Tally.Tried(tried.value + 1)).modify(_.noCopy).setTo(Tally.NoCopy(noCopy.value + 1))
      case Outcome.Failed =>
        this
          .modify(_.tried)
          .setTo(Tally.Tried(tried.value + 1))
          .modify(_.failed)
          .setTo(Tally.Failed(failed.value + 1))
          .modify(_.lastFailure)
          .setTo(Some(detail))

  object Tally:

    type Total = Total.T
    object Total extends Newtype[Int, Pure]
    type Tried = Tried.T
    object Tried extends Newtype[Int, Pure]
    type Closed = Closed.T
    object Closed extends Newtype[Int, Pure]
    type Fetched = Fetched.T
    object Fetched extends Newtype[Int, Pure]
    type NoCopy = NoCopy.T
    object NoCopy extends Newtype[Int, Pure]
    type Failed = Failed.T
    object Failed extends Newtype[Int, Pure]

  /** The job's progress: `120/453 items · 3 closed · 9 fetched · 98 no legal copy · 10 failed (HTTP 429 …)`. */
  def progressLine(t: Tally): ProgressLine =
    val why =
      t.lastFailure.map(_.value.trim).filter(_.nonEmpty).map(w => if w.length > 60 then w.take(60).trim + " …" else w)
    ProgressLine(
      s"${t.tried}/${t.total} items · ${t.closed} closed · ${t.fetched} fetched · ${t.noCopy} no legal copy · " +
        s"${t.failed} failed" + why.fold("")(w => s" ($w)")
    )
