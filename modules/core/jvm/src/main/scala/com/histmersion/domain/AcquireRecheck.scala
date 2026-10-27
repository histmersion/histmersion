package com.histmersion.domain

import com.histmersion.api.JobState

import java.nio.charset.StandardCharsets
import java.time.{Instant, LocalDate, ZoneOffset}

/** The acquisition list's LEGAL RE-CHECK (docs/research-process-managers.md "As built: the acquisition
  * list's re-check"). An item on the acquisition list — the person's buy/borrow list — was put there
  * because no legal copy was found THEN; fetchers and keys added since may have one. A model-free pass of
  * the managers' tick looks at every open item that names a DOI: one the corpus holds with its original
  * is closed; one checked within `every-days` under the same fetcher generation is left alone; the others
  * (a few a pass, a cap a day, within the shared legal ingest allowance) are tried through the LEGAL chain
  * only. The pure rules; the server reads the facts and writes the result. */
object AcquireRecheck:

  /** `acquire.recheck.*` as the rules read them: on or off, how many days an item's check holds, at most
    * how many items a day and a pass, the minutes between passes, the share (percent) of the shared
    * daily legal ingest allowance the re-check may take, and the hours after which a check that got no
    * answer is due again. */
  final case class Settings(enabled: Boolean, everyDays: Int, perDay: Int, perPass: Int, passMinutes: Int,
      sharePercent: Int, retryHours: Int)

  /** An item's last check: when, under which fetcher generation ([[generation]]), and whether it got no
    * answer ([[Outcome.Failed]]: a rate limit, an upstream down). */
  final case class LastCheck(at: Instant, generation: String, failed: Boolean = false)

  /** When an item is next due: never checked, or checked under another fetcher generation (a fetcher or a
    * key was added since) = now (None); a check that got no answer `retryHours` after it; else `everyDays`
    * after its check. */
  def dueAt(last: Option[LastCheck], generation: String, everyDays: Int, retryHours: Int): Option[Instant] =
    last.filter(_.generation == generation).map(l =>
      if l.failed then l.at.plusSeconds(math.max(1, retryHours).toLong * 3600L)
      else l.at.plusSeconds(math.max(1, everyDays).toLong * 86400L))

  def isDue(last: Option[LastCheck], generation: String, everyDays: Int, retryHours: Int, now: Instant): Boolean =
    dueAt(last, generation, everyDays, retryHours).forall(!_.isAfter(now))

  /** What the corpus holds under an item's DOI: the source, and whether it has its original (a converted
    * body or a stored file). */
  final case class Held(citekey: String, hasOriginal: Boolean)

  /** What a pass does with one open item. */
  enum Action:
    /** No DOI in its text: nothing to look up. */
    case NoDoi
    /** The corpus has the work with its original: the item is done. */
    case Close(citekey: String)
    /** Try the legal chain — into the metadata-only source `into` when there is one (its enrichment stays). */
    case Fetch(doi: String, into: Option[String])

  /** The first DOI an item names decides (an item is one work). */
  def action(dois: List[String], held: String => Option[Held]): Action =
    dois.headOption match
      case None => Action.NoDoi
      case Some(doi) =>
        held(doi) match
          case Some(h) if h.hasOriginal => Action.Close(h.citekey)
          case other                    => Action.Fetch(doi, other.map(_.citekey))

  /** What a check found, as stored (`acquire_checks.outcome`). */
  enum Outcome(val wire: String):
    /** In the corpus with its original: closed. */
    case Closed extends Outcome("closed")
    /** A legal copy found and handed to the conversion; the conversion closes the item. */
    case Fetched extends Outcome("fetched")
    /** The legal chain has no copy (yet). */
    case NoCopy extends Outcome("no-copy")
    /** The check did not get to an answer (an upstream down, the converter refused): tried again when due. */
    case Failed extends Outcome("failed")

  object Outcome:
    def parse(s: String): Option[Outcome] = values.find(_.wire == s.trim)

  /** A failed legal ingest: no legal copy, or something else in the way. */
  def failure(message: String): Outcome = if IngestBacklog.isNoLegalCopy(message) then Outcome.NoCopy else Outcome.Failed

  /** What each fetcher answered, from the legal ingest's refusal (`no legal open-access copy of <doi>:
    * <the chain's message> — <fetcher>: <answer>; …`): the answers when there are any, else the message. */
  def answers(message: String): String =
    val m = Option(message).map(_.trim).getOrElse("")
    val rest =
      if !IngestBacklog.isNoLegalCopy(m) then m
      else m.indexOf(": ") match
        case -1 => ""
        case i  => m.drop(i + 2).trim
    rest.indexOf(" — ") match
      case -1 => rest
      case i  => rest.drop(i + 3).trim

  /** The short reason an item carries: `checked 2026-10-27: no legal copy (unpaywall: no copy — none; …)`. */
  def reason(at: Instant, outcome: Outcome, detail: String): String =
    val day = LocalDate.ofInstant(at, ZoneOffset.UTC)
    val d   = Option(detail).map(_.trim).getOrElse("")
    val text = outcome match
      case Outcome.Closed  => s"checked $day: in the corpus with its original as [[$d]] — closed"
      case Outcome.Fetched => s"checked $day: a legal copy found ($d) — converting; the item closes once it is stored"
      case Outcome.NoCopy  =>
        val a = answers(d)
        s"checked $day: no legal copy" + (if a.isEmpty then "" else s" ($a)")
      case Outcome.Failed  => s"checked $day: no answer (${if d.isEmpty then "the ingest failed" else d})"
    text.take(500)

  /** What the shared daily legal ingest allowance leaves the re-check (the autopilot's: absent when it is off
    * or has no cap): what is `left` of the `cap` today, the watchers' backlog waiting for it, and what the
    * re-check fetched today. */
  final case class Shared(left: Int, cap: Int, backlogPending: Int, fetchedToday: Int)

  /** How many items this pass may try: within the day's cap (after `checkedToday`) and the pass's; and,
    * under a shared allowance, within what the watchers' backlog leaves of it (the backlog and the watchers'
    * new finds come first) and within the re-check's share of the cap — each try may fetch one work. */
  def allowance(s: Settings, checkedToday: Int, shared: Option[Shared]): Int =
    val own = math.min(math.max(0, s.perDay) - math.max(0, checkedToday), math.max(0, s.perPass))
    val room = shared.fold(Int.MaxValue) { sh =>
      val share = math.max(0, sh.cap) * math.max(0, math.min(100, s.sharePercent)) / 100
      math.min(sh.left - math.max(0, sh.backlogPending), share - math.max(0, sh.fetchedToday))
    }
    math.max(0, math.min(own, room))

  /** Is a pass due: never run (since the start), or `passMinutes` passed since the last. */
  def passDue(last: Option[Instant], passMinutes: Int, now: Instant): Boolean =
    last.forall(l => !l.plusSeconds(math.max(1, passMinutes).toLong * 60L).isAfter(now))

  /** The fetcher generation: one short digest of what the legal chain is — its fetchers in order, and each
    * one's plugin and settings (secrets as references) — so a fetcher or a key added changes it. */
  def generation(parts: List[String]): String =
    val md = java.security.MessageDigest.getInstance("SHA-256")
    md.digest(parts.mkString("\n").getBytes(StandardCharsets.UTF_8)).take(8).map(b => f"${b & 0xff}%02x").mkString

  /** The earliest time one of the checked items falls due again (None: none waits). */
  def nextDue(checks: List[LastCheck], generation: String, everyDays: Int, retryHours: Int): Option[Instant] =
    checks.flatMap(c => dueAt(Some(c), generation, everyDays, retryHours)).minOption

  // ── the re-check job ─────────────────────────────────────────────────────────────────────────────

  /** An open item that names a DOI, as a pass or a job orders it: its id and its last check. */
  final case class Candidate(item: Int, last: Option[LastCheck])

  /** The order items are tried in: the never-checked first, then the oldest check, then the item id. */
  def order(candidates: List[Candidate]): List[Candidate] =
    candidates.sortBy(c => (c.last.isDefined, c.last.fold(0L)(_.at.toEpochMilli), c.item))

  /** A re-check JOB: a person's pass over many items, run past the request. `all` = every open item that
    * names a DOI, due or not and past every cap (`upTo` unused); else at most `upTo` due ones (the caps as
    * they stood when it started). `since` = when it first started: an item checked since then was checked
    * by it, so a job resumed after a restart or a drain skips it. */
  final case class JobPlan(all: Boolean, upTo: Int, since: Instant)

  /** Was this item checked by a job that started at `since` (or since then by anything else)? */
  def checkedSince(last: Option[LastCheck], since: Instant): Boolean = last.exists(l => !l.at.isBefore(since))

  /** What a job — or its resumption — still tries, in [[order]]: the items not checked since it started;
    * for a capped job only the `due` ones, and no more than its `upTo` less the `triedByJob` already. */
  def jobPicks(plan: JobPlan, candidates: List[Candidate], triedByJob: Int, due: Candidate => Boolean): List[Candidate] =
    val left = order(candidates.filterNot(c => checkedSince(c.last, plan.since)))
    if plan.all then left else left.filter(due).take(math.max(0, plan.upTo - math.max(0, triedByJob)))

  /** The end of a job stopped while the autopilot drains: a later managers' tick resumes it. */
  val StoppedDraining = "stopped while the autopilot drains: it resumes once the drain is lifted"

  /** Does a later job take up where this one stopped? Only one a restart (`interrupted`) or the drain
    * stopped; one that finished, or failed for another reason, is over. */
  def resumable(status: String, error: Option[String]): Boolean =
    status == JobState.Failed.wire &&
      error.exists(e => e == JobState.Interrupted || e == StoppedDraining)

  /** What a job did so far: of the `total` items it tries, how many it `tried`, and what they found; the
    * last item that got no answer, why (the job goes on with the next). */
  final case class Tally(total: Int, tried: Int = 0, closed: Int = 0, fetched: Int = 0, noCopy: Int = 0, failed: Int = 0,
      lastFailure: Option[String] = None):
    def add(outcome: Outcome, detail: String): Tally = outcome match
      case Outcome.Closed  => copy(closed = closed + 1)
      case Outcome.Fetched => copy(tried = tried + 1, fetched = fetched + 1)
      case Outcome.NoCopy  => copy(tried = tried + 1, noCopy = noCopy + 1)
      case Outcome.Failed  => copy(tried = tried + 1, failed = failed + 1, lastFailure = Some(detail))

  /** The job's progress: `120/453 items · 3 closed · 9 fetched · 98 no legal copy · 10 failed (HTTP 429 …)`. */
  def progressLine(t: Tally): String =
    val why = t.lastFailure.map(_.trim).filter(_.nonEmpty).map(w => if w.length > 60 then w.take(60).trim + " …" else w)
    s"${t.tried}/${t.total} items · ${t.closed} closed · ${t.fetched} fetched · ${t.noCopy} no legal copy · " +
      s"${t.failed} failed" + why.fold("")(w => s" ($w)")
