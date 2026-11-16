package com.histmersion
package domain.library.workflows
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.{sentence, sentences}

import com.histmersion.domain.automation.agents.AgentsControllers

import com.histmersion.api.Plural
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Managers.*
import com.histmersion.api.automation.Queue
import com.histmersion.api.automation.Queue.QueueAdd
import com.histmersion.domain.automation.goals.FocusService
import com.histmersion.domain.library.acquisition.CandidateService

import java.time.Instant
import com.histmersion.api.library.Candidates
import com.histmersion.domain.automation.Goal
import com.histmersion.domain.automation.worklists.WorkListControllers
import com.histmersion.api.automation.Managers

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
      sources: Map[Goal.Slug, RowCount],
      watchers: Set[ManagerInfo.Id],
      lastSweep: Goal.Slug => Option[Instant],
      now: Instant
  ): List[Goal.Slug] =
    val every = math.max(1, cfg.scheduler.seedEveryHours.value).toLong * 3600L
    goals
      .map(g => g -> Goal.Slug.assume(g.slug.value))
      .filter((g, slug) =>
        !GoalFocus.finished(Goal.Status.Wire(g.status.value)) &&
          sources.get(slug).fold(0)(_.value) == 0 && watchers(watcherOf(slug))
      )
      .filter((_, slug) => lastSweep(slug).forall(t => !t.plusSeconds(every).isAfter(now)))
      .sortBy((g, slug) =>
        (
          -(g.priority.value + (if g.pinned.value then SchedulerPolicy.PinBoost else 0)),
          g.createdAt.value: String,
          slug.value: String
        )
      )
      .map(_._2)

  /** The id of a goal's watcher manager. */
  private def watcherOf(goal: Goal.Slug): ManagerInfo.Id =
    watchId(com.histmersion.api.automation.Goal.Ref(goal.value))

  /** When each goal was last seeded here (in memory: the candidate watches' own `last_run` covers restarts). */
  private val seeded = java.util.concurrent.ConcurrentHashMap[String, Instant]()

  /** One tick's seeding; one line per goal seeded. */
  def tick(now: Instant): List[String] =
    val n = cfg.scheduler.seedGoalsPerTick.value
    if n <= 0 || !agents.autopilot.on || agents.drain.draining then Nil
    else
      val watchers =
        agents.managerStore
          .light()
          .filter(m => m.kind.value == Kind.Watch.wire.value && m.state.value == State.Active.wire.value)
          .map(m => ManagerInfo.Id(m.id.value))
          .toSet
      val goals = due(
        agents.focusStore.goals(),
        agents.focusStore.sourceCounts().map((g, c) => Goal.Slug.assume(g.value) -> RowCount(c.value)),
        watchers,
        g =>
          (candidates
            .watches(g)
            .flatMap(_.lastRun)
            .flatMap(l => ManagerPolicy.parseInstant(ManagerPolicy.InstantText(l.value))) ++ Option(
            seeded.get(g.value)
          )).maxOption,
        now
      )
      val lines = goals.take(n).flatMap { g =>
        val _ = seeded.put(g.value, now)
        seed(g)
      }
      if lines.nonEmpty then
        val _ = site.workLists.add(
          WorkListControllers.ListName("log"),
          QueueAdd(Queue.QueueItem.Text(s"seed before the plan: ${lines.mkString("; ")}".take(1000)))
        )
      lines

  /** Sweep one goal's candidates and queue its legal open-access ones for ingest. */
  def seed(goal: Goal.Slug): Option[String] =
    acquisition().sweepCandidates(goal, dryRun = AgentUpstream.SweepCandidatesDryRun(false)) match
      case Left(why) =>
        scribe.info(s"seed before the plan: goal $goal not swept: ${why.sentences.mkString("; ")}")
        None
      case Right(sw) =>
        val fresh = acquisition().candidates(goal, Candidates.State.New.wire, AgentUpstream.Limit(200)).getOrElse(Nil)
        val oa = fresh.filter(_.isOa.value).take(math.max(0, cfg.scheduler.seedIngestPerGoal.value))
        val placed = oa.map { c =>
          c -> backlog.add(
            watcherOf(goal),
            goal,
            OaWork(
              c.doi,
              c.title,
              c.year,
              c.authors,
              c.venue,
              List(com.histmersion.api.automation.Goal.Slug(goal.value)),
              Managers.Rationale(
                "seed before the plan: a legal open-access candidate of the goal's own search (no model)"
              )
            )
          )
        }
        val queued = placed.count(_._2 == IngestBacklog.Placement.New)
        val _ = acquisition().markCandidates(oa.map(_.id), Candidates.State.Queued.wire)
        val line = s"$goal: ${sw.found} found, ${sw.added} new candidate(s), " +
          s"${Plural.count(Plural.CountN(queued.toLong), Plural.CountSingular("legal open-access work"))} queued for ingest (the plan is not needed for this)"
        agents.managerStore.event(
          watcherOf(goal),
          None,
          None,
          ManagerEvent.Kind(Feed.Swept),
          ManagerEvent.Text(s"seed before the plan — $line".take(2000))
        )
        Some(line)
