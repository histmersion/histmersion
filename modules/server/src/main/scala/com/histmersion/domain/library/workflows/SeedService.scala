package com.histmersion.domain.library.workflows

import com.histmersion.domain.automation.agents.AgentsControllers

import com.histmersion.api.Plural
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Managers.*
import com.histmersion.api.automation.Queue.QueueAdd
import com.histmersion.domain.automation.goals.FocusService
import com.histmersion.domain.library.acquisition.CandidateService

import java.time.Instant
import com.histmersion.api.library.Candidates

/** SEED BEFORE THE PLAN (#3762 proposal 5): a research goal with no source does not wait for its plan to be approved
  * before it gathers. Each managers' tick (the autopilot on, not draining), up to
  * `agents.scheduler.seed-goals-per-tick` such goals — the scheduler's goal order: a pin, a person's priority, the
  * oldest — each at most once per `seed-every-hours`, run the DETERMINISTIC candidate search (#3761: the goal's
  * queries, else its title, on the scholarly indexes; no model) and put up to `seed-ingest-per-goal` of the new legal
  * open-access candidates into the goal's watcher's ingest backlog, which the tick drains within the day's ingest caps
  * through the legal chain only. Nothing here calls a model or takes a place in the scheduler: its only cost is the
  * index requests and the ingests' own caps. A goal without a watcher yet (the autopilot makes one) is left for the
  * next tick.
  */
final class SeedService(
    candidates: CandidateService,
    cfg: => AgentsConfig,
    site: LibrarySite,
    agents: AgentsControllers,
    acquisition: () => AgentUpstream,
    backlog: OaBacklog
):

  /** The goals to seed now, in the scheduler's order: unfinished, no source linked, a watcher, not swept in
    * `seed-every-hours`. Pure over what it is given.
    */
  def due(
      goals: List[FocusService.Goal],
      sources: Map[com.histmersion.domain.library.Citekey, com.histmersion.domain.library.workflows.SeedService.DueDays],
      watchers: Set[com.histmersion.domain.library.workflows.SeedService.Watcher],
      lastSweep: com.histmersion.domain.library.workflows.SeedService.LastSweep => Option[Instant],
      now: Instant
  ): List[com.histmersion.domain.library.workflows.SeedService.DueText] =
    val every = math.max(1, cfg.scheduler.seedEveryHours).toLong * 3600L
    goals
      .filter(g => !GoalFocus.finished(g.status) && sources.getOrElse(g.slug, 0) == 0 && watchers(watchId(g.slug)))
      .filter(g => lastSweep(g.slug).forall(t => !t.plusSeconds(every).isAfter(now)))
      .sortBy(g => (-(g.priority + (if g.pinned then SchedulerPolicy.PinBoost else 0)), g.createdAt, g.slug))
      .map(_.slug)

  /** When each goal was last seeded here (in memory: the candidate watches' own `last_run` covers restarts). */
  private val seeded = java.util.concurrent.ConcurrentHashMap[String, Instant]()

  /** One tick's seeding; one line per goal seeded. */
  def tick(now: Instant): List[com.histmersion.domain.library.workflows.SeedService.TickLine] =
    val n = cfg.scheduler.seedGoalsPerTick
    if n <= 0 || !agents.autopilot.on || agents.drain.draining then Nil
    else
      val watchers =
        agents.managerStore
          .light()
          .filter(m => m.kind == Kind.Watch.wire && m.state == State.Active.wire)
          .map(_.id)
          .toSet
      val goals = due(
        agents.focusStore.goals(),
        agents.focusStore.sourceCounts(),
        watchers,
        g =>
          (candidates.watches(g).flatMap(_.lastRun).flatMap(ManagerPolicy.parseInstant) ++ Option(
            seeded.get(g)
          )).maxOption,
        now
      )
      val lines = goals.take(n).flatMap { g =>
        val _ = seeded.put(g, now)
        seed(g)
      }
      if lines.nonEmpty then
        val _ = site.workLists.add(com.histmersion.domain.automation.worklists.WorkListControllers.ListName("log"), QueueAdd(s"seed before the plan: ${lines.mkString("; ")}".take(1000)))
      lines

  /** Sweep one goal's candidates and queue its legal open-access ones for ingest. */
  def seed(goal: com.histmersion.domain.automation.Goal.Slug): Option[com.histmersion.domain.library.workflows.SeedService.Seeded] =
    acquisition().sweepCandidates(goal, dryRun = com.histmersion.domain.library.workflows.AgentUpstream.SweepCandidatesDryRun(false)) match
      case Left(why) =>
        scribe.info(s"seed before the plan: goal $goal not swept: $why")
        None
      case Right(sw) =>
        val fresh = acquisition().candidates(goal, Candidates.State.New.wire, com.histmersion.domain.library.workflows.AgentUpstream.Limit(200)).getOrElse(Nil)
        val oa = fresh.filter(_.isOa).take(math.max(0, cfg.scheduler.seedIngestPerGoal))
        val placed = oa.map { c =>
          c -> backlog.add(
            watchId(goal),
            goal,
            OaWork(
              c.doi,
              c.title,
              c.year,
              c.authors,
              c.venue,
              List(goal),
              "seed before the plan: a legal open-access candidate of the goal's own search (no model)"
            )
          )
        }
        val queued = placed.count(_._2 == IngestBacklog.Placement.New)
        val _ = acquisition().markCandidates(oa.map(_.id), Candidates.State.Queued.wire)
        val line = s"$goal: ${sw.found} found, ${sw.added} new candidate(s), " +
          s"${Plural.count(queued, com.histmersion.api.Plural.CountSingular("legal open-access work"))} queued for ingest (the plan is not needed for this)"
        agents.managerStore.event(watchId(goal), None, None, Feed.Swept, s"seed before the plan — $line".take(2000))
        Some(line)

object SeedService:
  type DueDays = DueDays.T
  object DueDays extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DueText = DueText.T
  object DueText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LastSweep = LastSweep.T
  object LastSweep extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Seeded = Seeded.T
  object Seeded extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TickLine = TickLine.T
  object TickLine extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Watcher = Watcher.T
  object Watcher extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
