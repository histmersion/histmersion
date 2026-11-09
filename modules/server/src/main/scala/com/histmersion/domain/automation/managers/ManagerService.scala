package com.histmersion
package domain.automation.managers

import com.histmersion.api.automation.Agents.{JobKind, PauseReason, RunInfo}
import com.histmersion.api.automation.Managers.*
import com.histmersion.domain.automation.managers.ManagerRepo
import com.histmersion.domain.automation.AutomationState
import com.histmersion.util.database.Transactor
import com.histmersion.util.jobs.{JobRuns, JobRunState}
import hearth.kindlings.optics.syntax.*

import java.time.{Clock, Instant, ZoneOffset}
import java.util.concurrent.atomic.AtomicReference
import com.histmersion.domain.automation.{Goal, Manager as DomainManager, Run}
import io.scalaland.chimney.dsl.*
import com.histmersion.domain.library.Doi
import com.histmersion.api.automation.Pipeline

/** The process managers as the engine's other features reach them: a manager and its settings, its items (added, held
  * and released for the focus), its events, and the few reads over every manager at once that the autopilot, the focus,
  * the scheduler and the digest make.
  */
trait ManagerService:
  def get(id: ManagerInfo.Id): Option[ManagerInfo]

  /** Every manager, the oldest first. */
  def list(): List[ManagerInfo]

  /** A manager's pending items, the most urgent first. */
  def pending(managerId: ManagerInfo.Id): List[ManagerItem]

  /** A manager's first `limit` items, the most urgent first: active, pending, held, then the rest; by priority, the
    * earliest not-before (none first), the latest change, the id.
    */
  def items(managerId: ManagerInfo.Id, limit: PageSize): List[ManagerItem]

  /** Every manager without its counts, by id. */
  def light(): List[ManagerLight]

  /** A new manager; false when the id is taken. */
  def create(
      id: ManagerInfo.Id,
      kind: Kind,
      goal: Option[Goal.Slug],
      state: State,
      config: ManagerConfig,
      cron: Option[ManagerInfo.Cron] = None,
      nextTick: Option[Instant] = None
  ): Boolean
  def setState(id: ManagerInfo.Id, state: State, pauseRuns: ManagerInfo.PauseRuns): Boolean

  /** Its settings and cron; `next` = when the cron fires next. */
  def setConfig(
      id: ManagerInfo.Id,
      config: ManagerConfig,
      cron: Option[ManagerInfo.Cron],
      next: Option[Instant]
  ): Boolean

  /** Add an item, or bring an existing one back (`reopen`: a settled one pending again; the priority raised): its id,
    * and whether it is new.
    */
  def upsert(
      managerId: ManagerInfo.Id,
      kind: ManagerItem.Kind,
      target: ManagerItem.Target,
      origin: ItemOrigin,
      priority: ManagerItem.Priority,
      reopen: Boolean,
      initial: ItemState = ItemState()
  ): (ManagerItem.Id, Boolean)
  def item(id: ManagerItem.Id): Option[ManagerItem]
  def itemFor(managerId: ManagerInfo.Id, kind: ManagerItem.Kind, target: ManagerItem.Target): Option[ManagerItem]

  /** The open items of any manager on `target`, the newest first. */
  def openFor(target: ManagerItem.Target): List[ManagerItem]

  /** A manager's open items as (kind, target). */
  def openKeys(managerId: ManagerInfo.Id): List[(ManagerItem.Kind, ManagerItem.Target)]

  /** How many of a manager's items of these kinds are in these states (`focusHeld`: one the focus holds counts as
    * held).
    */
  def openOf(
      managerId: ManagerInfo.Id,
      kinds: List[ManagerItem.Kind],
      statuses: List[ManagerItem.Status],
      focusHeld: Boolean = true
  ): RowCount
  def event(
      managerId: ManagerInfo.Id,
      itemId: Option[ManagerItem.Id],
      runId: Option[Run.Id],
      kind: ManagerEvent.Kind,
      text: ManagerEvent.Text
  ): Unit

  /** A manager's newest event of `kind`. */
  def lastEventOf(managerId: ManagerInfo.Id, kind: ManagerEvent.Kind): Option[ManagerEvent]

  /** Every manager's events of `kind` since `since`. */
  def countEventsAll(kind: ManagerEvent.Kind, since: Instant): RowCount

  /** Per watcher: its ingest backlog's (pending, held) items. */
  def backlogCounts(): Map[ManagerInfo.Id, (RowCount, RowCount)]

  /** Every pending item of the ingest backlog of an active watcher, the most urgent first (priority, then the oldest).
    */
  def backlogPending(): List[ManagerItem]

  /** The open (pending, held, active) items of every watcher's ingest backlog, the oldest first. */
  def backlogOpen(): List[ManagerItem]

  /** The open backlog item of any watcher for `doi`, the oldest. */
  def backlogOpenFor(doi: Doi): Option[ManagerItem]

  /** A pending backlog item tried again later: one more attempt, its failures, not before `notBefore`, why; false = it
    * was not pending.
    */
  def backlogRetry(
      itemId: ManagerItem.Id,
      failures: ManagerItem.Failures,
      notBefore: Instant,
      outcome: ManagerItem.Outcome
  ): Boolean

  /** Per manager: its events of `kind` since `since`. */
  def countEventsBy(kind: ManagerEvent.Kind, since: Instant): Map[ManagerInfo.Id, RowCount]

  /** An item's state replaced while it is in one of `from`; false = it was not. */
  def setItemState(itemId: ManagerItem.Id, state: ItemState, from: List[ItemStatus]): Boolean

  /** The open acquisition items a watcher's sweep created, found by the run that picked their DOI. */
  def sweepAcquireItems(): List[SweepAcquireItem]

  /** Per manager: its open items, the items it settled since `since`, and its last event's time (the backlog not
    * counted).
    */
  def pace(since: Instant): Map[ManagerInfo.Id, (RowCount, RowCount, Option[ManagerRepo.ProgressText])]

  /** A person's (or a hint's) hold of a pending or held item; false = it was neither. */
  def hold(itemId: ManagerItem.Id, h: Hold): Boolean

  /** A held item pending again; false = it was not held. */
  def release(itemId: ManagerItem.Id): Boolean

  /** Settle an item that is in one of `from`; false = it was not. */
  def settle(itemId: ManagerItem.Id, status: ItemStatus, outcome: ManagerItem.Outcome, from: List[ItemStatus]): Boolean

  /** A pending item active with run `run`, one more attempt, `state` its state; false = it was not pending. */
  def dispatch(itemId: ManagerItem.Id, run: Run.Id, state: ItemState): Boolean

  /** Hold a pending item for the focus; false = it was not pending. */
  def focusHold(itemId: ManagerItem.Id, h: Hold): Boolean

  /** Release the focus's holds among `ids`; how many. */
  def focusRelease(ids: List[ManagerItem.Id]): RowCount

  /** Release every focus hold; how many. */
  def focusReleaseAll(): RowCount
  def focusHeldCount(): RowCount

  /** Items that wait for a person and hold no place. */
  def forPerson(): RowCount

  /** Per goal: when it last progressed (an ISO instant). */
  def lastProgress(): Map[com.histmersion.api.automation.Goal.Slug, ManagerRepo.ProgressText]

  /** Every agent run that occupies a place: its pipeline and its manager's goal. */
  def occupyingRuns(): List[(Pipeline.Wire, Option[com.histmersion.api.automation.Goal.Slug])]

  /** Per goal: its items a person holds. */
  def personHeldByGoal(): Map[Goal.Slug, RowCount]

  /** The item kinds of the managers' last `n` runs, newest first. */
  def recentKinds(n: PageSize): List[ManagerItem.Kind]

  /** A manager's runs that occupy a place. */
  def running(id: ManagerInfo.Id): RowCount

  /** Per goal: its items whose run is queued or running. */
  def runningByGoal(): Map[Goal.Slug, RowCount]

  /** Per goal: its watcher's settled acquisition sweep's outcome. */
  def sweepOutcomes(): Map[Goal.Slug, ManagerItem.Outcome]

object ManagerService:
  def apply(db: Transactor): ManagerService = Implementation(db)

  final private class Implementation(db: Transactor) extends ManagerService:
    def get(id: ManagerInfo.Id): Option[ManagerInfo] = db.read(ManagerRepo.get(id))
    def list(): List[ManagerInfo] = db.read(ManagerRepo.list())
    def pending(managerId: ManagerInfo.Id): List[ManagerItem] = db.read(ManagerRepo.pending(managerId))
    def items(managerId: ManagerInfo.Id, limit: PageSize): List[ManagerItem] =
      db.read(ManagerRepo.itemPage(managerId, None, Offset(0), limit))._1
    def light(): List[ManagerLight] = db.read(ManagerRepo.light())
    def create(
        id: ManagerInfo.Id,
        kind: Kind,
        goal: Option[Goal.Slug],
        state: State,
        config: ManagerConfig,
        cron: Option[ManagerInfo.Cron],
        nextTick: Option[Instant]
    ): Boolean = db.transact(
      ManagerRepo.create(
        id,
        kind,
        goal.map(g => com.histmersion.api.automation.Goal.Slug(g.value)),
        state,
        config,
        cron,
        nextTick
      )
    )
    def setState(id: ManagerInfo.Id, state: State, pauseRuns: ManagerInfo.PauseRuns): Boolean =
      db.transact(ManagerRepo.setState(id, state, pauseRuns))
    def setConfig(
        id: ManagerInfo.Id,
        config: ManagerConfig,
        cron: Option[ManagerInfo.Cron],
        next: Option[Instant]
    ): Boolean =
      db.transact(ManagerRepo.setConfig(id, config, cron, next))
    def upsert(
        managerId: ManagerInfo.Id,
        kind: ManagerItem.Kind,
        target: ManagerItem.Target,
        origin: ItemOrigin,
        priority: ManagerItem.Priority,
        reopen: Boolean,
        initial: ItemState
    ): (ManagerItem.Id, Boolean) =
      db.transact(ManagerRepo.upsert(managerId, kind, target, origin, priority, reopen, initial))
    def item(id: ManagerItem.Id): Option[ManagerItem] = db.read(ManagerRepo.item(id))
    def itemFor(managerId: ManagerInfo.Id, kind: ManagerItem.Kind, target: ManagerItem.Target): Option[ManagerItem] =
      db.read(ManagerRepo.itemFor(managerId, kind, target))
    def openFor(target: ManagerItem.Target): List[ManagerItem] = db.read(ManagerRepo.openFor(target))
    def openKeys(managerId: ManagerInfo.Id): List[(ManagerItem.Kind, ManagerItem.Target)] =
      db.read(ManagerRepo.openKeys(managerId))
    def openOf(
        managerId: ManagerInfo.Id,
        kinds: List[ManagerItem.Kind],
        statuses: List[ManagerItem.Status],
        focusHeld: Boolean
    ): RowCount =
      db.read(ManagerRepo.openOf(managerId, kinds, statuses, focusHeld))
    def event(
        managerId: ManagerInfo.Id,
        itemId: Option[ManagerItem.Id],
        runId: Option[Run.Id],
        kind: ManagerEvent.Kind,
        text: ManagerEvent.Text
    ): Unit =
      db.transact(ManagerRepo.event(managerId, itemId, runId.map(r => RunInfo.Id(r.value)), kind, text))
    def lastEventOf(managerId: ManagerInfo.Id, kind: ManagerEvent.Kind): Option[ManagerEvent] =
      db.read(ManagerRepo.lastEventOf(managerId, kind))
    def countEventsAll(kind: ManagerEvent.Kind, since: Instant): RowCount =
      db.read(ManagerRepo.countEventsAll(kind, since))
    def backlogCounts(): Map[ManagerInfo.Id, (RowCount, RowCount)] = db.read(ManagerRepo.backlogCounts())
    def backlogPending(): List[ManagerItem] = db.read(ManagerRepo.backlogPendingIn())
    def backlogOpen(): List[ManagerItem] = db.read(ManagerRepo.backlogOpenIn())
    def backlogOpenFor(doi: Doi): Option[ManagerItem] = db.read(ManagerRepo.backlogOpenForIn(doi))
    def backlogRetry(
        itemId: ManagerItem.Id,
        failures: ManagerItem.Failures,
        notBefore: Instant,
        outcome: ManagerItem.Outcome
    ): Boolean =
      db.transact(ManagerRepo.backlogRetryIn(itemId, failures, notBefore, outcome))
    def countEventsBy(kind: ManagerEvent.Kind, since: Instant): Map[ManagerInfo.Id, RowCount] =
      db.read(ManagerRepo.countEventsByIn(kind, since))
    def setItemState(itemId: ManagerItem.Id, state: ItemState, from: List[ItemStatus]): Boolean =
      db.transact(ManagerRepo.setItemStateIn(itemId, state, from))
    def sweepAcquireItems(): List[SweepAcquireItem] = db.read(ManagerRepo.sweepAcquireItemsIn())
    def pace(since: Instant): Map[ManagerInfo.Id, (RowCount, RowCount, Option[ManagerRepo.ProgressText])] =
      db.read(ManagerRepo.pace(since))
    def hold(itemId: ManagerItem.Id, h: Hold): Boolean = db.transact(ManagerRepo.hold(itemId, h))
    def release(itemId: ManagerItem.Id): Boolean = db.transact(ManagerRepo.release(itemId))
    def settle(
        itemId: ManagerItem.Id,
        status: ItemStatus,
        outcome: ManagerItem.Outcome,
        from: List[ItemStatus]
    ): Boolean =
      db.transact(ManagerRepo.settle(itemId, status, outcome, from))
    def dispatch(itemId: ManagerItem.Id, run: Run.Id, state: ItemState): Boolean =
      db.transact(ManagerRepo.dispatchIn(itemId, run, state))
    def focusHold(itemId: ManagerItem.Id, h: Hold): Boolean = db.transact(ManagerRepo.focusHold(itemId, h))
    def focusRelease(ids: List[ManagerItem.Id]): RowCount = db.transact(ManagerRepo.focusRelease(ids))
    def focusReleaseAll(): RowCount = db.transact(ManagerRepo.focusReleaseAll())
    def focusHeldCount(): RowCount = db.read(ManagerRepo.focusHeldCount())
    def forPerson(): RowCount = db.read(ManagerRepo.forPerson())
    def lastProgress(): Map[com.histmersion.api.automation.Goal.Slug, ManagerRepo.ProgressText] =
      db.read(ManagerRepo.lastProgress())
    def occupyingRuns(): List[(Pipeline.Wire, Option[com.histmersion.api.automation.Goal.Slug])] =
      db.read(ManagerRepo.occupyingRuns())
    def personHeldByGoal(): Map[Goal.Slug, RowCount] = db.read(ManagerRepo.personHeldByGoal())
    def recentKinds(n: PageSize): List[ManagerItem.Kind] = db.read(ManagerRepo.recentKinds(n))
    def running(id: ManagerInfo.Id): RowCount = db.read(ManagerRepo.running(id))
    def runningByGoal(): Map[Goal.Slug, RowCount] = db.read(ManagerRepo.runningByGoal())
    def sweepOutcomes(): Map[Goal.Slug, ManagerItem.Outcome] = db.read(ManagerRepo.sweepOutcomes())

  /** The managers in memory, with the runs they dispatched (the jobs area's in-memory runs) and the shared state's
    * steps and goal links; `clock` stands in for the database's `now()`.
    */
  final class InMemory(state: AtomicReference[AutomationState], jobs: JobRuns.InMemory, clock: Clock)
      extends ManagerService:
    private def now: Instant = Instant.now(clock)
    private def text(at: Instant): String = AutomationState.text(at).value
    private def managers: Vector[ManagerRecord] = state.get.managers
    private def items: Vector[ManagerItemRecord] = state.get.items
    private def events: Vector[ManagerEventRecord] = state.get.events
    private def runs: List[JobRunState] = jobs.all.filter(_.row.kind.value == JobKind)
    private def manager(id: String): Option[ManagerRecord] = managers.find(_.id.value == id)
    private val Open = Set("pending", "held", "active")
    private val Settled = Set("done", "stopped", "failed", "cancelled")
    private def focusHeld(i: ManagerItemRecord): Boolean = i.status.value == "held" && i.hold.exists(_.focus.isDefined)
    private def personHeld(i: ManagerItemRecord): Boolean =
      i.status.value == "held" && i.hold.forall(h => h.focus.isEmpty && h.untilApproval.isEmpty)
    private def occupying(r: JobRunState): Boolean =
      r.row.status.value == "queued" || r.row.status.value == "running" ||
        (r.row.status.value == "paused" && (r.resumeAt.isDefined || r.error.exists(e =>
          e.value.startsWith(s"${PauseReason.Allowance.wire.value}:") ||
            e.value.startsWith(s"${PauseReason.PlanQuota.wire.value}:")
        )))
    private def runOf(i: ManagerItemRecord): Option[JobRunState] = i.runId.flatMap(jobs.get)
    private def goalOf(i: ManagerItemRecord): Option[String] = manager(i.managerId.value).flatMap(_.goal).map(_.value)
    private def viewEvent(e: ManagerEventRecord): ManagerEvent =
      ManagerEvent(
        ManagerEvent.Id(e.id.value),
        ManagerInfo.Id(e.managerId.value),
        e.itemId.map(i => ManagerEvent.ItemId(i.value)),
        e.runId.map(r => RunInfo.Id(r.value)),
        ManagerEvent.At(text(e.at)),
        ManagerEvent.Kind(e.kind.value),
        ManagerEvent.Text(e.text.value)
      )
    private def viewItem(i: ManagerItemRecord): ManagerItem =
      ManagerItem(
        ManagerItem.Id(i.id.value),
        ManagerInfo.Id(i.managerId.value),
        ManagerItem.Kind(i.kind.value),
        ManagerItem.Target(i.target.value),
        ManagerItem.Status(i.status.value),
        ManagerItem.Priority(i.priority.value),
        ManagerItem.Attempts(i.attempts.value),
        ManagerItem.Failures(i.failures.value),
        i.runId.map(r => RunInfo.Id(r.value)),
        i.evaluatedRun.map(r => RunInfo.Id(r.value)),
        i.notBefore.map(t => ManagerItem.NotBefore(text(t))),
        i.hold,
        i.state,
        i.outcome.map(o => ManagerItem.Outcome(o.value)),
        ManagerItem.Origin(i.origin.value),
        ManagerItem.CreatedAt(text(i.createdAt)),
        ManagerItem.UpdatedAt(text(i.updatedAt))
      )
    private def counted[K](ks: Iterable[K]): Map[K, Int] = ks.groupBy(identity).view.mapValues(_.size).toMap

    def get(id: ManagerInfo.Id): Option[ManagerInfo] =
      manager(id.value).map { m =>
        val day = now.atZone(ZoneOffset.UTC).toLocalDate.atStartOfDay(ZoneOffset.UTC).toInstant
        val mine = runs.filter(_.row.managerId.exists(_.value == id.value)).map(r => r.id.value: String).toSet
        val today = state.get.steps.filter(s => mine(s.jobId.value: String) && !s.finishedAt.isBefore(day)).map(_.step)
        ManagerInfo(
          ManagerInfo.Id(m.id.value),
          ManagerInfo.Kind(m.kind.wire.value),
          m.goal.map(g => com.histmersion.api.automation.Goal.Slug(g.value)),
          ManagerInfo.State(m.state.wire.value),
          ManagerInfo.PauseRuns(m.pauseRuns.value),
          m.config,
          ManagerConfig(),
          m.statusNote,
          m.cron.map(c => ManagerInfo.Cron(c.value)),
          m.nextTickAt.map(t => ManagerInfo.NextTickAt(text(t))),
          ManagerInfo.CreatedAt(text(m.createdAt)),
          ManagerInfo.UpdatedAt(text(m.updatedAt)),
          ItemCounts.of(
            items
              .filter(_.managerId.value == id.value)
              .map(_.status.value)
              .groupBy(identity)
              .toList
              .map((status, same) => (ItemStatus.Wire(status), ItemCounts.Count(same.size)))
          ),
          ManagerInfo.InFlight(
            runs.count(r =>
              r.row.managerId.exists(_.value == id.value) &&
                Set("queued", "running", "paused", "waiting")(r.row.status.value)
            )
          ),
          events.filter(_.managerId.value == id.value).maxByOption[Long](_.id.value).map(viewEvent),
          ManagerInfo.SpentTodayUsd(today.map(_.costUsd).sum.toDouble),
          ManagerInfo.TokensToday(today.map(s => s.usage.input.value + s.usage.output.value).sum)
        )
      }
    def list(): List[ManagerInfo] =
      managers.sortBy(m => (m.createdAt, m.id.value)).toList.flatMap(m => get(ManagerInfo.Id(m.id.value)))
    def pending(managerId: ManagerInfo.Id): List[ManagerItem] =
      items
        .filter(i => i.managerId.value == managerId.value && i.status.value == "pending")
        .sortBy[(Int, Long)](i => (-i.priority.value, i.id.value))
        .toList
        .map(viewItem)
    private def urgency(status: String): Int = status match
      case "active"  => 0
      case "pending" => 1
      case "held"    => 2
      case _         => 3
    def items(managerId: ManagerInfo.Id, limit: PageSize): List[ManagerItem] =
      items
        .filter(_.managerId.value == managerId.value)
        .sortBy[(Int, Int, Long, Long, Long)](i =>
          (
            urgency(i.status.value),
            -i.priority.value,
            i.notBefore.fold(Long.MinValue)(_.toEpochMilli),
            -i.updatedAt.toEpochMilli,
            i.id.value
          )
        )
        .take(math.max(1, limit.value))
        .toList
        .map(viewItem)
    def light(): List[ManagerLight] =
      managers
        .sortBy(_.id.value)
        .toList
        .map(m =>
          ManagerLight(
            ManagerLight.Id(m.id.value),
            ManagerLight.Kind(m.kind.wire.value),
            m.goal.map(g => ManagerLight.Goal(g.value)),
            ManagerLight.State(m.state.wire.value),
            m.cron.map(c => ManagerLight.Cron(c.value)),
            m.config,
            m.statusNote.map(n => ManagerLight.StatusNote(n.render)),
            ManagerLight.PlanItem(items.exists(i => i.managerId.value == m.id.value && i.kind.value == PlanGoalItem))
          )
        )
    def create(
        id: ManagerInfo.Id,
        kind: Kind,
        goal: Option[Goal.Slug],
        state: State,
        config: ManagerConfig,
        cron: Option[ManagerInfo.Cron],
        nextTick: Option[Instant]
    ): Boolean =
      val at = now
      !this.state
        .getAndUpdate(s =>
          if s.managers.exists(_.id.value == id.value) then s
          else
            s.modify(_.managers)
              .using(
                _ :+ ManagerRecord(
                  ManagerRecord.Id(id.value),
                  kind.transformInto[DomainManager.Kind],
                  goal.map(g => ManagerRecord.Goal(g.value)),
                  state.transformInto[DomainManager.State],
                  ManagerRecord.PauseRuns(false),
                  config,
                  None,
                  cron.map(c => ManagerRecord.Cron(c.value)),
                  nextTick,
                  at,
                  at
                )
              )
        )
        .managers
        .exists(_.id.value == id.value)
    private def changeManager(id: ManagerInfo.Id)(f: ManagerRecord => ManagerRecord): Boolean =
      state
        .getAndUpdate(_.modify(_.managers).using(_.map(m => if m.id.value == id.value then f(m) else m)))
        .managers
        .exists(_.id.value == id.value)
    def setState(id: ManagerInfo.Id, state: State, pauseRuns: ManagerInfo.PauseRuns): Boolean =
      val at = now
      changeManager(id)(
        _.modify(_.state)
          .setTo(state.transformInto[DomainManager.State])
          .modify(_.pauseRuns)
          .setTo(ManagerRecord.PauseRuns(pauseRuns.value))
          .modify(_.updatedAt)
          .setTo(at)
      )
    def setConfig(
        id: ManagerInfo.Id,
        config: ManagerConfig,
        cron: Option[ManagerInfo.Cron],
        next: Option[Instant]
    ): Boolean =
      val at = now
      changeManager(id)(
        _.modify(_.config)
          .setTo(config)
          .modify(_.cron)
          .setTo(cron.map(c => ManagerRecord.Cron(c.value)))
          .modify(_.nextTickAt)
          .setTo(next)
          .modify(_.updatedAt)
          .setTo(at)
      )
    def upsert(
        managerId: ManagerInfo.Id,
        kind: ManagerItem.Kind,
        target: ManagerItem.Target,
        origin: ItemOrigin,
        priority: ManagerItem.Priority,
        reopen: Boolean,
        initial: ItemState
    ): (ManagerItem.Id, Boolean) =
      val at = now
      var result: (ManagerItem.Id, Boolean) = (ManagerItem.Id(0L), false)
      val _ = state.updateAndGet { s =>
        s.items.find(i =>
          i.managerId.value == managerId.value && i.kind.value == kind.value && i.target.value == target.value
        ) match
          case None =>
            val id = s.lastItemId.value + 1
            result = (ManagerItem.Id(id), true)
            val made = ManagerItemRecord(
              ManagerItemRecord.Id(id),
              ManagerItemRecord.ManagerId(managerId.value),
              ManagerItemRecord.Kind(kind.value),
              ManagerItemRecord.Target(target.value),
              ManagerItemRecord.Status(ItemStatus.Pending.wire.value),
              ManagerItemRecord.Priority(priority.value),
              ManagerItemRecord.Attempts(0),
              ManagerItemRecord.Failures(0),
              None,
              None,
              None,
              None,
              initial,
              None,
              ManagerItemRecord.Origin(origin.wire.value),
              at,
              at
            )
            s.modify(_.items).using(_ :+ made).modify(_.lastItemId).setTo(AutomationState.LastItemId(id))
          case Some(old) =>
            result = (ManagerItem.Id(old.id.value), false)
            if !reopen then s
            else
              val back = Settled(old.status.value)
              val again = old
                .modify(_.status)
                .using(st => if back then ManagerItemRecord.Status(ItemStatus.Pending.wire.value) else st)
                .modify(_.attempts)
                .using(n => if back then ManagerItemRecord.Attempts(0) else n)
                .modify(_.failures)
                .using(n => if back then ManagerItemRecord.Failures(0) else n)
                .modify(_.notBefore)
                .using(nb => if back then None else nb)
                .modify(_.outcome)
                .using(o => if back then None else o)
                .modify(_.priority)
                .using(p => ManagerItemRecord.Priority(math.max(p.value, priority.value)))
                .modify(_.origin)
                .setTo(ManagerItemRecord.Origin(origin.wire.value))
                .modify(_.updatedAt)
                .setTo(at)
              s.modify(_.items).using(_.map(i => if i.id == old.id then again else i))
      }
      result
    def item(id: ManagerItem.Id): Option[ManagerItem] = items.find(_.id.value == id.value).map(viewItem)
    def itemFor(managerId: ManagerInfo.Id, kind: ManagerItem.Kind, target: ManagerItem.Target): Option[ManagerItem] =
      items
        .find(i => i.managerId.value == managerId.value && i.kind.value == kind.value && i.target.value == target.value)
        .map(viewItem)
    def openFor(target: ManagerItem.Target): List[ManagerItem] =
      items.filter(i => i.target.value == target.value && Open(i.status.value)).sortBy(-_.id.value).toList.map(viewItem)
    def openKeys(managerId: ManagerInfo.Id): List[(ManagerItem.Kind, ManagerItem.Target)] =
      items
        .filter(i => i.managerId.value == managerId.value && Open(i.status.value))
        .toList
        .map(i => (ManagerItem.Kind(i.kind.value), ManagerItem.Target(i.target.value)))
    def openOf(
        managerId: ManagerInfo.Id,
        kinds: List[ManagerItem.Kind],
        statuses: List[ManagerItem.Status],
        focusHeld: Boolean
    ): RowCount =
      RowCount(
        items.count(i =>
          i.managerId.value == managerId.value && kinds.exists(_.value == i.kind.value) &&
            statuses.exists(_.value == i.status.value) &&
            (focusHeld || !this.focusHeld(i))
        )
      )
    def event(
        managerId: ManagerInfo.Id,
        itemId: Option[ManagerItem.Id],
        runId: Option[Run.Id],
        kind: ManagerEvent.Kind,
        text: ManagerEvent.Text
    ): Unit =
      val at = now
      val _ = state.updateAndGet { s =>
        val id = s.lastEventId.value + 1
        s.modify(_.events)
          .using(
            _ :+ ManagerEventRecord(
              ManagerEventRecord.Id(id),
              ManagerEventRecord.ManagerId(managerId.value),
              itemId.map(i => ManagerEventRecord.ItemId(i.value)),
              runId,
              at,
              ManagerEventRecord.Kind(kind.value),
              ManagerEventRecord.Text(text.value.take(2000))
            )
          )
          .modify(_.lastEventId)
          .setTo(AutomationState.LastEventId(id))
      }
    def lastEventOf(managerId: ManagerInfo.Id, kind: ManagerEvent.Kind): Option[ManagerEvent] =
      events
        .filter(e => e.managerId.value == managerId.value && e.kind.value == kind.value)
        .maxByOption[Long](_.id.value)
        .map(viewEvent)
    def countEventsAll(kind: ManagerEvent.Kind, since: Instant): RowCount =
      RowCount(events.count(e => e.kind.value == kind.value && !e.at.isBefore(since)))
    def backlogCounts(): Map[ManagerInfo.Id, (RowCount, RowCount)] =
      items
        .filter(i => i.kind.value == OaIngest && (i.status.value == "pending" || i.status.value == "held"))
        .groupBy(_.managerId.value)
        .view
        .map((m, is) =>
          ManagerInfo.Id(m) -> (
            RowCount(is.count(_.status.value == "pending")),
            RowCount(is.count(_.status.value == "held"))
          )
        )
        .toMap
    private def backlog(i: ManagerItemRecord): Boolean = i.kind.value == OaIngest && Open(i.status.value)
    def backlogPending(): List[ManagerItem] =
      val active = managers
        .filter(m => m.kind == DomainManager.Kind.Watch && m.state == DomainManager.State.Active)
        .map(_.id.value)
        .toSet
      items
        .filter(i => i.kind.value == OaIngest && i.status.value == "pending" && active(i.managerId.value))
        .sortBy[(Int, Long)](i => (-i.priority.value, i.id.value))
        .toList
        .map(viewItem)
    def backlogOpen(): List[ManagerItem] = items.filter(backlog).sortBy[Long](_.id.value).toList.map(viewItem)
    def backlogOpenFor(doi: Doi): Option[ManagerItem] =
      items.filter(i => backlog(i) && i.target.value == doi.value).minByOption[Long](_.id.value).map(viewItem)
    def backlogRetry(
        itemId: ManagerItem.Id,
        failures: ManagerItem.Failures,
        notBefore: Instant,
        outcome: ManagerItem.Outcome
    ): Boolean =
      val at = now
      changeItems(i => i.id.value == itemId.value && i.status.value == "pending")(
        _.modify(_.failures)
          .setTo(ManagerItemRecord.Failures(failures.value))
          .modify(_.attempts)
          .using(n => ManagerItemRecord.Attempts(n.value + 1))
          .modify(_.notBefore)
          .setTo(Some(notBefore))
          .modify(_.outcome)
          .setTo(Some(ManagerItemRecord.Outcome(outcome.value)))
          .modify(_.updatedAt)
          .setTo(at)
      ) > 0
    def countEventsBy(kind: ManagerEvent.Kind, since: Instant): Map[ManagerInfo.Id, RowCount] =
      counted(events.filter(e => e.kind.value == kind.value && !e.at.isBefore(since)).map(_.managerId.value))
        .map((m, n) => ManagerInfo.Id(m) -> RowCount(n))
    def setItemState(itemId: ManagerItem.Id, state: ItemState, from: List[ItemStatus]): Boolean =
      val at = now
      changeItems(i => i.id.value == itemId.value && from.exists(_.wire.value == i.status.value))(
        _.modify(_.state).setTo(state).modify(_.updatedAt).setTo(at)
      ) > 0

    /** The acquisition items are the work lists', which this state does not hold: none. */
    def sweepAcquireItems(): List[SweepAcquireItem] = Nil

    def pace(since: Instant): Map[ManagerInfo.Id, (RowCount, RowCount, Option[ManagerRepo.ProgressText])] =
      managers.map { m =>
        val mine = items.filter(i => i.managerId.value == m.id.value && i.kind.value != OaIngest)
        ManagerInfo.Id(m.id.value) -> (
          RowCount(mine.count(i => Open(i.status.value))),
          RowCount(
            mine.count(i => Set("done", "stopped", "failed")(i.status.value) && !i.updatedAt.isBefore(since))
          ),
          events.filter(_.managerId.value == m.id.value).map(_.at).maxOption.map(t => ManagerRepo.ProgressText(text(t)))
        )
      }.toMap
    private def changeItems(which: ManagerItemRecord => Boolean)(f: ManagerItemRecord => ManagerItemRecord): Int =
      state.getAndUpdate(_.modify(_.items).using(_.map(i => if which(i) then f(i) else i))).items.count(which)
    private def released(at: Instant)(i: ManagerItemRecord): ManagerItemRecord =
      i.modify(_.status)
        .setTo(ManagerItemRecord.Status(ItemStatus.Pending.wire.value))
        .modify(_.hold)
        .setTo(None)
        .modify(_.updatedAt)
        .setTo(at)
    def hold(itemId: ManagerItem.Id, h: Hold): Boolean =
      val at = now
      changeItems(i => i.id.value == itemId.value && (i.status.value == "pending" || i.status.value == "held"))(
        _.modify(_.status)
          .setTo(ManagerItemRecord.Status(ItemStatus.Held.wire.value))
          .modify(_.hold)
          .setTo(Some(h))
          .modify(_.updatedAt)
          .setTo(at)
      ) > 0
    def release(itemId: ManagerItem.Id): Boolean =
      changeItems(i => i.id.value == itemId.value && i.status.value == "held")(released(now)) > 0
    def settle(
        itemId: ManagerItem.Id,
        status: ItemStatus,
        outcome: ManagerItem.Outcome,
        from: List[ItemStatus]
    ): Boolean =
      val at = now
      changeItems(i => i.id.value == itemId.value && from.exists(_.wire.value == i.status.value))(
        _.modify(_.status)
          .setTo(ManagerItemRecord.Status(status.wire.value))
          .modify(_.outcome)
          .setTo(Some(ManagerItemRecord.Outcome(outcome.value)))
          .modify(_.hold)
          .setTo(None)
          .modify(_.updatedAt)
          .setTo(at)
      ) > 0
    def dispatch(itemId: ManagerItem.Id, run: Run.Id, state: ItemState): Boolean =
      val at = now
      changeItems(i => i.id.value == itemId.value && i.status.value == ItemStatus.Pending.wire.value)(
        _.modify(_.status)
          .setTo(ManagerItemRecord.Status(ItemStatus.Active.wire.value))
          .modify(_.runId)
          .setTo(Some(run))
          .modify(_.attempts)
          .using(n => ManagerItemRecord.Attempts(n.value + 1))
          .modify(_.state)
          .setTo(state)
          .modify(_.updatedAt)
          .setTo(at)
      ) > 0
    def focusHold(itemId: ManagerItem.Id, h: Hold): Boolean =
      val at = now
      changeItems(i => i.id.value == itemId.value && i.status.value == "pending")(
        _.modify(_.status)
          .setTo(ManagerItemRecord.Status(ItemStatus.Held.wire.value))
          .modify(_.hold)
          .setTo(Some(h))
          .modify(_.updatedAt)
          .setTo(at)
      ) > 0
    def focusRelease(ids: List[ManagerItem.Id]): RowCount =
      RowCount(
        if ids.isEmpty then 0
        else changeItems(i => ids.exists(_.value == i.id.value) && focusHeld(i))(released(now))
      )
    def focusReleaseAll(): RowCount = RowCount(changeItems(focusHeld)(released(now)))
    def focusHeldCount(): RowCount = RowCount(items.count(focusHeld))
    def forPerson(): RowCount =
      RowCount(
        items.count(i =>
          manager(i.managerId.value).exists(_.state != DomainManager.State.Stopped) &&
            ((i.status.value == "held" && i.hold.forall(_.focus.isEmpty)) ||
              (i.status.value == "active" && runOf(i).exists(_.row.status.value == "waiting")))
        )
      )
    def lastProgress(): Map[com.histmersion.api.automation.Goal.Slug, ManagerRepo.ProgressText] =
      val kinds = Set(Feed.Started, Feed.Settled, Feed.Evaluated, Feed.Ingested, Feed.Swept)
      val fromEvents = events
        .filter(e => kinds(e.kind.value))
        .flatMap(e => manager(e.managerId.value).flatMap(_.goal).map(g => g.value -> e.at))
      val fromLinks = state.get.links.filter(_.kind == Goal.LinkKind.Source).map(l => l.goal.value -> l.createdAt)
      (fromEvents ++ fromLinks)
        .groupBy(_._1)
        .view
        .map((g, ts) => com.histmersion.api.automation.Goal.Slug(g) -> ManagerRepo.ProgressText(text(ts.map(_._2).max)))
        .toMap
    def occupyingRuns(): List[(Pipeline.Wire, Option[com.histmersion.api.automation.Goal.Slug])] =
      runs
        .filter(occupying)
        .map(r =>
          (
            Pipeline.Wire(r.row.pipeline.value),
            r.row.managerId
              .flatMap(id => manager(id.value))
              .flatMap(_.goal)
              .map(g => com.histmersion.api.automation.Goal.Slug(g.value))
          )
        )
    def personHeldByGoal(): Map[Goal.Slug, RowCount] =
      counted(items.filter(personHeld).flatMap(goalOf)).map((g, n) => Goal.Slug.assume(g) -> RowCount(n))
    def recentKinds(n: PageSize): List[ManagerItem.Kind] =
      runs
        .sortBy[(java.time.Instant, Long)](r => (r.createdAt, r.created.value))
        .reverse
        .flatMap(r =>
          r.row.managerItem
            .flatMap(id => items.find(_.id.value == id.value))
            .map(i => ManagerItem.Kind(i.kind.value))
        )
        .take(math.max(0, n.value))
    def running(id: ManagerInfo.Id): RowCount =
      RowCount(runs.count(r => r.row.managerId.exists(_.value == id.value) && occupying(r)))
    def runningByGoal(): Map[Goal.Slug, RowCount] =
      counted(
        items
          .filter(i =>
            i.status.value == "active" &&
              runOf(i).exists(r => r.row.status.value == "queued" || r.row.status.value == "running")
          )
          .flatMap(goalOf)
      ).map((g, n) => Goal.Slug.assume(g) -> RowCount(n))
    def sweepOutcomes(): Map[Goal.Slug, ManagerItem.Outcome] =
      items
        .filter(i => i.kind.value == AcquisitionSweep && Set("done", "stopped", "failed")(i.status.value))
        .flatMap(i => goalOf(i).map(g => Goal.Slug.assume(g) -> ManagerItem.Outcome(i.outcome.fold("")(_.value))))
        .toMap
