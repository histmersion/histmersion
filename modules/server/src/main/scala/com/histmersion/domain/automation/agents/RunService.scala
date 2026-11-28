package com.histmersion
package domain.automation.agents
import com.histmersion.util.problem.Problem

import com.histmersion.api.automation.Agents.*
import com.histmersion.api.automation.Stopped
import com.histmersion.api.automation.{Goal as ApiGoal, Managers, Queue}
import com.histmersion.domain.automation.Run
import com.histmersion.util.jobs.Job
import com.histmersion.{PageSize, RowCount}
import com.histmersion.api.Paging
import com.histmersion.api.automation.ListFilters
import com.histmersion.domain.automation.agents.AgentRepo
import com.histmersion.util.database.Jsonb
import com.histmersion.domain.automation.AutomationState
import com.histmersion.util.database.Transactor
import com.histmersion.util.jobs.{JobRuns, JobRunState, RunRow}
import hearth.kindlings.optics.syntax.*

import java.time.{Clock, Instant, ZoneOffset}
import java.util.concurrent.atomic.AtomicReference
import com.histmersion.domain.automation.Goal
import com.histmersion.domain.automation.managers.ManagerLight
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.automation.managers.SweepAcquireItem
import com.histmersion.domain.automation.goals.FocusRepo
import com.histmersion.domain.CodexRetention
import com.histmersion.util.database.WritePermit
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.TwoCases

/** The agent runs as the engine's features read them — each run's row with its steps' usage summed — and the steps
  * themselves: the checkpoints a resume replays, the attempts, the trace, the spend.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@tolerated(LintRule.Repository, "pending G11 (a repository stays inside its feature)")
trait RunService:
  def get(id: Run.Id): Option[RunInfo]

  /** Newest first: of one state and one goal, when given. */
  def list(status: Option[RunQuery.Status], limit: RunQuery.Limit, goal: Option[Goal.Slug] = None): Vector[RunInfo]

  /** One newest-first page of a state and a goal; `before`/`upTo` are run ids (Left: one that names no job). */
  def page(
      status: Option[RunQuery.Status],
      limit: RunQuery.Limit,
      goal: Option[Goal.Slug] = None,
      before: Option[AgentRepo.PageCursor] = None,
      upTo: Option[RunQuery.UpTo] = None
  ): Either[Problem, (Vector[RunInfo], Boolean)]

  /** One newest-first page of the runs `q` matches. */
  def page(
      q: RunQuery,
      limit: RunQuery.Limit,
      before: Option[AgentRepo.PageCursor],
      upTo: Option[RunQuery.UpTo]
  ): Either[Problem, (Vector[RunInfo], Boolean)]

  /** Per state, and the pipeline, origin, manager, model and provider facets, each without its own filter. */
  def runFacets(
      q: RunQuery
  ): (List[(Run.State.Wire, RunService.Count)], List[(String, List[(String, RunService.Count)])])

  /** Runs per state (of one goal). */
  def stateCounts(goal: Option[Goal.Slug] = None): List[(Run.State.Wire, RunService.Count)]

  /** Every run of `p` on `target`, newest first. */
  def forTarget(p: Pipeline, target: AgentsControllers.Target): Vector[RunInfo]

  /** The runs a manager dispatched, newest first (`active`: those not settled yet). */
  def forManager(
      managerId: ManagerLight.Id,
      active: RunService.Active = RunService.Active.Inactive,
      limit: RunQuery.Limit = RunQuery.Limit(200)
  ): Vector[RunInfo]

  /** Every run on `target` that has not settled. */
  def unsettledOn(target: AgentsControllers.Target): Vector[RunInfo]

  /** Step `seq`'s checkpoint: its name and output, when an attempt finished. */
  def checkpoint(
      jobId: Job.Id,
      seq: AgentRepo.StepRecord.Seq
  ): Option[(AgentRepo.StepRecord.Name, AgentRepo.StepRecord.Output)]

  /** How many attempts step `seq` has. */
  def attempts(jobId: Job.Id, seq: AgentRepo.StepRecord.Seq): RunService.Count

  /** One attempt and its trace, together. */
  def record(jobId: Job.Id, step: AgentRepo.StepRecord, messages: List[AgentRepo.MessageRecord])(using
      WritePermit
  ): Unit
  def steps(jobId: Job.Id, outputChars: AgentRepo.OutputChars): List[StepInfo]
  def messages(jobId: Job.Id): List[MessageInfo]

  /** What one run has spent, every attempt counted. */
  def runSpend(jobId: Job.Id): (Usage, BigDecimal)

  /** What every run spent since the start of the UTC day and month. */
  def spendDayMonth(): (BigDecimal, BigDecimal)

  /** Traced messages older than `days` deleted (none for 0 or less); how many. */
  def pruneMessages(days: RunService.RetentionDays)(using WritePermit): RunService.Count

object RunService:
  enum Active derives CanEqual:
    case Active, Inactive
  object Active extends TwoCases[Active]:
    extension (active: Active) def active: Boolean = active == Active
  type Count = Count.T
  object Count extends Newtype[Int, Pure]
  enum DryRun derives CanEqual:
    case DryRun, Applied
  object DryRun extends TwoCases[DryRun]:
    extension (dryRun: DryRun) def dryRun: Boolean = dryRun == DryRun
  type RetentionDays = RetentionDays.T
  object RetentionDays extends Newtype[Int, Pure]

  def apply(db: Transactor): RunService = Implementation(db)

  /** A new run's row: `queued`, of pipeline `p` on `target`; `goal` is the goal it works for, `parent` the run that
    * started it, `origin` who; `dryRun` is fixed here, once — a resumed dry run is still one. `managerId` and
    * `managerItem`: the manager and work item it is for. The jobs area records it (`util.jobs.JobRuns`).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def row(
      p: Pipeline,
      target: AgentsControllers.Target,
      model: Option[AgentsControllers.ModelName],
      budgetUsd: BigDecimal,
      budgetTokens: AgentsControllers.BudgetTokens,
      params: RunParams,
      acquireItem: Option[SweepAcquireItem.Item],
      goal: Option[Goal.Slug],
      parent: Option[Run.Id],
      origin: Origin,
      dryRun: DryRun,
      managerId: Option[ManagerLight.Id] = None,
      managerItem: Option[FocusRepo.WorkRow.Id] = None
  ): RunRow =
    RunRow(
      RunRow.Kind(JobKind),
      RunRow.Status(RunState.Queued.wire.value),
      RunRow.Pipeline(p.wire.value),
      RunRow.PipelineVersion(p.version.value),
      RunRow.TargetKind(p.targetKind.wire.value),
      RunRow.Target(target.value),
      if p.targetKind == TargetKind.Source then RunRow.Citekey.option(target.value) else None,
      model.map(m => RunRow.Model(m.value)),
      budgetUsd,
      RunRow.BudgetTokens(budgetTokens.value),
      RunRow.Params(Jsonb.write(params, dropNulls = Jsonb.DropNulls.DropNulls).value),
      acquireItem.map(i => RunRow.AcquireItem(i.value)),
      goal.map(g => RunRow.Goal(g.value)),
      parent.map(r => RunRow.Parent(r.value)),
      RunRow.Origin(origin.wire.value),
      (if dryRun.dryRun then RunRow.DryRun.DryRun else RunRow.DryRun.Applied),
      managerId.map(m => RunRow.ManagerId(m.value)),
      managerItem.map(i => RunRow.ManagerItem(i.value))
    )

  final private class Implementation(db: Transactor) extends RunService:
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def stateOf(status: Option[RunQuery.Status]): Option[Run.State.Wire] =
      status.map(s => Run.State.Wire(s.value))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def size(limit: RunQuery.Limit): PageSize = PageSize.assume(math.max(1, limit.value))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def count(n: RowCount): Count =
      Count(n.value)

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def get(id: Run.Id): Option[RunInfo] = db.read(AgentRepo.get(RunInfo.Id(id.value)))
    def list(status: Option[RunQuery.Status], limit: RunQuery.Limit, goal: Option[Goal.Slug]): Vector[RunInfo] =
      db.read(AgentRepo.list(stateOf(status), size(limit), goal))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def page(
        status: Option[RunQuery.Status],
        limit: RunQuery.Limit,
        goal: Option[Goal.Slug],
        before: Option[AgentRepo.PageCursor],
        upTo: Option[RunQuery.UpTo]
    ): Either[Problem, (Vector[RunInfo], Boolean)] =
      db.read(AgentRepo.page(stateOf(status), size(limit), goal, before, upTo.map(u => AgentRepo.PageCursor(u.value))))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def page(
        q: RunQuery,
        limit: RunQuery.Limit,
        before: Option[AgentRepo.PageCursor],
        upTo: Option[RunQuery.UpTo]
    ): Either[Problem, (Vector[RunInfo], Boolean)] =
      db.read(AgentRepo.page(q, size(limit), before, upTo.map(u => AgentRepo.PageCursor(u.value))))
    def runFacets(q: RunQuery): (List[(Run.State.Wire, Count)], List[(String, List[(String, Count)])]) =
      val (states, facets) = db.read(AgentRepo.runFacets(q))
      (states.map((s, n) => (s, count(n))), facets.map((name, vs) => (name, vs.map((v, n) => (v, count(n))))))
    def stateCounts(goal: Option[Goal.Slug]): List[(Run.State.Wire, Count)] =
      db.read(AgentRepo.stateCounts(goal)).map((s, n) => (s, count(n)))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def forTarget(p: Pipeline, target: AgentsControllers.Target): Vector[RunInfo] =
      db.read(AgentRepo.forTarget(p, RunInfo.Target(target.value)))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def forManager(managerId: ManagerLight.Id, active: Active, limit: RunQuery.Limit): Vector[RunInfo] =
      db.read(
        AgentRepo.forManager(
          Managers.ManagerInfo.Id(managerId.value),
          (if active.active then AgentRepo.ActiveOnly.ActiveOnly else AgentRepo.ActiveOnly.All),
          size(limit)
        )
      )
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def unsettledOn(target: AgentsControllers.Target): Vector[RunInfo] =
      db.read(AgentRepo.unsettledOn(RunInfo.Target(target.value)))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def checkpoint(
        jobId: Job.Id,
        seq: AgentRepo.StepRecord.Seq
    ): Option[(AgentRepo.StepRecord.Name, AgentRepo.StepRecord.Output)] =
      db.read(AgentRepo.checkpoint(jobId, StepInfo.Seq(seq.value)))
        .map((name, output) => (AgentRepo.StepRecord.Name(name.value), AgentRepo.StepRecord.Output(output.value)))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def attempts(jobId: Job.Id, seq: AgentRepo.StepRecord.Seq): Count =
      Count(db.read(AgentRepo.attempts(jobId, StepInfo.Seq(seq.value))).value)
    def record(jobId: Job.Id, step: AgentRepo.StepRecord, messages: List[AgentRepo.MessageRecord])(using
        WritePermit
    ): Unit =
      db.write(AgentRepo.record(jobId, step, messages))
    def steps(jobId: Job.Id, outputChars: AgentRepo.OutputChars): List[StepInfo] =
      db.read(AgentRepo.steps(jobId, outputChars))
    def messages(jobId: Job.Id): List[MessageInfo] = db.read(AgentRepo.messages(jobId))
    def runSpend(jobId: Job.Id): (Usage, BigDecimal) = db.read(AgentRepo.runSpend(jobId))
    def spendDayMonth(): (BigDecimal, BigDecimal) = db.read(AgentRepo.spendDayMonth())
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def pruneMessages(days: RetentionDays)(using WritePermit): Count =
      count(db.write(AgentRepo.pruneMessages(CodexRetention.KeepDays(days.value))))

  /** The runs in memory: their rows are the jobs area's in-memory runs, their steps and traces the shared state;
    * `clock` stands in for the database's `now()`.
    */
  final class InMemory(state: AtomicReference[AutomationState], jobs: JobRuns.InMemory, clock: Clock)
      extends RunService:
    private def now: Instant = Instant.now(clock)
    private val Unsettled = Set("queued", "running", "paused", "waiting")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def runs: List[JobRunState] = jobs.all.filter(_.row.kind.value == JobKind)
    private def stepsOf(jobId: Job.Id): Vector[StepRecordState] = state.get.steps.filter(_.jobId == jobId)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def jobOf(r: JobRunState): Job.Id = Job.Id.assume(r.id.value)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def text(at: Instant): String = AutomationState.text(at).value
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def count(n: Int): Count =
      Count(n)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def info(r: JobRunState): RunInfo =
      val ss = stepsOf(jobOf(r)).map(_.step)
      val error = r.error
      val status = RunInfo.Status(r.row.status.value)
      RunInfo(
        RunInfo.Id(r.id.value),
        RunInfo.Pipeline(r.row.pipeline.value),
        RunInfo.Target(r.row.target.value),
        status,
        r.progress.map(p => RunInfo.Progress(p.value)),
        error,
        r.row.model.map(m => RunInfo.Model(m.value)),
        Usage(
          Usage.Input(ss.map(_.usage.input.value).sum),
          Usage.Output(ss.map(_.usage.output.value).sum),
          Usage.CacheRead(ss.map(_.usage.cacheRead.value).sum),
          Usage.CacheWrite(ss.map(_.usage.cacheWrite.value).sum)
        ),
        RunInfo.CostUsd(ss.map(_.costUsd).sum.toDouble),
        RunInfo.BudgetUsd(r.row.budgetUsd.toDouble),
        RunInfo.BudgetTokens(r.row.budgetTokens.value),
        r.warnings.map(w => RunInfo.Warnings(w.value)),
        RunInfo.CreatedAt(text(r.createdAt)),
        RunInfo.UpdatedAt(text(r.updatedAt)),
        RunInfo.TargetKind(r.row.targetKind.value),
        Jsonb.read[RunParams](Jsonb.JsonText(r.row.params.value)).getOrElse(RunParams()),
        r.row.acquireItem.map(i => Queue.QueueItem.Id(i.value)),
        r.row.goal.map(g => ApiGoal.Slug(g.value)),
        r.row.parent.map(p => RunInfo.Id(p.value)),
        Some(RunInfo.Origin(r.row.origin.value)),
        r.resumeAt.map(t => RunInfo.ResumeAt(text(t))),
        (if r.row.dryRun.dryRun then RunInfo.DryRun.DryRun else RunInfo.DryRun.Apply),
        r.row.managerId.map(m => Managers.ManagerInfo.Id(m.value)),
        r.row.managerItem.map(i => Managers.ManagerItem.Id(i.value)),
        r.heartbeatAt.map(t => RunInfo.HeartbeatAt(text(t))),
        pausedFor = Option.when(status == RunState.Paused.wire)(Stopped.pausedFor(error)).flatten
      )
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def key(r: JobRunState): (Instant, Long) = (r.createdAt, r.created.value)
    private def newest(rs: List[JobRunState]): List[JobRunState] = rs.sortBy(key).reverse
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def runOf(cursor: AgentRepo.PageCursor): Option[JobRunState] =
      Run.Id.option(cursor.value).flatMap(jobs.get)

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def get(id: Run.Id): Option[RunInfo] = jobs.get(id).filter(_.row.kind.value == JobKind).map(info)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def list(status: Option[RunQuery.Status], limit: RunQuery.Limit, goal: Option[Goal.Slug]): Vector[RunInfo] =
      val st = status.map(_.value.trim.toLowerCase).filter(_.nonEmpty)
      val g = goal.map(_.value.trim).filter(_.nonEmpty)
      newest(runs.filter(r => st.forall(_ == r.row.status.value) && g.forall(x => r.row.goal.exists(_.value == x))))
        .take(math.max(1, limit.value))
        .toVector
        .map(info)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def page(
        status: Option[RunQuery.Status],
        limit: RunQuery.Limit,
        goal: Option[Goal.Slug],
        before: Option[AgentRepo.PageCursor],
        upTo: Option[RunQuery.UpTo]
    ): Either[Problem, (Vector[RunInfo], Boolean)] =
      page(
        RunQuery(
          status = status.map(_.value.trim.toLowerCase).filter(_.nonEmpty).map(s => RunQuery.Status(s)).toList,
          goal = goal.map(_.value.trim).filter(_.nonEmpty).map(g => RunQuery.Goal(g))
        ),
        limit,
        before,
        upTo
      )

    /** The models a run used: the one it was started with and every one its steps reported. */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def models(r: JobRunState): Set[String] =
      (r.row.model.toList.map(_.value) ++ stepsOf(jobOf(r)).flatMap(_.step.model.map(_.value))).toSet
    private def provider(model: String): String = model.takeWhile(_ != ':')

    /** Whether `r` passes every filter of `q` but `except`'s. */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def matches(q: RunQuery, except: String)(r: JobRunState): Boolean =
      def any(name: String, vs: List[String], v: => Option[String]) =
        except == name || vs.isEmpty || v.exists(vs.contains)
      any("status", q.status.map(_.value), Some(r.row.status.value)) &&
      any("pipeline", q.pipelines.map(_.value), Some(r.row.pipeline.value)) &&
      any("manager", q.managers.map(_.value), r.row.managerId.map(_.value)) &&
      (except == "goal" || q.goal.forall(g => r.row.goal.exists(_.value == g.value))) &&
      (except == "model" || q.models.isEmpty || models(r).exists(q.models.map(_.value).contains)) &&
      (except == "provider" || q.providers.isEmpty || models(r)
        .map(provider)
        .exists(q.providers.map(_.value).contains)) &&
      any("origin", q.origins.map(_.value), Some(r.row.origin.value)) &&
      (except == "time" || q.since.forall(s => !r.createdAt.isBefore(s)) && q.until.forall(u =>
        r.createdAt.isBefore(u)
      )) &&
      (except == "q" || q.q.forall(t => r.row.target.value.toLowerCase.contains(t.value.toLowerCase)))

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def page(
        q: RunQuery,
        limit: RunQuery.Limit,
        before: Option[AgentRepo.PageCursor],
        upTo: Option[RunQuery.UpTo]
    ): Either[Problem, (Vector[RunInfo], Boolean)] =
      val cursors = before.toList ++ upTo.toList.map(u => AgentRepo.PageCursor(u.value))
      cursors.find(c => runOf(c).isEmpty) match
        case Some(c) => Left(Problem.NotFound.Entity(Problem.TypeName.of(Run.Id), Problem.Raw.of(c)))
        case None    =>
          val ordering = summon[Ordering[(Instant, Long)]]
          val all = newest(runs.filter(matches(q, "")))
          val b = before.flatMap(runOf)
          val u = upTo.flatMap(c => runOf(AgentRepo.PageCursor(c.value)))
          val bounded =
            all.filter(r => b.forall(c => ordering.lt(key(r), key(c))) && u.forall(c => ordering.gteq(key(r), key(c))))
          val n = math.max(1, limit.value)
          val (shown, more) = Paging.cut(bounded.take(n + 1).toVector, Paging.Limit(n))
          val older =
            if more || u.isEmpty then more
            else shown.lastOption.exists(last => all.exists(r => ordering.lt(key(r), key(last))))
          Right((shown.map(info), older))

    private def facet(values: List[String]): List[(String, Count)] =
      values
        .groupBy(identity)
        .toList
        .map((v, n) => (v, n.size))
        .sortBy((v, n) => (-n, v))
        .take(ListFilters.FacetMax)
        .map((v, n) => (v, count(n)))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def runFacets(q: RunQuery): (List[(Run.State.Wire, Count)], List[(String, List[(String, Count)])]) =
      def of(except: String) = runs.filter(matches(q, except))
      (
        facet(of("status").map(_.row.status.value)).map((v, n) => (Run.State.Wire(v), n)),
        List(
          "pipeline" -> facet(of("pipeline").map(_.row.pipeline.value)),
          "origin" -> facet(of("origin").map(_.row.origin.value)),
          "manager" -> facet(of("manager").flatMap(_.row.managerId.map(_.value))),
          "model" -> facet(of("model").flatMap(r => models(r).toList)),
          "provider" -> facet(of("provider").flatMap(r => models(r).map(provider)))
        )
      )
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def stateCounts(goal: Option[Goal.Slug]): List[(Run.State.Wire, Count)] =
      val g = goal.map(_.value.trim).filter(_.nonEmpty)
      facet(runs.filter(r => g.forall(x => r.row.goal.exists(_.value == x))).map(_.row.status.value))
        .map((v, n) => (Run.State.Wire(v), n))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def forTarget(p: Pipeline, target: AgentsControllers.Target): Vector[RunInfo] =
      runs
        .filter(r => r.row.pipeline.value == p.wire.value && r.row.target.value == target.value)
        .sortBy(r => (r.createdAt, -r.created.value))
        .reverse
        .toVector
        .map(info)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def forManager(managerId: ManagerLight.Id, active: Active, limit: RunQuery.Limit): Vector[RunInfo] =
      newest(
        runs.filter(r =>
          r.row.managerId.exists(_.value == managerId.value) && (!active.active || Unsettled(r.row.status.value))
        )
      )
        .take(math.max(1, limit.value))
        .toVector
        .map(info)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def unsettledOn(target: AgentsControllers.Target): Vector[RunInfo] =
      runs.filter(r => r.row.target.value == target.value && Unsettled(r.row.status.value)).toVector.map(info)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def checkpoint(
        jobId: Job.Id,
        seq: AgentRepo.StepRecord.Seq
    ): Option[(AgentRepo.StepRecord.Name, AgentRepo.StepRecord.Output)] =
      stepsOf(jobId)
        .filter(s => s.step.seq == seq && s.step.status == StepStatus.Done)
        .sortBy[Int](_.step.attempt.value)
        .lastOption
        .map(s => (s.step.name, s.step.output.getOrElse(AgentRepo.StepRecord.Output("null"))))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def attempts(jobId: Job.Id, seq: AgentRepo.StepRecord.Seq): Count =
      count(stepsOf(jobId).filter(_.step.seq == seq).map(_.step.attempt.value).maxOption.getOrElse(0))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def record(jobId: Job.Id, step: AgentRepo.StepRecord, messages: List[AgentRepo.MessageRecord])(using
        WritePermit
    ): Unit =
      val at = now
      val _ = state.updateAndGet(
        _.modify(_.steps)
          .using(_ :+ StepRecordState(jobId, step, at))
          .modify(_.messages)
          .using(
            _ ++ messages.map(m =>
              MessageRecordState(
                jobId,
                MessageRecordState.Seq(step.seq.value),
                MessageRecordState.Attempt(step.attempt.value),
                m,
                at
              )
            )
          )
      )
      jobs.stamped(Run.Id.assume(jobId.value))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def steps(jobId: Job.Id, outputChars: AgentRepo.OutputChars): List[StepInfo] =
      stepsOf(jobId).sortBy[(Int, Int)](s => (s.step.seq.value, s.step.attempt.value)).toList.map { s =>
        val r = s.step
        StepInfo(
          StepInfo.Seq(r.seq.value),
          StepInfo.Name(r.name.value),
          StepInfo.Kind(r.kind.wire.value),
          StepInfo.Attempt(r.attempt.value),
          StepInfo.Status(r.status.wire.value),
          r.model.map(m => StepInfo.Model(m.value)),
          r.promptHash.map(h => StepInfo.PromptHash(h.value)),
          r.usage,
          StepInfo.CostUsd(r.costUsd.toDouble),
          r.error,
          r.output.map(o => StepInfo.Output(o.value.take(outputChars.value))),
          StepInfo.StartedAt(text(r.startedAt)),
          StepInfo.FinishedAt(text(s.finishedAt)),
          r.actualModel.map(a => StepInfo.ActualModel(a.value)),
          r.wouldWrite,
          r.firstCallTokens.map(t => StepInfo.FirstCallTokens(t.value)),
          r.turns.map(t => StepInfo.Turns(t.value))
        )
      }
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def messages(jobId: Job.Id): List[MessageInfo] =
      state.get.messages
        .filter(_.jobId == jobId)
        .sortBy[(Int, Int, Int)](m => (m.seq.value, m.attempt.value, m.message.n.value))
        .toList
        .map(m =>
          MessageInfo(
            StepInfo.Seq(m.seq.value),
            MessageInfo.N(m.message.n.value),
            MessageInfo.Role(m.message.role.value),
            m.message.content.map(c => MessageInfo.Content(c.value)),
            m.message.toolName.map(t => MessageInfo.ToolName(t.value)),
            m.message.toolInput.map(t => MessageInfo.ToolInput(t.value)),
            m.message.toolResult.map(t => MessageInfo.ToolOutput(t.value)),
            m.message.latencyMs.map(l => MessageInfo.LatencyMs(l.value))
          )
        )
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def runSpend(jobId: Job.Id): (Usage, BigDecimal) =
      val ss = stepsOf(jobId).map(_.step)
      (
        Usage(
          Usage.Input(ss.map(_.usage.input.value).sum),
          Usage.Output(ss.map(_.usage.output.value).sum),
          Usage.CacheRead(ss.map(_.usage.cacheRead.value).sum),
          Usage.CacheWrite(ss.map(_.usage.cacheWrite.value).sum)
        ),
        ss.map(_.costUsd).sum
      )
    def spendDayMonth(): (BigDecimal, BigDecimal) =
      val today = now.atZone(ZoneOffset.UTC).toLocalDate
      val day = today.atStartOfDay(ZoneOffset.UTC).toInstant
      val month = today.withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant
      val ss = state.get.steps
      (
        ss.filter(s => !s.finishedAt.isBefore(day)).map(_.step.costUsd).sum,
        ss.filter(s => !s.finishedAt.isBefore(month)).map(_.step.costUsd).sum
      )
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def pruneMessages(days: RetentionDays)(using WritePermit): Count =
      if days.value <= 0 then Count(0)
      else
        val cut = now.minusSeconds(days.value.toLong * 86400L)
        count(
          state
            .getAndUpdate(_.modify(_.messages).using(_.filterNot(_.createdAt.isBefore(cut))))
            .messages
            .count(_.createdAt.isBefore(cut))
        )
