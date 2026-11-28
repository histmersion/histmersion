package com.histmersion
package domain

import com.histmersion.domain.managerpolicy.ReviewSettings
import com.histmersion.domain.managerpolicy.Next

import com.histmersion.util.problem.{Problem, Problems}
import com.histmersion.util.problem.Text.{sentence, sentences}
import scala.annotation.tailrec
import com.histmersion.api.Plural
import com.histmersion.api.given

import java.time.Instant
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.library.Doi
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

/** The watchers' INGEST BACKLOG (docs/research-process-managers.md "As built: the ingest backlog"): the legal
  * open-access works a sweep picked past the day's ingest allowance wait as `oa-ingest` items of the watcher that found
  * them, and are ingested on later days within the same caps — the backlog first, the oldest (highest priority) first,
  * before any new find. Only a work with no legal copy goes to the acquisition list, which is the person's buy/borrow
  * list. The pure rules; the supervisor reads the facts and writes the result.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@movesTo(FeaturePlace.LibraryAcquisition)
object IngestBacklog:
  type DaysToDrain = DaysToDrain.T
  object DaysToDrain extends Newtype[Int, Pure]
  type DrainHorizon = DrainHorizon.T
  object DrainHorizon extends Newtype[Int, Pure]
  type DrainLimit = DrainLimit.T
  object DrainLimit extends Newtype[Int, Pure]
  type OwnAllowance = OwnAllowance.T
  object OwnAllowance extends Newtype[Int, Pure]
  type SharedAllowance = SharedAllowance.T
  object SharedAllowance extends Newtype[Int, Pure]
  type SweepAllowance = SweepAllowance.T
  object SweepAllowance extends Newtype[Int, Pure]
  type TotalBacklog = TotalBacklog.T
  object TotalBacklog extends Newtype[Int, Pure]

  /** How an earlier version's legal chain said a work has no legal file, as the text it stored begins: such a stored
    * text reads back as [[Problem.Stored.Unrecognised]] and still counts as no legal copy.
    */
  val NoLegalCopy = "no legal open-access copy of"

  /** Has the legal chain no legal file for the work (`AcquisitionControllers.ingestLegal` refuses with
    * [[Problem.NotFound.Copy]], or an earlier version stored [[NoLegalCopy]]): such a work goes to the acquisition list
    * at once; any other failure is tried again later.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def isNoLegalCopy(message: Problems): Boolean = message.exists {
    case _: Problem.NotFound.Copy            => true
    case stored: Problem.Stored.Unrecognised => stored.sentence.trim.startsWith(NoLegalCopy)
    case _                                   => false
  }

  /** What a watcher's next sweep may ingest of its NEW finds: what is left of its own allowance (`own`) and of the
    * shared one (`shared`, the autopilot's; None = no shared cap), each after the backlog that drains under it first —
    * the watcher's own pending items (`ownBacklog`) and every drainable watcher's (`allBacklog`). Never negative.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def sweepAllowance(
      own: OwnAllowance,
      shared: Option[SharedAllowance],
      ownBacklog: Lane.Backlog,
      allBacklog: TotalBacklog
  ): SweepAllowance =
    val mine = own.value - math.max(0, ownBacklog.value)
    val all = shared.fold(Int.MaxValue)(_.value - math.max(0, allBacklog.value))
    SweepAllowance(math.max(0, math.min(mine, all)))

  /** One pending backlog item, as the drain sees it. */
  final case class Entry(id: Entry.Id, managerId: Entry.ManagerId, priority: Entry.Priority, notBefore: Option[Instant])

  object Entry:
    type Id = Id.T
    object Id extends Newtype[Long, Pure]
    type ManagerId = ManagerId.T
    object ManagerId extends Newtype[String, Pure]
    type Priority = Priority.T
    object Priority extends Newtype[Int, Pure]

  /** The backlog items to ingest now, in order: the due ones (`notBefore` passed), highest priority first, then the
    * oldest; each within what is left of its watcher's own allowance today (`ownLeft`; a watcher missing there —
    * paused, auto-ingest off — drains nothing) and of the shared one (None = no shared cap); at most `max` (one tick's
    * share: an ingest fetches a file, so a tick takes only a few).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def drain(
      entries: List[Entry],
      ownLeft: Map[Entry.ManagerId, Lane.LeftToday],
      shared: Option[SharedAllowance],
      now: Instant,
      max: DrainLimit
  ): List[Entry] =
    val ordered = entries.filter(_.notBefore.forall(!_.isAfter(now))).sortBy(e => (-e.priority.value, e.id.value: Long))
    @tailrec def take(
        rest: List[Entry],
        acc: Vector[Entry],
        left: Map[Entry.ManagerId, Int],
        sh: Option[Int]
    ): List[Entry] =
      rest match
        case Nil       => acc.toList
        case e :: tail =>
          val mine = left.getOrElse(e.managerId, 0)
          if acc.size >= math.max(0, max.value) || mine <= 0 || sh.exists(_ <= 0) then take(tail, acc, left, sh)
          else take(tail, acc :+ e, left.updated(e.managerId, mine - 1), sh.map(_ - 1))
    take(ordered, Vector.empty, ownLeft.view.mapValues(_.value).toMap, shared.map(_.value))

  /** Where a legal open-access pick goes when it is not ingested now: nowhere new when it is in the corpus already,
    * onto the open acquisition item that names its DOI (the goal linked to it), into the backlog entry another watcher
    * (or this one) holds for it (this goal added to it), nowhere when this watcher settled one for it before (a person
    * skipped it; an ingested one is in the corpus) — else a new entry.
    */
  enum Placement derives CanEqual:
    case InCorpus(citekey: Citekey)
    case JoinAcquire(item: Placement.JoinAcquire.Item)
    case JoinBacklog(item: Placement.JoinBacklog.Item)
    case SettledBefore(item: Placement.SettledBefore.Item)
    case New

  object Placement:
    object JoinAcquire:
      type Item = Item.T
      object Item extends Newtype[Int, Pure]
    object JoinBacklog:
      type Item = Item.T
      object Item extends Newtype[Long, Pure]
    object SettledBefore:
      type Item = Item.T
      object Item extends Newtype[Long, Pure]

  def place(
      inCorpus: Option[Citekey],
      acquire: Option[Placement.JoinAcquire.Item],
      openBacklog: Option[Placement.JoinBacklog.Item],
      settledHere: Option[Placement.SettledBefore.Item]
  ): Placement =
    inCorpus
      .map(Placement.InCorpus(_))
      .orElse(acquire.map(Placement.JoinAcquire(_)))
      .orElse(openBacklog.map(Placement.JoinBacklog(_)))
      .orElse(settledHere.map(Placement.SettledBefore(_)))
      .getOrElse(Placement.New)

  /** What a failed ingest of a backlog item leads to: the acquisition list (the legal chain has no file for it, or it
    * failed `maxFailures` times), or another try after the backoff.
    */
  enum Failure:
    case ToAcquire(why: Rationale)
    case Retry(notBefore: Instant, failures: Failure.Retry.Failures, why: Rationale)

  object Failure:
    object Retry:
      type Failures = Failures.T
      object Failures extends Newtype[Int, Pure]

  /** Why the item goes where it goes: the account of the backlog's decision about the ingest's failure. */
  enum Rationale:
    /** The ingest failed with `problems`. */
    case Failed(problems: Problems)

    /** The ingest failed with `problems`, the `tries`th time: the item gives up. */
    case GaveUp(problems: Problems, tries: Plural.Count)

    /** The account as the item's outcome and the manager's feed show it. */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def said: String =
      def words(problems: Problems): String =
        Option(problems)
          .map(_.sentences.map(_.trim).filter(_.nonEmpty).mkString("; "))
          .filter(_.nonEmpty)
          .getOrElse("the ingest failed")
          .take(500)
      this match
        case Failed(problem)        => words(problem)
        case GaveUp(problem, tries) =>
          s"${words(problem)} (${Plural.count(tries, Plural.CountSingular("try"), Plural.CountPlural("tries"))})"

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def onFailure(
      message: Problems,
      failures: Failure.Retry.Failures,
      maxFailures: ReviewSettings.MaxFailures,
      backoffMinutes: ReviewSettings.BackoffMinutes,
      maxBackoffMinutes: ReviewSettings.MaxBackoffMinutes,
      now: Instant
  ): Failure =
    val failed = Rationale.Failed(message)
    if isNoLegalCopy(message) then Failure.ToAcquire(failed)
    else
      val n = math.max(0, failures.value) + 1
      if n >= math.max(1, maxFailures.value) then Failure.ToAcquire(Rationale.GaveUp(message, Plural.CountN(n.toLong)))
      else
        Failure.Retry(
          now.plus(ReviewSettings.backoff(backoffMinutes, maxBackoffMinutes, Next.Retry.Failures(n))),
          Failure.Retry.Failures(n),
          failed
        )

  /** One watcher's share of the drain: its backlog, its own daily cap, and what is left of it today. */
  final case class Lane(backlog: Lane.Backlog, perDay: Lane.PerDay, leftToday: Lane.LeftToday)

  object Lane:
    type Backlog = Backlog.T
    object Backlog extends Newtype[Int, Pure]
    type PerDay = PerDay.T
    object PerDay extends Newtype[Int, Pure]
    type LeftToday = LeftToday.T
    object LeftToday extends Newtype[Int, Pure]

  /** How many more UTC days the backlogs take to drain at the current allowance: 0 = today, None = never (no cap leaves
    * room). Day by day: each lane takes up to what its own cap leaves (today: `leftToday`, then `perDay`), the largest
    * backlog first, and the shared cap (`sharedPerDay` / `sharedLeftToday`, None = none) truncates the day's total. An
    * estimate: the drain itself goes by priority and age.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def daysToDrain(
      lanes: List[Lane],
      sharedPerDay: Option[Lane.PerDay],
      sharedLeftToday: Option[Lane.LeftToday],
      horizon: DrainHorizon = DrainHorizon(3650)
  ): Option[DaysToDrain] =
    @annotation.tailrec
    def go(bs: Vector[Int], day: Int): Option[DaysToDrain] =
      if bs.forall(_ <= 0) then Some(DaysToDrain(math.max(0, day - 1)))
      else if day > horizon.value then None
      else
        val caps = lanes.map(l => math.max(0, if day == 0 then l.leftToday.value else l.perDay.value)).toVector
        val share =
          (if day == 0 then sharedLeftToday.map(_.value).orElse(sharedPerDay.map(_.value))
           else sharedPerDay.map(_.value)).map(math.max(0, _))
        val order = bs.indices.sortBy(i => -bs(i))
        @tailrec def drainDay(rest: List[Int], cur: Vector[Int], room: Int): Vector[Int] =
          rest match
            case Nil       => cur
            case i :: tail =>
              val take = math.min(math.min(cur(i), caps(i)), room)
              if take <= 0 then drainDay(tail, cur, room)
              else drainDay(tail, cur.updated(i, cur(i) - take), room - take)
        val next = drainDay(order.toList, bs, share.getOrElse(Int.MaxValue))
        if day > 0 && next == bs then None // nothing drains on a full day: it never will
        else go(next, day + 1)
    val start = lanes.map(l => math.max(0, l.backlog.value)).toVector
    if start.forall(_ == 0) then Some(DaysToDrain(0)) else go(start, 0)

  // ── the one-shot repair (`agent autopilot repair-acquire`) ─────────────────────────────────────

  /** What the repair knows of one open acquisition item a watcher's sweep created: the work's DOI, whether the sweep's
    * ranking marked it open access, the watcher that ran the sweep (does it exist, does it auto-ingest), and the source
    * holding the DOI now, if any.
    */
  final case class RepairFacts(
      item: RepairFacts.Item,
      doi: Doi,
      isOa: RepairFacts.IsOa,
      watcher: RepairFacts.Watcher,
      watcherExists: RepairFacts.WatcherExists,
      watcherAutoIngest: RepairFacts.WatcherAutoIngest,
      inCorpus: Option[Citekey]
  )

  object RepairFacts:
    type Item = Item.T
    object Item extends Newtype[Int, Pure]
    type IsOa = IsOa.T
    object IsOa extends Newtype[Boolean, Pure]
    type Watcher = Watcher.T
    object Watcher extends Newtype[String, Pure]
    type WatcherExists = WatcherExists.T
    @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
    object WatcherExists extends Newtype[Boolean, Pure]
    type WatcherAutoIngest = WatcherAutoIngest.T
    @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
    object WatcherAutoIngest extends Newtype[Boolean, Pure]

  enum RepairAction:
    /** Into the watcher's ingest backlog; the acquisition item leaves the list. */
    case Move

    /** In the corpus already: the acquisition item leaves the list. */
    case Remove(citekey: Citekey)

    /** It stays on the list, with why. */
    case Keep(why: RepairRationale)

  /** Why an item stays on the acquisition list: the account of the repair's decision, not a failure. */
  enum RepairRationale:
    /** The sweep's ranking did not mark the work open access. */
    case NoLegalCopyKnown

    /** The watcher `watcher` that found it no longer exists. */
    case WatcherGone(watcher: RepairFacts.Watcher)

    /** The watcher `watcher` that found it does not auto-ingest. */
    case WatcherManual(watcher: RepairFacts.Watcher)

    /** The account as the repair's report shows it. */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def said: String = this match
      case NoLegalCopyKnown       => "no legal open-access copy known: it stays on the acquisition list"
      case WatcherGone(watcher)   => s"its watcher $watcher no longer exists"
      case WatcherManual(watcher) => s"its watcher $watcher does not auto-ingest"

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def repairAction(f: RepairFacts): RepairAction =
    if !f.isOa.value then RepairAction.Keep(RepairRationale.NoLegalCopyKnown)
    else
      f.inCorpus match
        case Some(ck)                       => RepairAction.Remove(ck)
        case None if !f.watcherExists.value =>
          RepairAction.Keep(RepairRationale.WatcherGone(f.watcher))
        case None if !f.watcherAutoIngest.value =>
          RepairAction.Keep(RepairRationale.WatcherManual(f.watcher))
        case None => RepairAction.Move
