package com.histmersion.domain

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
    * how many items a day and a pass, the minutes between passes, and the share (percent) of the shared
    * daily legal ingest allowance the re-check may take. */
  final case class Settings(enabled: Boolean, everyDays: Int, perDay: Int, perPass: Int, passMinutes: Int,
      sharePercent: Int)

  /** An item's last check: when, and under which fetcher generation ([[generation]]). */
  final case class LastCheck(at: Instant, generation: String)

  /** When an item is next due: never checked, or checked under another fetcher generation (a fetcher or a
    * key was added since) = now (None); else `everyDays` after its check. */
  def dueAt(last: Option[LastCheck], generation: String, everyDays: Int): Option[Instant] =
    last.filter(_.generation == generation).map(_.at.plusSeconds(math.max(1, everyDays).toLong * 86400L))

  def isDue(last: Option[LastCheck], generation: String, everyDays: Int, now: Instant): Boolean =
    dueAt(last, generation, everyDays).forall(!_.isAfter(now))

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
  def nextDue(checks: List[LastCheck], generation: String, everyDays: Int): Option[Instant] =
    checks.flatMap(c => dueAt(Some(c), generation, everyDays)).minOption
