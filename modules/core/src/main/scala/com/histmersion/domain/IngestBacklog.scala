package com.histmersion.domain

import scala.annotation.tailrec
import com.histmersion.api.Plural
import com.histmersion.api.given

import java.time.Instant

/** The watchers' INGEST BACKLOG (docs/research-process-managers.md "As built: the ingest backlog"): the legal
  * open-access works a sweep picked past the day's ingest allowance wait as `oa-ingest` items of the watcher that found
  * them, and are ingested on later days within the same caps — the backlog first, the oldest (highest priority) first,
  * before any new find. Only a work with no legal copy goes to the acquisition list, which is the person's buy/borrow
  * list. The pure rules; the supervisor reads the facts and writes the result.
  */
object IngestBacklog:

  /** How the legal chain says a work has no legal file (`AcquisitionControllers.ingestLegal`): such a work goes to the
    * acquisition list at once; any other failure is tried again later.
    */
  type DrainTakeLeftPart1 = DrainTakeLeftPart1.T
  object DrainTakeLeftPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DrainTakeLeftPart2 = DrainTakeLeftPart2.T
  object DrainTakeLeftPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DrainTakeSh = DrainTakeSh.T
  object DrainTakeSh extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DaysToDrainGoResult = DaysToDrainGoResult.T
  object DaysToDrainGoResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DaysToDrainGoBs = DaysToDrainGoBs.T
  object DaysToDrainGoBs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DaysToDrainGoDay = DaysToDrainGoDay.T
  object DaysToDrainGoDay extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DaysToDrainGoDrainDayResult = DaysToDrainGoDrainDayResult.T
  object DaysToDrainGoDrainDayResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DaysToDrainGoDrainDayRest = DaysToDrainGoDrainDayRest.T
  object DaysToDrainGoDrainDayRest extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DaysToDrainGoDrainDayCur = DaysToDrainGoDrainDayCur.T
  object DaysToDrainGoDrainDayCur extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DaysToDrainGoDrainDayRoom = DaysToDrainGoDrainDayRoom.T
  object DaysToDrainGoDrainDayRoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type IsNoLegalCopyResult = IsNoLegalCopyResult.T
  object IsNoLegalCopyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsNoLegalCopyMessage = IsNoLegalCopyMessage.T
  object IsNoLegalCopyMessage extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SweepAllowanceResult = SweepAllowanceResult.T
  object SweepAllowanceResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SweepAllowanceOwn = SweepAllowanceOwn.T
  object SweepAllowanceOwn extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SweepAllowanceShared = SweepAllowanceShared.T
  object SweepAllowanceShared extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SweepAllowanceOwnBacklog = SweepAllowanceOwnBacklog.T
  object SweepAllowanceOwnBacklog extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SweepAllowanceAllBacklog = SweepAllowanceAllBacklog.T
  object SweepAllowanceAllBacklog extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DrainOwnLeftPart1 = DrainOwnLeftPart1.T
  object DrainOwnLeftPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DrainOwnLeftPart2 = DrainOwnLeftPart2.T
  object DrainOwnLeftPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DrainShared = DrainShared.T
  object DrainShared extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DrainMax = DrainMax.T
  object DrainMax extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceInCorpus = PlaceInCorpus.T
  object PlaceInCorpus extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceAcquire = PlaceAcquire.T
  object PlaceAcquire extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceOpenBacklog = PlaceOpenBacklog.T
  object PlaceOpenBacklog extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceSettledHere = PlaceSettledHere.T
  object PlaceSettledHere extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OnFailureMessage = OnFailureMessage.T
  object OnFailureMessage extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OnFailureFailures = OnFailureFailures.T
  object OnFailureFailures extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OnFailureMaxFailures = OnFailureMaxFailures.T
  object OnFailureMaxFailures extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OnFailureBackoffMinutes = OnFailureBackoffMinutes.T
  object OnFailureBackoffMinutes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OnFailureMaxBackoffMinutes = OnFailureMaxBackoffMinutes.T
  object OnFailureMaxBackoffMinutes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DaysToDrainResult = DaysToDrainResult.T
  object DaysToDrainResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DaysToDrainSharedPerDay = DaysToDrainSharedPerDay.T
  object DaysToDrainSharedPerDay extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DaysToDrainSharedLeftToday = DaysToDrainSharedLeftToday.T
  object DaysToDrainSharedLeftToday extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DaysToDrainHorizon = DaysToDrainHorizon.T
  object DaysToDrainHorizon extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val NoLegalCopy = "no legal open-access copy of"

  def isNoLegalCopy(message: com.histmersion.domain.IngestBacklog.IsNoLegalCopyMessage): com.histmersion.domain.IngestBacklog.IsNoLegalCopyResult = Option(message).exists(_.trim.startsWith(NoLegalCopy))

  /** What a watcher's next sweep may ingest of its NEW finds: what is left of its own allowance (`own`) and of the
    * shared one (`shared`, the autopilot's; None = no shared cap), each after the backlog that drains under it first —
    * the watcher's own pending items (`ownBacklog`) and every drainable watcher's (`allBacklog`). Never negative.
    */
  def sweepAllowance(own: com.histmersion.domain.IngestBacklog.SweepAllowanceOwn, shared: Option[com.histmersion.domain.IngestBacklog.SweepAllowanceShared], ownBacklog: com.histmersion.domain.IngestBacklog.SweepAllowanceOwnBacklog, allBacklog: com.histmersion.domain.IngestBacklog.SweepAllowanceAllBacklog): com.histmersion.domain.IngestBacklog.SweepAllowanceResult =
    val mine = own - math.max(0, ownBacklog)
    val all = shared.fold(Int.MaxValue)(_ - math.max(0, allBacklog))
    math.max(0, math.min(mine, all))

  /** One pending backlog item, as the drain sees it. */
  final case class Entry(id: Entry.Id, managerId: Entry.ManagerId, priority: Entry.Priority, notBefore: Option[Instant])

  object Entry:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ManagerId = ManagerId.T
    object ManagerId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Priority = Priority.T
    object Priority extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The backlog items to ingest now, in order: the due ones (`notBefore` passed), highest priority first, then the
    * oldest; each within what is left of its watcher's own allowance today (`ownLeft`; a watcher missing there —
    * paused, auto-ingest off — drains nothing) and of the shared one (None = no shared cap); at most `max` (one tick's
    * share: an ingest fetches a file, so a tick takes only a few).
    */
  def drain(entries: List[Entry], ownLeft: Map[com.histmersion.domain.IngestBacklog.DrainOwnLeftPart1, com.histmersion.domain.IngestBacklog.DrainOwnLeftPart2], shared: Option[com.histmersion.domain.IngestBacklog.DrainShared], now: Instant, max: com.histmersion.domain.IngestBacklog.DrainMax): List[Entry] =
    val ordered = entries.filter(_.notBefore.forall(!_.isAfter(now))).sortBy(e => (-e.priority, e.id))
    @tailrec def take(rest: List[Entry], acc: Vector[Entry], left: Map[com.histmersion.domain.IngestBacklog.DrainTakeLeftPart1, com.histmersion.domain.IngestBacklog.DrainTakeLeftPart2], sh: Option[com.histmersion.domain.IngestBacklog.DrainTakeSh]): List[Entry] =
      rest match
        case Nil       => acc.toList
        case e :: tail =>
          val mine = left.getOrElse(e.managerId, 0)
          if acc.size >= math.max(0, max) || mine <= 0 || sh.exists(_ <= 0) then take(tail, acc, left, sh)
          else take(tail, acc :+ e, left.updated(e.managerId, mine - 1), sh.map(_ - 1))
    take(ordered, Vector.empty, ownLeft, shared)

  /** Where a legal open-access pick goes when it is not ingested now: nowhere new when it is in the corpus already,
    * onto the open acquisition item that names its DOI (the goal linked to it), into the backlog entry another watcher
    * (or this one) holds for it (this goal added to it), nowhere when this watcher settled one for it before (a person
    * skipped it; an ingested one is in the corpus) — else a new entry.
    */
  enum Placement:
    case InCorpus(citekey: com.histmersion.domain.library.Citekey)
    case JoinAcquire(item: Placement.JoinAcquire.Item)
    case JoinBacklog(item: Placement.JoinBacklog.Item)
    case SettledBefore(item: Placement.SettledBefore.Item)
    case New

  object Placement:
    object JoinAcquire:
      type Item = Item.T
      object Item extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object JoinBacklog:
      type Item = Item.T
      object Item extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object SettledBefore:
      type Item = Item.T
      object Item extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def place(
      inCorpus: Option[com.histmersion.domain.IngestBacklog.PlaceInCorpus],
      acquire: Option[com.histmersion.domain.IngestBacklog.PlaceAcquire],
      openBacklog: Option[com.histmersion.domain.IngestBacklog.PlaceOpenBacklog],
      settledHere: Option[com.histmersion.domain.IngestBacklog.PlaceSettledHere]
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
    case ToAcquire(why: Failure.ToAcquire.Why)
    case Retry(notBefore: Instant, failures: Failure.Retry.Failures, why: Failure.Retry.Why)

  object Failure:
    object ToAcquire:
      type Why = Why.T
      object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object Retry:
      type Failures = Failures.T
      object Failures extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Why = Why.T
      object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def onFailure(
      message: com.histmersion.domain.IngestBacklog.OnFailureMessage,
      failures: com.histmersion.domain.IngestBacklog.OnFailureFailures,
      maxFailures: com.histmersion.domain.IngestBacklog.OnFailureMaxFailures,
      backoffMinutes: com.histmersion.domain.IngestBacklog.OnFailureBackoffMinutes,
      maxBackoffMinutes: com.histmersion.domain.IngestBacklog.OnFailureMaxBackoffMinutes,
      now: Instant
  ): Failure =
    val why = Option(message).map(_.trim).filter(_.nonEmpty).getOrElse("the ingest failed").take(500)
    if isNoLegalCopy(why) then Failure.ToAcquire(why)
    else
      val n = math.max(0, failures) + 1
      if n >= math.max(1, maxFailures) then Failure.ToAcquire(s"$why (${Plural.count(n, "try", "tries")})")
      else Failure.Retry(now.plus(ManagerPolicy.backoff(backoffMinutes, maxBackoffMinutes, n)), n, why)

  /** One watcher's share of the drain: its backlog, its own daily cap, and what is left of it today. */
  final case class Lane(backlog: Lane.Backlog, perDay: Lane.PerDay, leftToday: Lane.LeftToday)

  object Lane:
    type Backlog = Backlog.T
    object Backlog extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PerDay = PerDay.T
    object PerDay extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LeftToday = LeftToday.T
    object LeftToday extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** How many more UTC days the backlogs take to drain at the current allowance: 0 = today, None = never (no cap leaves
    * room). Day by day: each lane takes up to what its own cap leaves (today: `leftToday`, then `perDay`), the largest
    * backlog first, and the shared cap (`sharedPerDay` / `sharedLeftToday`, None = none) truncates the day's total. An
    * estimate: the drain itself goes by priority and age.
    */
  def daysToDrain(
      lanes: List[Lane],
      sharedPerDay: Option[com.histmersion.domain.IngestBacklog.DaysToDrainSharedPerDay],
      sharedLeftToday: Option[com.histmersion.domain.IngestBacklog.DaysToDrainSharedLeftToday],
      horizon: com.histmersion.domain.IngestBacklog.DaysToDrainHorizon = 3650
  ): Option[com.histmersion.domain.IngestBacklog.DaysToDrainResult] =
    @annotation.tailrec
    def go(bs: Vector[com.histmersion.domain.IngestBacklog.DaysToDrainGoBs], day: com.histmersion.domain.IngestBacklog.DaysToDrainGoDay): Option[com.histmersion.domain.IngestBacklog.DaysToDrainGoResult] =
      if bs.forall(_ <= 0) then Some(math.max(0, day - 1))
      else if day > horizon then None
      else
        val caps = lanes.map(l => math.max(0, if day == 0 then l.leftToday else l.perDay)).toVector
        val share = (if day == 0 then sharedLeftToday.orElse(sharedPerDay) else sharedPerDay).map(math.max(0, _))
        val order = bs.indices.sortBy(i => -bs(i))
        @tailrec def drainDay(rest: List[com.histmersion.domain.IngestBacklog.DaysToDrainGoDrainDayRest], cur: Vector[com.histmersion.domain.IngestBacklog.DaysToDrainGoDrainDayCur], room: com.histmersion.domain.IngestBacklog.DaysToDrainGoDrainDayRoom): Vector[com.histmersion.domain.IngestBacklog.DaysToDrainGoDrainDayResult] =
          rest match
            case Nil       => cur
            case i :: tail =>
              val take = math.min(math.min(cur(i), caps(i)), room)
              if take <= 0 then drainDay(tail, cur, room)
              else drainDay(tail, cur.updated(i, cur(i) - take), room - take)
        val next = drainDay(order.toList, bs, share.getOrElse(Int.MaxValue))
        if day > 0 && next == bs then None // nothing drains on a full day: it never will
        else go(next, day + 1)
    val start = lanes.map(l => math.max(0, l.backlog)).toVector
    if start.forall(_ == 0) then Some(0) else go(start, 0)

  // ── the one-shot repair (`agent autopilot repair-acquire`) ─────────────────────────────────────

  /** What the repair knows of one open acquisition item a watcher's sweep created: the work's DOI, whether the sweep's
    * ranking marked it open access, the watcher that ran the sweep (does it exist, does it auto-ingest), and the source
    * holding the DOI now, if any.
    */
  final case class RepairFacts(
      item: RepairFacts.Item,
      doi: com.histmersion.domain.library.Doi,
      isOa: RepairFacts.IsOa,
      watcher: RepairFacts.Watcher,
      watcherExists: RepairFacts.WatcherExists,
      watcherAutoIngest: RepairFacts.WatcherAutoIngest,
      inCorpus: Option[RepairFacts.InCorpus]
  )

  object RepairFacts:
    type Item = Item.T
    object Item extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IsOa = IsOa.T
    object IsOa extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Watcher = Watcher.T
    object Watcher extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WatcherExists = WatcherExists.T
    object WatcherExists extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WatcherAutoIngest = WatcherAutoIngest.T
    object WatcherAutoIngest extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type InCorpus = InCorpus.T
    object InCorpus extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  enum RepairAction:
    /** Into the watcher's ingest backlog; the acquisition item leaves the list. */
    case Move

    /** In the corpus already: the acquisition item leaves the list. */
    case Remove(citekey: com.histmersion.domain.library.Citekey)

    /** It stays on the list, with why. */
    case Keep(why: RepairAction.Keep.Why)

  object RepairAction:
    object Keep:
      type Why = Why.T
      object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def repairAction(f: RepairFacts): RepairAction =
    if !f.isOa then RepairAction.Keep("no legal open-access copy known: it stays on the acquisition list")
    else
      f.inCorpus match
        case Some(ck)                     => RepairAction.Remove(ck)
        case None if !f.watcherExists     => RepairAction.Keep(s"its watcher ${f.watcher} no longer exists")
        case None if !f.watcherAutoIngest => RepairAction.Keep(s"its watcher ${f.watcher} does not auto-ingest")
        case None                         => RepairAction.Move
