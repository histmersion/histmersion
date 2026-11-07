package com.histmersion.domain.automation.agents

import com.histmersion.api.automation.Agents.*
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

/** The agent runs as the engine's features read them — each run's row with its steps' usage summed — and the steps
  * themselves: the checkpoints a resume replays, the attempts, the trace, the spend.
  */
trait RunService:
  def get(id: com.histmersion.domain.automation.Run.Id): Option[RunInfo]

  /** Newest first: of one state and one goal, when given. */
  def list(status: Option[com.histmersion.domain.automation.agents.RunQuery.Status], limit: com.histmersion.domain.automation.agents.RunQuery.Limit, goal: Option[com.histmersion.domain.automation.Goal.Slug] = None): Vector[RunInfo]

  /** One newest-first page of a state and a goal; `before`/`upTo` are run ids (Left: one that names no job). */
  def page(
      status: Option[com.histmersion.domain.automation.agents.RunQuery.Status],
      limit: com.histmersion.domain.automation.agents.RunQuery.Limit,
      goal: Option[com.histmersion.domain.automation.Goal.Slug] = None,
      before: Option[com.histmersion.domain.automation.agents.AgentRepo.PageCursor] = None,
      upTo: Option[com.histmersion.domain.automation.agents.RunQuery.UpTo] = None
  ): Either[com.histmersion.domain.automation.agents.AgentRepo.PageCursor, (Vector[RunInfo], Boolean)]

  /** One newest-first page of the runs `q` matches. */
  def page(
      q: RunQuery,
      limit: com.histmersion.domain.automation.agents.RunQuery.Limit,
      before: Option[com.histmersion.domain.automation.agents.AgentRepo.PageCursor],
      upTo: Option[com.histmersion.domain.automation.agents.RunQuery.UpTo]
  ): Either[com.histmersion.domain.automation.agents.AgentRepo.PageCursor, (Vector[RunInfo], Boolean)]

  /** Per state, and the pipeline, origin, manager, model and provider facets, each without its own filter. */
  def runFacets(q: RunQuery): (List[(com.histmersion.domain.automation.agents.RunService.RunFacetsResultPart1Part1, com.histmersion.domain.automation.agents.RunService.Count)], List[(com.histmersion.domain.automation.agents.RunService.RunFacetsResultPart2Part1, List[(com.histmersion.domain.automation.agents.RunService.RunFacetsResultPart2Part2Part1, com.histmersion.domain.automation.agents.RunService.Count)])])

  /** Runs per state (of one goal). */
  def stateCounts(goal: Option[com.histmersion.domain.automation.Goal.Slug] = None): List[(com.histmersion.domain.automation.Run.State.Wire, com.histmersion.domain.automation.agents.RunService.Count)]

  /** Every run of `p` on `target`, newest first. */
  def forTarget(p: Pipeline, target: com.histmersion.domain.automation.agents.AgentsControllers.Target): Vector[RunInfo]

  /** The runs a manager dispatched, newest first (`active`: those not settled yet). */
  def forManager(managerId: com.histmersion.domain.automation.managers.ManagerLight.Id, active: com.histmersion.domain.automation.agents.RunService.Active = com.histmersion.domain.automation.agents.RunService.Active(false), limit: com.histmersion.domain.automation.agents.RunQuery.Limit = com.histmersion.domain.automation.agents.RunQuery.Limit(200)): Vector[RunInfo]

  /** Every run on `target` that has not settled. */
  def unsettledOn(target: com.histmersion.domain.automation.agents.AgentsControllers.Target): Vector[RunInfo]

  /** Step `seq`'s checkpoint: its name and output, when an attempt finished. */
  def checkpoint(jobId: com.histmersion.util.jobs.Job.Id, seq: com.histmersion.domain.automation.agents.AgentRepo.StepRecord.Seq): Option[(com.histmersion.domain.automation.agents.AgentRepo.StepRecord.Name, com.histmersion.domain.automation.agents.AgentRepo.StepRecord.Output)]

  /** How many attempts step `seq` has. */
  def attempts(jobId: com.histmersion.util.jobs.Job.Id, seq: com.histmersion.domain.automation.agents.AgentRepo.StepRecord.Seq): com.histmersion.domain.automation.agents.RunService.Count

  /** One attempt and its trace, together. */
  def record(jobId: com.histmersion.util.jobs.Job.Id, step: AgentRepo.StepRecord, messages: List[AgentRepo.MessageRecord]): Unit
  def steps(jobId: com.histmersion.util.jobs.Job.Id, outputChars: com.histmersion.domain.automation.agents.AgentRepo.OutputChars): List[StepInfo]
  def messages(jobId: com.histmersion.util.jobs.Job.Id): List[MessageInfo]

  /** What one run has spent, every attempt counted. */
  def runSpend(jobId: com.histmersion.util.jobs.Job.Id): (Usage, BigDecimal)

  /** What every run spent since the start of the UTC day and month. */
  def spendDayMonth(): (BigDecimal, BigDecimal)

  /** Traced messages older than `days` deleted (none for 0 or less); how many. */
  def pruneMessages(days: com.histmersion.domain.automation.agents.RunService.RetentionDays): com.histmersion.domain.automation.agents.RunService.Count

object RunService:
  type Active = Active.T
  object Active extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Count = Count.T
  object Count extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DryRun = DryRun.T
  object DryRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RetentionDays = RetentionDays.T
  object RetentionDays extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunFacetsResultPart1Part1 = RunFacetsResultPart1Part1.T
  object RunFacetsResultPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunFacetsResultPart2Part1 = RunFacetsResultPart2Part1.T
  object RunFacetsResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunFacetsResultPart2Part2Part1 = RunFacetsResultPart2Part2Part1.T
  object RunFacetsResultPart2Part2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(db: Transactor): RunService = Implementation(db)

  /** A new run's row: `queued`, of pipeline `p` on `target`; `goal` is the goal it works for, `parent` the run that
    * started it, `origin` who; `dryRun` is fixed here, once — a resumed dry run is still one. `managerId` and
    * `managerItem`: the manager and work item it is for. The jobs area records it (`util.jobs.JobRuns`).
    */
  def row(
      p: Pipeline,
      target: com.histmersion.domain.automation.agents.AgentsControllers.Target,
      model: Option[com.histmersion.domain.automation.agents.AgentsControllers.ModelName],
      budgetUsd: BigDecimal,
      budgetTokens: com.histmersion.domain.automation.agents.AgentsControllers.BudgetTokens,
      params: RunParams,
      acquireItem: Option[com.histmersion.domain.automation.managers.SweepAcquireItem.Item],
      goal: Option[com.histmersion.domain.automation.Goal.Slug],
      parent: Option[com.histmersion.domain.automation.Run.Id],
      origin: Origin,
      dryRun: com.histmersion.domain.automation.agents.RunService.DryRun,
      managerId: Option[com.histmersion.domain.automation.managers.ManagerLight.Id] = None,
      managerItem: Option[com.histmersion.domain.automation.goals.FocusRepo.WorkRow.Id] = None
  ): RunRow =
    RunRow(
      JobKind,
      RunState.Queued.wire,
      p.wire,
      p.version,
      p.targetKind.wire,
      target,
      Option.when(p.targetKind == TargetKind.Source)(target),
      model,
      budgetUsd,
      budgetTokens,
      Jsonb.write(params, dropNulls = com.histmersion.util.database.Jsonb.DropNulls(true)),
      acquireItem,
      goal,
      parent,
      origin.wire,
      dryRun,
      managerId,
      managerItem
    )

  final private class Implementation(db: Transactor) extends RunService:
    def get(id: com.histmersion.domain.automation.Run.Id): Option[RunInfo] = db.read(AgentRepo.get(id))
    def list(status: Option[com.histmersion.domain.automation.agents.RunQuery.Status], limit: com.histmersion.domain.automation.agents.RunQuery.Limit, goal: Option[com.histmersion.domain.automation.Goal.Slug]): Vector[RunInfo] =
      db.read(AgentRepo.list(status, limit, goal))
    def page(
        status: Option[com.histmersion.domain.automation.agents.RunQuery.Status],
        limit: com.histmersion.domain.automation.agents.RunQuery.Limit,
        goal: Option[com.histmersion.domain.automation.Goal.Slug],
        before: Option[com.histmersion.domain.automation.agents.AgentRepo.PageCursor],
        upTo: Option[com.histmersion.domain.automation.agents.RunQuery.UpTo]
    ): Either[com.histmersion.domain.automation.agents.AgentRepo.PageCursor, (Vector[RunInfo], Boolean)] = db.read(AgentRepo.page(status, limit, goal, before, upTo))
    def page(
        q: RunQuery,
        limit: com.histmersion.domain.automation.agents.RunQuery.Limit,
        before: Option[com.histmersion.domain.automation.agents.AgentRepo.PageCursor],
        upTo: Option[com.histmersion.domain.automation.agents.RunQuery.UpTo]
    ): Either[com.histmersion.domain.automation.agents.AgentRepo.PageCursor, (Vector[RunInfo], Boolean)] = db.read(AgentRepo.page(q, limit, before, upTo))
    def runFacets(q: RunQuery): (List[(com.histmersion.domain.automation.agents.RunService.Implementation.RunFacetsResultPart1Part1, com.histmersion.domain.automation.agents.RunService.Count)], List[(com.histmersion.domain.automation.agents.RunService.Implementation.RunFacetsResultPart2Part1, List[(com.histmersion.domain.automation.agents.RunService.Implementation.RunFacetsResultPart2Part2Part1, com.histmersion.domain.automation.agents.RunService.Count)])]) =
      db.read(AgentRepo.runFacets(q))
    def stateCounts(goal: Option[com.histmersion.domain.automation.Goal.Slug]): List[(com.histmersion.domain.automation.Run.State.Wire, com.histmersion.domain.automation.agents.RunService.Count)] = db.read(AgentRepo.stateCounts(goal))
    def forTarget(p: Pipeline, target: com.histmersion.domain.automation.agents.AgentsControllers.Target): Vector[RunInfo] = db.read(AgentRepo.forTarget(p, target))
    def forManager(managerId: com.histmersion.domain.automation.managers.ManagerLight.Id, active: com.histmersion.domain.automation.agents.RunService.Active, limit: com.histmersion.domain.automation.agents.RunQuery.Limit): Vector[RunInfo] =
      db.read(AgentRepo.forManager(managerId, active, limit))
    def unsettledOn(target: com.histmersion.domain.automation.agents.AgentsControllers.Target): Vector[RunInfo] = db.read(AgentRepo.unsettledOn(target))
    def checkpoint(jobId: com.histmersion.util.jobs.Job.Id, seq: com.histmersion.domain.automation.agents.AgentRepo.StepRecord.Seq): Option[(com.histmersion.domain.automation.agents.AgentRepo.StepRecord.Name, com.histmersion.domain.automation.agents.AgentRepo.StepRecord.Output)] = db.read(AgentRepo.checkpoint(jobId, seq))
    def attempts(jobId: com.histmersion.util.jobs.Job.Id, seq: com.histmersion.domain.automation.agents.AgentRepo.StepRecord.Seq): com.histmersion.domain.automation.agents.RunService.Count = db.read(AgentRepo.attempts(jobId, seq))
    def record(jobId: com.histmersion.util.jobs.Job.Id, step: AgentRepo.StepRecord, messages: List[AgentRepo.MessageRecord]): Unit =
      db.transact(AgentRepo.record(jobId, step, messages))
    def steps(jobId: com.histmersion.util.jobs.Job.Id, outputChars: com.histmersion.domain.automation.agents.AgentRepo.OutputChars): List[StepInfo] = db.read(AgentRepo.steps(jobId, outputChars))
    def messages(jobId: com.histmersion.util.jobs.Job.Id): List[MessageInfo] = db.read(AgentRepo.messages(jobId))
    def runSpend(jobId: com.histmersion.util.jobs.Job.Id): (Usage, BigDecimal) = db.read(AgentRepo.runSpend(jobId))
    def spendDayMonth(): (BigDecimal, BigDecimal) = db.read(AgentRepo.spendDayMonth())
    def pruneMessages(days: com.histmersion.domain.automation.agents.RunService.RetentionDays): com.histmersion.domain.automation.agents.RunService.Count = db.transact(AgentRepo.pruneMessages(days))

  object Implementation:
    type RunFacetsResultPart1Part1 = RunFacetsResultPart1Part1.T
    object RunFacetsResultPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RunFacetsResultPart2Part1 = RunFacetsResultPart2Part1.T
    object RunFacetsResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RunFacetsResultPart2Part2Part1 = RunFacetsResultPart2Part2Part1.T
    object RunFacetsResultPart2Part2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The runs in memory: their rows are the jobs area's in-memory runs, their steps and traces the shared state;
    * `clock` stands in for the database's `now()`.
    */
  final class InMemory(state: AtomicReference[AutomationState], jobs: JobRuns.InMemory, clock: Clock)
      extends RunService:
    private def now: Instant = Instant.now(clock)
    private val Unsettled = Set("queued", "running", "paused", "waiting")
    private def runs: List[JobRunState] = jobs.all.filter(_.row.kind == JobKind)
    private def stepsOf(jobId: com.histmersion.util.jobs.Job.Id): Vector[StepRecordState] = state.get.steps.filter(_.jobId == jobId)
    private def text(at: Instant): com.histmersion.domain.automation.AutomationState.Timestamp = AutomationState.text(at)
    private def info(r: JobRunState): RunInfo =
      val ss = stepsOf(r.id).map(_.step)
      RunInfo(
        r.id,
        r.row.pipeline,
        r.row.target,
        r.row.status,
        r.progress,
        r.error,
        r.row.model,
        Usage(
          ss.map(_.usage.input).sum,
          ss.map(_.usage.output).sum,
          ss.map(_.usage.cacheRead).sum,
          ss.map(_.usage.cacheWrite).sum
        ),
        ss.map(_.costUsd).sum.toDouble,
        r.row.budgetUsd.toDouble,
        r.row.budgetTokens,
        r.warnings,
        text(r.createdAt),
        text(r.updatedAt),
        r.row.targetKind,
        Jsonb.read[RunParams](r.row.params).getOrElse(RunParams()),
        r.row.acquireItem,
        r.row.goal,
        r.row.parent,
        Some(r.row.origin),
        r.resumeAt.map(text),
        r.row.dryRun,
        r.row.managerId,
        r.row.managerItem,
        r.heartbeatAt.map(text)
      )
    private def key(r: JobRunState): (Instant, Long) = (r.createdAt, r.created)
    private def newest(rs: List[JobRunState]): List[JobRunState] = rs.sortBy(key).reverse

    def get(id: com.histmersion.domain.automation.Run.Id): Option[RunInfo] = jobs.get(id).filter(_.row.kind == JobKind).map(info)
    def list(status: Option[com.histmersion.domain.automation.agents.RunQuery.Status], limit: com.histmersion.domain.automation.agents.RunQuery.Limit, goal: Option[com.histmersion.domain.automation.Goal.Slug]): Vector[RunInfo] =
      val st = status.map(_.trim.toLowerCase).filter(_.nonEmpty)
      val g = goal.map(_.trim).filter(_.nonEmpty)
      newest(runs.filter(r => st.forall(_ == r.row.status) && g.forall(r.row.goal.contains)))
        .take(math.max(1, limit))
        .toVector
        .map(info)
    def page(
        status: Option[com.histmersion.domain.automation.agents.RunQuery.Status],
        limit: com.histmersion.domain.automation.agents.RunQuery.Limit,
        goal: Option[com.histmersion.domain.automation.Goal.Slug],
        before: Option[com.histmersion.domain.automation.agents.AgentRepo.PageCursor],
        upTo: Option[com.histmersion.domain.automation.agents.RunQuery.UpTo]
    ): Either[com.histmersion.domain.automation.agents.AgentRepo.PageCursor, (Vector[RunInfo], Boolean)] =
      page(
        RunQuery(
          status = status.map(_.trim.toLowerCase).filter(_.nonEmpty).toList,
          goal = goal.map(_.trim).filter(_.nonEmpty)
        ),
        limit,
        before,
        upTo
      )

    /** The models a run used: the one it was started with and every one its steps reported. */
    private def models(r: JobRunState): Set[com.histmersion.domain.automation.agents.AgentsControllers.ModelName] = (r.row.model.toList ++ stepsOf(r.id).flatMap(_.step.model)).toSet
    private def provider(model: com.histmersion.domain.automation.agents.AgentsControllers.ModelName): com.histmersion.domain.automation.ProviderKind.Wire = model.takeWhile(_ != ':')

    /** Whether `r` passes every filter of `q` but `except`'s. */
    private def matches(q: RunQuery, except: String)(r: JobRunState): Boolean =
      def any(name: String, vs: List[String], v: => Option[String]) =
        except == name || vs.isEmpty || v.exists(vs.contains)
      any("status", q.status, Some(r.row.status)) &&
      any("pipeline", q.pipelines, Some(r.row.pipeline)) &&
      any("manager", q.managers, r.row.managerId) &&
      (except == "goal" || q.goal.forall(r.row.goal.contains)) &&
      (except == "model" || q.models.isEmpty || models(r).exists(q.models.contains)) &&
      (except == "provider" || q.providers.isEmpty || models(r).map(provider).exists(q.providers.contains)) &&
      any("origin", q.origins, Some(r.row.origin)) &&
      (except == "time" || q.since.forall(s => !r.createdAt.isBefore(s)) && q.until.forall(u =>
        r.createdAt.isBefore(u)
      )) &&
      (except == "q" || q.q.forall(t => r.row.target.toLowerCase.contains(t.toLowerCase)))

    def page(
        q: RunQuery,
        limit: com.histmersion.domain.automation.agents.RunQuery.Limit,
        before: Option[com.histmersion.domain.automation.agents.AgentRepo.PageCursor],
        upTo: Option[com.histmersion.domain.automation.agents.RunQuery.UpTo]
    ): Either[com.histmersion.domain.automation.agents.AgentRepo.PageCursor, (Vector[RunInfo], Boolean)] =
      (before.toList ++ upTo.toList).find(c => jobs.get(c).isEmpty) match
        case Some(c) => Left(s"No agent run '$c' to page from")
        case None    =>
          val ordering = summon[Ordering[(Instant, Long)]]
          val all = newest(runs.filter(matches(q, "")))
          val b = before.flatMap(jobs.get)
          val u = upTo.flatMap(jobs.get)
          val bounded =
            all.filter(r => b.forall(c => ordering.lt(key(r), key(c))) && u.forall(c => ordering.gteq(key(r), key(c))))
          val n = math.max(1, limit)
          val (shown, more) = Paging.cut(bounded.take(n + 1).toVector, n)
          val older =
            if more || u.isEmpty then more
            else shown.lastOption.exists(last => all.exists(r => ordering.lt(key(r), key(last))))
          Right((shown.map(info), older))

    private def facet(values: List[com.histmersion.domain.automation.agents.RunService.InMemory.FacetValues]): List[(com.histmersion.domain.automation.agents.RunService.InMemory.FacetResultPart1, com.histmersion.domain.automation.agents.RunService.Count)] =
      values.groupBy(identity).toList.map((v, n) => (v, n.size)).sortBy((v, n) => (-n, v)).take(ListFilters.FacetMax)
    def runFacets(q: RunQuery): (List[(com.histmersion.domain.automation.agents.RunService.InMemory.RunFacetsResultPart1Part1, com.histmersion.domain.automation.agents.RunService.Count)], List[(com.histmersion.domain.automation.agents.RunService.InMemory.RunFacetsResultPart2Part1, List[(com.histmersion.domain.automation.agents.RunService.InMemory.RunFacetsResultPart2Part2Part1, com.histmersion.domain.automation.agents.RunService.Count)])]) =
      def of(except: String) = runs.filter(matches(q, except))
      (
        facet(of("status").map(_.row.status)),
        List(
          "pipeline" -> facet(of("pipeline").map(_.row.pipeline)),
          "origin" -> facet(of("origin").map(_.row.origin)),
          "manager" -> facet(of("manager").flatMap(_.row.managerId)),
          "model" -> facet(of("model").flatMap(models)),
          "provider" -> facet(of("provider").flatMap(r => models(r).map(provider)))
        )
      )
    def stateCounts(goal: Option[com.histmersion.domain.automation.Goal.Slug]): List[(com.histmersion.domain.automation.Run.State.Wire, com.histmersion.domain.automation.agents.RunService.Count)] =
      val g = goal.map(_.trim).filter(_.nonEmpty)
      facet(runs.filter(r => g.forall(r.row.goal.contains)).map(_.row.status))
    def forTarget(p: Pipeline, target: com.histmersion.domain.automation.agents.AgentsControllers.Target): Vector[RunInfo] =
      runs
        .filter(r => r.row.pipeline == p.wire && r.row.target == target)
        .sortBy(r => (r.createdAt, -r.created))
        .reverse
        .toVector
        .map(info)
    def forManager(managerId: com.histmersion.domain.automation.managers.ManagerLight.Id, active: com.histmersion.domain.automation.agents.RunService.Active, limit: com.histmersion.domain.automation.agents.RunQuery.Limit): Vector[RunInfo] =
      newest(runs.filter(r => r.row.managerId.contains(managerId) && (!active || Unsettled(r.row.status))))
        .take(math.max(1, limit))
        .toVector
        .map(info)
    def unsettledOn(target: com.histmersion.domain.automation.agents.AgentsControllers.Target): Vector[RunInfo] =
      runs.filter(r => r.row.target == target && Unsettled(r.row.status)).toVector.map(info)
    def checkpoint(jobId: com.histmersion.util.jobs.Job.Id, seq: com.histmersion.domain.automation.agents.AgentRepo.StepRecord.Seq): Option[(com.histmersion.domain.automation.agents.AgentRepo.StepRecord.Name, com.histmersion.domain.automation.agents.AgentRepo.StepRecord.Output)] =
      stepsOf(jobId)
        .filter(s => s.step.seq == seq && s.step.status == StepStatus.Done)
        .sortBy(_.step.attempt)
        .lastOption
        .map(s => (s.step.name, s.step.output.getOrElse("null")))
    def attempts(jobId: com.histmersion.util.jobs.Job.Id, seq: com.histmersion.domain.automation.agents.AgentRepo.StepRecord.Seq): com.histmersion.domain.automation.agents.RunService.Count =
      stepsOf(jobId).filter(_.step.seq == seq).map(_.step.attempt).maxOption.getOrElse(0)
    def record(jobId: com.histmersion.util.jobs.Job.Id, step: AgentRepo.StepRecord, messages: List[AgentRepo.MessageRecord]): Unit =
      val at = now
      val _ = state.updateAndGet(
        _.modify(_.steps)
          .using(_ :+ StepRecordState(jobId, step, at))
          .modify(_.messages)
          .using(_ ++ messages.map(m => MessageRecordState(jobId, step.seq, step.attempt, m, at)))
      )
      jobs.stamped(jobId)
    def steps(jobId: com.histmersion.util.jobs.Job.Id, outputChars: com.histmersion.domain.automation.agents.AgentRepo.OutputChars): List[StepInfo] =
      stepsOf(jobId).sortBy(s => (s.step.seq, s.step.attempt)).toList.map { s =>
        val r = s.step
        StepInfo(
          r.seq,
          r.name,
          r.kind.wire,
          r.attempt,
          r.status.wire,
          r.model,
          r.promptHash,
          r.usage,
          r.costUsd.toDouble,
          r.error,
          r.output.map(_.take(outputChars)),
          text(r.startedAt),
          text(s.finishedAt),
          r.actualModel,
          r.wouldWrite,
          r.firstCallTokens,
          r.turns
        )
      }
    def messages(jobId: com.histmersion.util.jobs.Job.Id): List[MessageInfo] =
      state.get.messages
        .filter(_.jobId == jobId)
        .sortBy(m => (m.seq, m.attempt, m.message.n))
        .toList
        .map(m =>
          MessageInfo(
            m.seq,
            m.message.n,
            m.message.role,
            m.message.content,
            m.message.toolName,
            m.message.toolInput,
            m.message.toolResult,
            m.message.latencyMs
          )
        )
    def runSpend(jobId: com.histmersion.util.jobs.Job.Id): (Usage, BigDecimal) =
      val ss = stepsOf(jobId).map(_.step)
      (
        Usage(
          ss.map(_.usage.input).sum,
          ss.map(_.usage.output).sum,
          ss.map(_.usage.cacheRead).sum,
          ss.map(_.usage.cacheWrite).sum
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
    def pruneMessages(days: com.histmersion.domain.automation.agents.RunService.RetentionDays): com.histmersion.domain.automation.agents.RunService.Count =
      if days <= 0 then com.histmersion.domain.automation.agents.RunService.Count(0)
      else
        val cut = now.minusSeconds(days.toLong * 86400L)
        state
          .getAndUpdate(_.modify(_.messages).using(_.filterNot(_.createdAt.isBefore(cut))))
          .messages
          .count(_.createdAt.isBefore(cut))

  object InMemory:

    type FacetResultPart1 = FacetResultPart1.T
    object FacetResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FacetValues = FacetValues.T
    object FacetValues extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RunFacetsResultPart1Part1 = RunFacetsResultPart1Part1.T
    object RunFacetsResultPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RunFacetsResultPart2Part1 = RunFacetsResultPart2Part1.T
    object RunFacetsResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RunFacetsResultPart2Part2Part1 = RunFacetsResultPart2Part2Part1.T
    object RunFacetsResultPart2Part2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
