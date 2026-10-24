package com.histmersion.domain

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import java.time.Instant

/** The watchers' INGEST BACKLOG (docs/research-process-managers.md "As built: the ingest backlog"): the
  * legal open-access works a sweep picked past the day's ingest allowance wait as `oa-ingest` items of
  * the watcher that found them, and are ingested on later days within the same caps — the backlog first,
  * the oldest (highest priority) first, before any new find. Only a work with no legal copy goes to the
  * acquisition list, which is the person's buy/borrow list. The pure rules; the supervisor reads the facts
  * and writes the result. */
object IngestBacklog:

  /** How the legal chain says a work has no legal file (`AcquireHandlers.ingestLegal`): such a work goes
    * to the acquisition list at once; any other failure is tried again later. */
  val NoLegalCopy = "no legal open-access copy of"

  def isNoLegalCopy(message: String): Boolean = Option(message).exists(_.trim.startsWith(NoLegalCopy))

  /** What a watcher's next sweep may ingest of its NEW finds: what is left of its own allowance (`own`)
    * and of the shared one (`shared`, the autopilot's; None = no shared cap), each after the backlog that
    * drains under it first — the watcher's own pending items (`ownBacklog`) and every drainable watcher's
    * (`allBacklog`). Never negative. */
  def sweepAllowance(own: Int, shared: Option[Int], ownBacklog: Int, allBacklog: Int): Int =
    val mine = own - math.max(0, ownBacklog)
    val all  = shared.fold(Int.MaxValue)(_ - math.max(0, allBacklog))
    math.max(0, math.min(mine, all))

  /** One pending backlog item, as the drain sees it. */
  final case class Entry(id: Long, managerId: String, priority: Int, notBefore: Option[Instant])

  /** The backlog items to ingest now, in order: the due ones (`notBefore` passed), highest priority first,
    * then the oldest; each within what is left of its watcher's own allowance today (`ownLeft`; a watcher
    * missing there — paused, auto-ingest off — drains nothing) and of the shared one (None = no shared cap);
    * at most `max` (one tick's share: an ingest fetches a file, so a tick takes only a few). */
  def drain(entries: List[Entry], ownLeft: Map[String, Int], shared: Option[Int], now: Instant, max: Int): List[Entry] =
    val ordered = entries.filter(_.notBefore.forall(!_.isAfter(now))).sortBy(e => (-e.priority, e.id))
    ordered.foldLeft((Vector.empty[Entry], ownLeft, shared)) { case ((acc, left, sh), e) =>
      val mine = left.getOrElse(e.managerId, 0)
      if acc.size >= math.max(0, max) || mine <= 0 || sh.exists(_ <= 0) then (acc, left, sh)
      else (acc :+ e, left.updated(e.managerId, mine - 1), sh.map(_ - 1))
    }._1.toList

  /** Where a legal open-access pick goes when it is not ingested now: nowhere new when it is in the corpus
    * already, onto the open acquisition item that names its DOI (the goal linked to it), into the backlog
    * entry another watcher (or this one) holds for it (this goal added to it), nowhere when this watcher
    * settled one for it before (a person skipped it; an ingested one is in the corpus) — else a new entry. */
  enum Placement:
    case InCorpus(citekey: String)
    case JoinAcquire(item: Int)
    case JoinBacklog(item: Long)
    case SettledBefore(item: Long)
    case New

  def place(inCorpus: Option[String], acquire: Option[Int], openBacklog: Option[Long], settledHere: Option[Long]): Placement =
    inCorpus.map(Placement.InCorpus(_))
      .orElse(acquire.map(Placement.JoinAcquire(_)))
      .orElse(openBacklog.map(Placement.JoinBacklog(_)))
      .orElse(settledHere.map(Placement.SettledBefore(_)))
      .getOrElse(Placement.New)

  /** What a failed ingest of a backlog item leads to: the acquisition list (the legal chain has no file
    * for it, or it failed `maxFailures` times), or another try after the backoff. */
  enum Failure:
    case ToAcquire(why: String)
    case Retry(notBefore: Instant, failures: Int, why: String)

  def onFailure(message: String, failures: Int, maxFailures: Int, backoffMinutes: Int, maxBackoffMinutes: Int,
      now: Instant): Failure =
    val why = Option(message).map(_.trim).filter(_.nonEmpty).getOrElse("the ingest failed").take(500)
    if isNoLegalCopy(why) then Failure.ToAcquire(why)
    else
      val n = math.max(0, failures) + 1
      if n >= math.max(1, maxFailures) then Failure.ToAcquire(s"$why (${Plural.count(n, "try", "tries")})")
      else Failure.Retry(now.plus(ManagerPolicy.backoff(backoffMinutes, maxBackoffMinutes, n)), n, why)

  /** One watcher's share of the drain: its backlog, its own daily cap, and what is left of it today. */
  final case class Lane(backlog: Int, perDay: Int, leftToday: Int)

  /** How many more UTC days the backlogs take to drain at the current allowance: 0 = today, None = never
    * (no cap leaves room). Day by day: each lane takes up to what its own cap leaves (today: `leftToday`,
    * then `perDay`), the largest backlog first, and the shared cap (`sharedPerDay` / `sharedLeftToday`,
    * None = none) truncates the day's total. An estimate: the drain itself goes by priority and age. */
  def daysToDrain(lanes: List[Lane], sharedPerDay: Option[Int], sharedLeftToday: Option[Int], horizon: Int = 3650): Option[Int] =
    @annotation.tailrec
    def go(bs: Vector[Int], day: Int): Option[Int] =
      if bs.forall(_ <= 0) then Some(math.max(0, day - 1))
      else if day > horizon then None
      else
        val caps  = lanes.map(l => math.max(0, if day == 0 then l.leftToday else l.perDay)).toVector
        val share = (if day == 0 then sharedLeftToday.orElse(sharedPerDay) else sharedPerDay).map(math.max(0, _))
        val order = bs.indices.sortBy(i => -bs(i))
        val (next, _) = order.foldLeft((bs, share.getOrElse(Int.MaxValue))) { case ((cur, room), i) =>
          val take = math.min(math.min(cur(i), caps(i)), room)
          if take <= 0 then (cur, room) else (cur.updated(i, cur(i) - take), room - take)
        }
        if day > 0 && next == bs then None // nothing drains on a full day: it never will
        else go(next, day + 1)
    val start = lanes.map(l => math.max(0, l.backlog)).toVector
    if start.forall(_ == 0) then Some(0) else go(start, 0)

  // ── the one-shot repair (`agent autopilot repair-acquire`) ─────────────────────────────────────

  /** What the repair knows of one open acquisition item a watcher's sweep created: the work's DOI, whether
    * the sweep's ranking marked it open access, the watcher that ran the sweep (does it exist, does it
    * auto-ingest), and the source holding the DOI now, if any. */
  final case class RepairFacts(item: Int, doi: String, isOa: Boolean, watcher: String, watcherExists: Boolean,
      watcherAutoIngest: Boolean, inCorpus: Option[String])

  enum RepairAction:
    /** Into the watcher's ingest backlog; the acquisition item leaves the list. */
    case Move
    /** In the corpus already: the acquisition item leaves the list. */
    case Remove(citekey: String)
    /** It stays on the list, with why. */
    case Keep(why: String)

  def repairAction(f: RepairFacts): RepairAction =
    if !f.isOa then RepairAction.Keep("no legal open-access copy known: it stays on the acquisition list")
    else
      f.inCorpus match
        case Some(ck)                     => RepairAction.Remove(ck)
        case None if !f.watcherExists     => RepairAction.Keep(s"its watcher ${f.watcher} no longer exists")
        case None if !f.watcherAutoIngest => RepairAction.Keep(s"its watcher ${f.watcher} does not auto-ingest")
        case None                         => RepairAction.Move
