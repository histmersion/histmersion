package com.histmersion.domain.automation.managers

import com.histmersion.api.automation.Agents.{JobKind, PauseReason}
import com.histmersion.api.automation.Managers.*
import com.histmersion.domain.automation.managers.ManagerRepo
import com.histmersion.domain.automation.AutomationState
import com.histmersion.util.database.Transactor
import com.histmersion.util.jobs.{JobRuns, JobRunState}
import hearth.kindlings.optics.syntax.*

import java.time.{Clock, Instant, ZoneOffset}
import java.util.concurrent.atomic.AtomicReference
import com.histmersion.domain.automation.Goal

/** The process managers as the engine's other features reach them: a manager and its settings, its items (added, held
  * and released for the focus), its events, and the few reads over every manager at once that the autopilot, the focus,
  * the scheduler and the digest make.
  */
trait ManagerService:
  def get(id: String): Option[ManagerInfo]

  /** Every manager, the oldest first. */
  def list(): List[ManagerInfo]

  /** A manager's pending items, the most urgent first. */
  def pending(managerId: String): List[ManagerItem]

  /** A manager's first `limit` items, the most urgent first: active, pending, held, then the rest; by priority, the
    * earliest not-before (none first), the latest change, the id.
    */
  def items(managerId: String, limit: Int): List[ManagerItem]

  /** Every manager without its counts, by id. */
  def light(): List[ManagerLight]

  /** A new manager; false when the id is taken. */
  def create(
      id: String,
      kind: Kind,
      goal: Option[String],
      state: State,
      config: ManagerConfig,
      cron: Option[String] = None,
      nextTick: Option[Instant] = None
  ): Boolean
  def setState(id: String, state: State, pauseRuns: Boolean): Boolean

  /** Its settings and cron; `next` = when the cron fires next. */
  def setConfig(id: String, config: ManagerConfig, cron: Option[String], next: Option[Instant]): Boolean

  /** Add an item, or bring an existing one back (`reopen`: a settled one pending again; the priority raised): its id,
    * and whether it is new.
    */
  def upsert(
      managerId: String,
      kind: String,
      target: String,
      origin: ItemOrigin,
      priority: Int,
      reopen: Boolean,
      initial: ItemState = ItemState()
  ): (Long, Boolean)
  def item(id: Long): Option[ManagerItem]
  def itemFor(managerId: String, kind: String, target: String): Option[ManagerItem]

  /** The open items of any manager on `target`, the newest first. */
  def openFor(target: String): List[ManagerItem]

  /** A manager's open items as (kind, target). */
  def openKeys(managerId: String): List[(String, String)]

  /** How many of a manager's items of these kinds are in these states (`focusHeld`: one the focus holds counts as
    * held).
    */
  def openOf(managerId: String, kinds: List[String], statuses: List[String], focusHeld: Boolean = true): Int
  def event(managerId: String, itemId: Option[Long], runId: Option[String], kind: String, text: String): Unit

  /** A manager's newest event of `kind`. */
  def lastEventOf(managerId: String, kind: String): Option[ManagerEvent]

  /** Every manager's events of `kind` since `since`. */
  def countEventsAll(kind: String, since: Instant): Int

  /** Per watcher: its ingest backlog's (pending, held) items. */
  def backlogCounts(): Map[String, (Int, Int)]

  /** Every pending item of the ingest backlog of an active watcher, the most urgent first (priority, then the oldest).
    */
  def backlogPending(): List[ManagerItem]

  /** The open (pending, held, active) items of every watcher's ingest backlog, the oldest first. */
  def backlogOpen(): List[ManagerItem]

  /** The open backlog item of any watcher for `doi`, the oldest. */
  def backlogOpenFor(doi: String): Option[ManagerItem]

  /** A pending backlog item tried again later: one more attempt, its failures, not before `notBefore`, why; false = it
    * was not pending.
    */
  def backlogRetry(itemId: Long, failures: Int, notBefore: Instant, outcome: String): Boolean

  /** Per manager: its events of `kind` since `since`. */
  def countEventsBy(kind: String, since: Instant): Map[String, Int]

  /** An item's state replaced while it is in one of `from`; false = it was not. */
  def setItemState(itemId: Long, state: ItemState, from: List[ItemStatus]): Boolean

  /** The open acquisition items a watcher's sweep created, found by the run that picked their DOI. */
  def sweepAcquireItems(): List[SweepAcquireItem]

  /** Per manager: its open items, the items it settled since `since`, and its last event's time (the backlog not
    * counted).
    */
  def pace(since: Instant): Map[String, (Int, Int, Option[String])]

  /** A person's (or a hint's) hold of a pending or held item; false = it was neither. */
  def hold(itemId: Long, h: Hold): Boolean

  /** A held item pending again; false = it was not held. */
  def release(itemId: Long): Boolean

  /** Settle an item that is in one of `from`; false = it was not. */
  def settle(itemId: Long, status: ItemStatus, outcome: String, from: List[ItemStatus]): Boolean

  /** A pending item active with run `run`, one more attempt, `state` its state; false = it was not pending. */
  def dispatch(itemId: Long, run: String, state: ItemState): Boolean

  /** Hold a pending item for the focus; false = it was not pending. */
  def focusHold(itemId: Long, h: Hold): Boolean

  /** Release the focus's holds among `ids`; how many. */
  def focusRelease(ids: List[Long]): Int

  /** Release every focus hold; how many. */
  def focusReleaseAll(): Int
  def focusHeldCount(): Int

  /** Items that wait for a person and hold no place. */
  def forPerson(): Int

  /** Per goal: when it last progressed (an ISO instant). */
  def lastProgress(): Map[String, String]

  /** Every agent run that occupies a place: its pipeline and its manager's goal. */
  def occupyingRuns(): List[(String, Option[String])]

  /** Per goal: its items a person holds. */
  def personHeldByGoal(): Map[String, Int]

  /** The item kinds of the managers' last `n` runs, newest first. */
  def recentKinds(n: Int): List[String]

  /** A manager's runs that occupy a place. */
  def running(id: String): Int

  /** Per goal: its items whose run is queued or running. */
  def runningByGoal(): Map[String, Int]

  /** Per goal: its watcher's settled acquisition sweep's outcome. */
  def sweepOutcomes(): Map[String, String]

object ManagerService:
  def apply(db: Transactor): ManagerService = Implementation(db)

  final private class Implementation(db: Transactor) extends ManagerService:
    def get(id: String): Option[ManagerInfo] = db.read(ManagerRepo.get(id))
    def list(): List[ManagerInfo] = db.read(ManagerRepo.list())
    def pending(managerId: String): List[ManagerItem] = db.read(ManagerRepo.pending(managerId))
    def items(managerId: String, limit: Int): List[ManagerItem] =
      db.read(ManagerRepo.itemPage(managerId, None, 0, limit))._1
    def light(): List[ManagerLight] = db.read(ManagerRepo.light())
    def create(
        id: String,
        kind: Kind,
        goal: Option[String],
        state: State,
        config: ManagerConfig,
        cron: Option[String],
        nextTick: Option[Instant]
    ): Boolean = db.transact(ManagerRepo.create(id, kind, goal, state, config, cron, nextTick))
    def setState(id: String, state: State, pauseRuns: Boolean): Boolean =
      db.transact(ManagerRepo.setState(id, state, pauseRuns))
    def setConfig(id: String, config: ManagerConfig, cron: Option[String], next: Option[Instant]): Boolean =
      db.transact(ManagerRepo.setConfig(id, config, cron, next))
    def upsert(
        managerId: String,
        kind: String,
        target: String,
        origin: ItemOrigin,
        priority: Int,
        reopen: Boolean,
        initial: ItemState
    ): (Long, Boolean) = db.transact(ManagerRepo.upsert(managerId, kind, target, origin, priority, reopen, initial))
    def item(id: Long): Option[ManagerItem] = db.read(ManagerRepo.item(id))
    def itemFor(managerId: String, kind: String, target: String): Option[ManagerItem] =
      db.read(ManagerRepo.itemFor(managerId, kind, target))
    def openFor(target: String): List[ManagerItem] = db.read(ManagerRepo.openFor(target))
    def openKeys(managerId: String): List[(String, String)] = db.read(ManagerRepo.openKeys(managerId))
    def openOf(managerId: String, kinds: List[String], statuses: List[String], focusHeld: Boolean): Int =
      db.read(ManagerRepo.openOf(managerId, kinds, statuses, focusHeld))
    def event(managerId: String, itemId: Option[Long], runId: Option[String], kind: String, text: String): Unit =
      db.transact(ManagerRepo.event(managerId, itemId, runId, kind, text))
    def lastEventOf(managerId: String, kind: String): Option[ManagerEvent] =
      db.read(ManagerRepo.lastEventOf(managerId, kind))
    def countEventsAll(kind: String, since: Instant): Int = db.read(ManagerRepo.countEventsAll(kind, since))
    def backlogCounts(): Map[String, (Int, Int)] = db.read(ManagerRepo.backlogCounts())
    def backlogPending(): List[ManagerItem] = db.read(ManagerRepo.backlogPendingIn())
    def backlogOpen(): List[ManagerItem] = db.read(ManagerRepo.backlogOpenIn())
    def backlogOpenFor(doi: String): Option[ManagerItem] = db.read(ManagerRepo.backlogOpenForIn(doi))
    def backlogRetry(itemId: Long, failures: Int, notBefore: Instant, outcome: String): Boolean =
      db.transact(ManagerRepo.backlogRetryIn(itemId, failures, notBefore, outcome))
    def countEventsBy(kind: String, since: Instant): Map[String, Int] =
      db.read(ManagerRepo.countEventsByIn(kind, since))
    def setItemState(itemId: Long, state: ItemState, from: List[ItemStatus]): Boolean =
      db.transact(ManagerRepo.setItemStateIn(itemId, state, from))
    def sweepAcquireItems(): List[SweepAcquireItem] = db.read(ManagerRepo.sweepAcquireItemsIn())
    def pace(since: Instant): Map[String, (Int, Int, Option[String])] = db.read(ManagerRepo.pace(since))
    def hold(itemId: Long, h: Hold): Boolean = db.transact(ManagerRepo.hold(itemId, h))
    def release(itemId: Long): Boolean = db.transact(ManagerRepo.release(itemId))
    def settle(itemId: Long, status: ItemStatus, outcome: String, from: List[ItemStatus]): Boolean =
      db.transact(ManagerRepo.settle(itemId, status, outcome, from))
    def dispatch(itemId: Long, run: String, state: ItemState): Boolean =
      db.transact(ManagerRepo.dispatchIn(itemId, run, state))
    def focusHold(itemId: Long, h: Hold): Boolean = db.transact(ManagerRepo.focusHold(itemId, h))
    def focusRelease(ids: List[Long]): Int = db.transact(ManagerRepo.focusRelease(ids))
    def focusReleaseAll(): Int = db.transact(ManagerRepo.focusReleaseAll())
    def focusHeldCount(): Int = db.read(ManagerRepo.focusHeldCount())
    def forPerson(): Int = db.read(ManagerRepo.forPerson())
    def lastProgress(): Map[String, String] = db.read(ManagerRepo.lastProgress())
    def occupyingRuns(): List[(String, Option[String])] = db.read(ManagerRepo.occupyingRuns())
    def personHeldByGoal(): Map[String, Int] = db.read(ManagerRepo.personHeldByGoal())
    def recentKinds(n: Int): List[String] = db.read(ManagerRepo.recentKinds(n))
    def running(id: String): Int = db.read(ManagerRepo.running(id))
    def runningByGoal(): Map[String, Int] = db.read(ManagerRepo.runningByGoal())
    def sweepOutcomes(): Map[String, String] = db.read(ManagerRepo.sweepOutcomes())

  /** The managers in memory, with the runs they dispatched (the jobs area's in-memory runs) and the shared state's
    * steps and goal links; `clock` stands in for the database's `now()`.
    */
  final class InMemory(state: AtomicReference[AutomationState], jobs: JobRuns.InMemory, clock: Clock)
      extends ManagerService:
    private def now: Instant = Instant.now(clock)
    private def text(at: Instant): String = AutomationState.text(at)
    private def managers: Vector[ManagerRecord] = state.get.managers
    private def items: Vector[ManagerItemRecord] = state.get.items
    private def events: Vector[ManagerEventRecord] = state.get.events
    private def runs: List[JobRunState] = jobs.all.filter(_.row.kind == JobKind)
    private def manager(id: String): Option[ManagerRecord] = managers.find(_.id == id)
    private val Open = Set("pending", "held", "active")
    private val Settled = Set("done", "stopped", "failed", "cancelled")
    private def focusHeld(i: ManagerItemRecord): Boolean = i.status == "held" && i.hold.exists(_.focus.isDefined)
    private def personHeld(i: ManagerItemRecord): Boolean =
      i.status == "held" && i.hold.forall(h => h.focus.isEmpty && h.untilApproval.isEmpty)
    private def occupying(r: JobRunState): Boolean =
      r.row.status == "queued" || r.row.status == "running" ||
        (r.row.status == "paused" && (r.resumeAt.isDefined || r.error.exists(e =>
          e.startsWith(s"${PauseReason.Allowance.wire}:") || e.startsWith(s"${PauseReason.PlanQuota.wire}:")
        )))
    private def runOf(i: ManagerItemRecord): Option[JobRunState] = i.runId.flatMap(jobs.get)
    private def goalOf(i: ManagerItemRecord): Option[String] = manager(i.managerId).flatMap(_.goal)
    private def view(e: ManagerEventRecord): ManagerEvent =
      ManagerEvent(e.id, e.managerId, e.itemId, e.runId, text(e.at), e.kind, e.text)
    private def view(i: ManagerItemRecord): ManagerItem =
      ManagerItem(
        i.id,
        i.managerId,
        i.kind,
        i.target,
        i.status,
        i.priority,
        i.attempts,
        i.failures,
        i.runId,
        i.evaluatedRun,
        i.notBefore.map(text),
        i.hold,
        i.state,
        i.outcome,
        i.origin,
        text(i.createdAt),
        text(i.updatedAt)
      )
    private def counted[K](ks: Iterable[K]): Map[K, Int] = ks.groupBy(identity).view.mapValues(_.size).toMap

    def get(id: String): Option[ManagerInfo] =
      manager(id).map { m =>
        val day = now.atZone(ZoneOffset.UTC).toLocalDate.atStartOfDay(ZoneOffset.UTC).toInstant
        val mine = runs.filter(_.row.managerId.contains(id)).map(_.id).toSet
        val today = state.get.steps.filter(s => mine(s.jobId) && !s.finishedAt.isBefore(day)).map(_.step)
        ManagerInfo(
          m.id,
          m.kind,
          m.goal,
          m.state,
          m.pauseRuns,
          m.config,
          ManagerConfig(),
          m.statusNote,
          m.cron,
          m.nextTickAt.map(text),
          text(m.createdAt),
          text(m.updatedAt),
          ItemCounts.of(counted(items.filter(_.managerId == id).map(_.status)).toList),
          runs.count(r => r.row.managerId.contains(id) && Set("queued", "running", "paused", "waiting")(r.row.status)),
          events.filter(_.managerId == id).maxByOption(_.id).map(view),
          today.map(_.costUsd).sum.toDouble,
          today.map(s => s.usage.input + s.usage.output).sum
        )
      }
    def list(): List[ManagerInfo] = managers.sortBy(m => (m.createdAt, m.id)).toList.flatMap(m => get(m.id))
    def pending(managerId: String): List[ManagerItem] =
      items
        .filter(i => i.managerId == managerId && i.status == "pending")
        .sortBy(i => (-i.priority, i.id))
        .toList
        .map(view)
    private def urgency(status: String): Int = status match
      case "active"  => 0
      case "pending" => 1
      case "held"    => 2
      case _         => 3
    def items(managerId: String, limit: Int): List[ManagerItem] =
      items
        .filter(_.managerId == managerId)
        .sortBy(i =>
          (
            urgency(i.status),
            -i.priority,
            i.notBefore.fold(Long.MinValue)(_.toEpochMilli),
            -i.updatedAt.toEpochMilli,
            i.id
          )
        )
        .take(math.max(1, limit))
        .toList
        .map(view)
    def light(): List[ManagerLight] =
      managers
        .sortBy(_.id)
        .toList
        .map(m =>
          ManagerLight(
            m.id,
            m.kind,
            m.goal,
            m.state,
            m.cron,
            m.config,
            m.statusNote,
            items.exists(i => i.managerId == m.id && i.kind == PlanGoalItem)
          )
        )
    def create(
        id: String,
        kind: Kind,
        goal: Option[String],
        state: State,
        config: ManagerConfig,
        cron: Option[String],
        nextTick: Option[Instant]
    ): Boolean =
      val at = now
      !this.state
        .getAndUpdate(s =>
          if s.managers.exists(_.id == id) then s
          else
            s.modify(_.managers)
              .using(_ :+ ManagerRecord(id, kind.wire, goal, state.wire, false, config, None, cron, nextTick, at, at))
        )
        .managers
        .exists(_.id == id)
    private def changeManager(id: String)(f: ManagerRecord => ManagerRecord): Boolean =
      state
        .getAndUpdate(_.modify(_.managers).using(_.map(m => if m.id == id then f(m) else m)))
        .managers
        .exists(_.id == id)
    def setState(id: String, state: State, pauseRuns: Boolean): Boolean =
      val at = now
      changeManager(id)(
        _.modify(_.state).setTo(state.wire).modify(_.pauseRuns).setTo(pauseRuns).modify(_.updatedAt).setTo(at)
      )
    def setConfig(id: String, config: ManagerConfig, cron: Option[String], next: Option[Instant]): Boolean =
      val at = now
      changeManager(id)(
        _.modify(_.config)
          .setTo(config)
          .modify(_.cron)
          .setTo(cron)
          .modify(_.nextTickAt)
          .setTo(next)
          .modify(_.updatedAt)
          .setTo(at)
      )
    def upsert(
        managerId: String,
        kind: String,
        target: String,
        origin: ItemOrigin,
        priority: Int,
        reopen: Boolean,
        initial: ItemState
    ): (Long, Boolean) =
      val at = now
      var result = (0L, false)
      val _ = state.updateAndGet { s =>
        s.items.find(i => i.managerId == managerId && i.kind == kind && i.target == target) match
          case None =>
            val id = s.lastItemId + 1
            result = (id, true)
            val made = ManagerItemRecord(
              id,
              managerId,
              kind,
              target,
              ItemStatus.Pending.wire,
              priority,
              0,
              0,
              None,
              None,
              None,
              None,
              initial,
              None,
              origin.wire,
              at,
              at
            )
            s.modify(_.items).using(_ :+ made).modify(_.lastItemId).setTo(id)
          case Some(old) =>
            result = (old.id, false)
            if !reopen then s
            else
              val back = Settled(old.status)
              val again = old
                .modify(_.status)
                .using(st => if back then ItemStatus.Pending.wire else st)
                .modify(_.attempts)
                .using(n => if back then 0 else n)
                .modify(_.failures)
                .using(n => if back then 0 else n)
                .modify(_.notBefore)
                .using(nb => if back then None else nb)
                .modify(_.outcome)
                .using(o => if back then None else o)
                .modify(_.priority)
                .using(math.max(_, priority))
                .modify(_.origin)
                .setTo(origin.wire)
                .modify(_.updatedAt)
                .setTo(at)
              s.modify(_.items).using(_.map(i => if i.id == old.id then again else i))
      }
      result
    def item(id: Long): Option[ManagerItem] = items.find(_.id == id).map(view)
    def itemFor(managerId: String, kind: String, target: String): Option[ManagerItem] =
      items.find(i => i.managerId == managerId && i.kind == kind && i.target == target).map(view)
    def openFor(target: String): List[ManagerItem] =
      items.filter(i => i.target == target && Open(i.status)).sortBy(-_.id).toList.map(view)
    def openKeys(managerId: String): List[(String, String)] =
      items.filter(i => i.managerId == managerId && Open(i.status)).toList.map(i => (i.kind, i.target))
    def openOf(managerId: String, kinds: List[String], statuses: List[String], focusHeld: Boolean): Int =
      items.count(i =>
        i.managerId == managerId && kinds.contains(i.kind) && statuses.contains(i.status) &&
          (focusHeld || !this.focusHeld(i))
      )
    def event(managerId: String, itemId: Option[Long], runId: Option[String], kind: String, text: String): Unit =
      val at = now
      val _ = state.updateAndGet { s =>
        val id = s.lastEventId + 1
        s.modify(_.events)
          .using(_ :+ ManagerEventRecord(id, managerId, itemId, runId, at, kind, text.take(2000)))
          .modify(_.lastEventId)
          .setTo(id)
      }
    def lastEventOf(managerId: String, kind: String): Option[ManagerEvent] =
      events.filter(e => e.managerId == managerId && e.kind == kind).maxByOption(_.id).map(view)
    def countEventsAll(kind: String, since: Instant): Int =
      events.count(e => e.kind == kind && !e.at.isBefore(since))
    def backlogCounts(): Map[String, (Int, Int)] =
      items
        .filter(i => i.kind == OaIngest && (i.status == "pending" || i.status == "held"))
        .groupBy(_.managerId)
        .view
        .mapValues(is => (is.count(_.status == "pending"), is.count(_.status == "held")))
        .toMap
    private def backlog(i: ManagerItemRecord): Boolean = i.kind == OaIngest && Open(i.status)
    def backlogPending(): List[ManagerItem] =
      val active = managers.filter(m => m.kind == Kind.Watch.wire && m.state == State.Active.wire).map(_.id).toSet
      items
        .filter(i => i.kind == OaIngest && i.status == "pending" && active(i.managerId))
        .sortBy(i => (-i.priority, i.id))
        .toList
        .map(view)
    def backlogOpen(): List[ManagerItem] = items.filter(backlog).sortBy(_.id).toList.map(view)
    def backlogOpenFor(doi: String): Option[ManagerItem] =
      items.filter(i => backlog(i) && i.target == doi).minByOption(_.id).map(view)
    def backlogRetry(itemId: Long, failures: Int, notBefore: Instant, outcome: String): Boolean =
      val at = now
      changeItems(i => i.id == itemId && i.status == "pending")(
        _.modify(_.failures)
          .setTo(failures)
          .modify(_.attempts)
          .using(_ + 1)
          .modify(_.notBefore)
          .setTo(Some(notBefore))
          .modify(_.outcome)
          .setTo(Some(outcome))
          .modify(_.updatedAt)
          .setTo(at)
      ) > 0
    def countEventsBy(kind: String, since: Instant): Map[String, Int] =
      counted(events.filter(e => e.kind == kind && !e.at.isBefore(since)).map(_.managerId))
    def setItemState(itemId: Long, state: ItemState, from: List[ItemStatus]): Boolean =
      val at = now
      changeItems(i => i.id == itemId && from.exists(_.wire == i.status))(
        _.modify(_.state).setTo(state).modify(_.updatedAt).setTo(at)
      ) > 0

    /** The acquisition items are the work lists', which this state does not hold: none. */
    def sweepAcquireItems(): List[SweepAcquireItem] = Nil

    def pace(since: Instant): Map[String, (Int, Int, Option[String])] =
      managers.map { m =>
        val mine = items.filter(i => i.managerId == m.id && i.kind != OaIngest)
        m.id -> (
          mine.count(i => Open(i.status)),
          mine.count(i => Set("done", "stopped", "failed")(i.status) && !i.updatedAt.isBefore(since)),
          events.filter(_.managerId == m.id).map(_.at).maxOption.map(text)
        )
      }.toMap
    private def changeItems(which: ManagerItemRecord => Boolean)(f: ManagerItemRecord => ManagerItemRecord): Int =
      state.getAndUpdate(_.modify(_.items).using(_.map(i => if which(i) then f(i) else i))).items.count(which)
    private def released(at: Instant)(i: ManagerItemRecord): ManagerItemRecord =
      i.modify(_.status).setTo(ItemStatus.Pending.wire).modify(_.hold).setTo(None).modify(_.updatedAt).setTo(at)
    def hold(itemId: Long, h: Hold): Boolean =
      val at = now
      changeItems(i => i.id == itemId && (i.status == "pending" || i.status == "held"))(
        _.modify(_.status).setTo(ItemStatus.Held.wire).modify(_.hold).setTo(Some(h)).modify(_.updatedAt).setTo(at)
      ) > 0
    def release(itemId: Long): Boolean = changeItems(i => i.id == itemId && i.status == "held")(released(now)) > 0
    def settle(itemId: Long, status: ItemStatus, outcome: String, from: List[ItemStatus]): Boolean =
      val at = now
      changeItems(i => i.id == itemId && from.exists(_.wire == i.status))(
        _.modify(_.status)
          .setTo(status.wire)
          .modify(_.outcome)
          .setTo(Some(outcome))
          .modify(_.hold)
          .setTo(None)
          .modify(_.updatedAt)
          .setTo(at)
      ) > 0
    def dispatch(itemId: Long, run: String, state: ItemState): Boolean =
      val at = now
      changeItems(i => i.id == itemId && i.status == ItemStatus.Pending.wire)(
        _.modify(_.status)
          .setTo(ItemStatus.Active.wire)
          .modify(_.runId)
          .setTo(Some(run))
          .modify(_.attempts)
          .using(_ + 1)
          .modify(_.state)
          .setTo(state)
          .modify(_.updatedAt)
          .setTo(at)
      ) > 0
    def focusHold(itemId: Long, h: Hold): Boolean =
      val at = now
      changeItems(i => i.id == itemId && i.status == "pending")(
        _.modify(_.status).setTo(ItemStatus.Held.wire).modify(_.hold).setTo(Some(h)).modify(_.updatedAt).setTo(at)
      ) > 0
    def focusRelease(ids: List[Long]): Int =
      if ids.isEmpty then 0 else changeItems(i => ids.contains(i.id) && focusHeld(i))(released(now))
    def focusReleaseAll(): Int = changeItems(focusHeld)(released(now))
    def focusHeldCount(): Int = items.count(focusHeld)
    def forPerson(): Int =
      items.count(i =>
        manager(i.managerId).exists(_.state != State.Stopped.wire) &&
          ((i.status == "held" && i.hold.forall(_.focus.isEmpty)) ||
            (i.status == "active" && runOf(i).exists(_.row.status == "waiting")))
      )
    def lastProgress(): Map[String, String] =
      val kinds = Set(Feed.Started, Feed.Settled, Feed.Evaluated, Feed.Ingested, Feed.Swept)
      val fromEvents = events
        .filter(e => kinds(e.kind))
        .flatMap(e => manager(e.managerId).flatMap(_.goal).map(_ -> e.at))
      val fromLinks = state.get.links.filter(_.kind == Goal.LinkKind.Source).map(l => l.goal -> l.createdAt)
      (fromEvents ++ fromLinks).groupBy(_._1).view.mapValues(ts => text(ts.map(_._2).max)).toMap
    def occupyingRuns(): List[(String, Option[String])] =
      runs.filter(occupying).map(r => (r.row.pipeline, r.row.managerId.flatMap(manager).flatMap(_.goal)))
    def personHeldByGoal(): Map[String, Int] = counted(items.filter(personHeld).flatMap(goalOf))
    def recentKinds(n: Int): List[String] =
      runs
        .sortBy(r => (r.createdAt, r.created))
        .reverse
        .flatMap(r => r.row.managerItem.flatMap(id => items.find(_.id == id)).map(_.kind))
        .take(math.max(0, n))
    def running(id: String): Int = runs.count(r => r.row.managerId.contains(id) && occupying(r))
    def runningByGoal(): Map[String, Int] =
      counted(
        items
          .filter(i =>
            i.status == "active" && runOf(i).exists(r => r.row.status == "queued" || r.row.status == "running")
          )
          .flatMap(goalOf)
      )
    def sweepOutcomes(): Map[String, String] =
      items
        .filter(i => i.kind == AcquisitionSweep && Set("done", "stopped", "failed")(i.status))
        .flatMap(i => goalOf(i).map(_ -> i.outcome.getOrElse("")))
        .toMap
