package com.histmersion
package domain.library.workflows
import com.histmersion.domain.automation.workflows.InTreeWorkflow
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.RawJson
import com.histmersion.util.problem.{Legacy, Problem, Problems}

import com.histmersion.domain.automation.agents.AgentRepo
import com.histmersion.domain.automation.managers.ManagerContext
import com.histmersion.domain.automation.managers.ManagerDefaultsViews
import com.histmersion.domain.automation.managers.ManagerKindDefinition
import com.histmersion.domain.automation.managers.ManagerLight

import com.histmersion.api.Plural
import com.histmersion.api.library.{Acquire, CandidateSweep}
import com.histmersion.api.automation.Autopilot
import com.histmersion.api.automation.Agents.*
import com.histmersion.api.automation.Managers.*
import com.histmersion.domain.IngestBacklog
import com.histmersion.domain.ManagerPolicy
import com.histmersion.domain.WatchManagerDefaults
import com.histmersion.domain.ManagerPolicy.ReviewSettings
import com.histmersion.domain.ManagerPolicy.StepFacts
import com.histmersion.config.{AgentsConfig, LibraryAgentsConfig}
import com.histmersion.domain.ManagerDefaults

import hearth.kindlings.optics.syntax.*

import java.time.Instant
import scala.collection.mutable.Builder
import com.histmersion.api.Api
import com.histmersion.domain.automation.Run
import com.histmersion.domain.library.Doi
import com.histmersion.domain.automation.Goal
import com.histmersion.api.library.UpstreamQuota
import com.histmersion.domain.ManagerPolicy.Item
import com.histmersion.util.database.WritePermit
import com.histmersion.domain.Creator
import com.histmersion.lint.{tolerated, LintRule}

/** The `watch:<goal>` manager kind (an acquisition-sweep and a currency-check item, re-opened by its cron; a sweep
  * ingests legal open-access picks when it opted in, within its daily cap), registered by this transition adapter until
  * the library contributes it. Its housework at every tick: the goals with no source gather candidates (no model), the
  * ingest backlog drains, and the acquisition list's legal re-check runs.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@tolerated(LintRule.Repository, "pending G11 (a repository stays inside its feature)")
final class WatchManagerKind(site: LibrarySite, val work: AcquisitionWork, lib: => LibraryAgentsConfig)
    extends ManagerKindDefinition:
  val kind: Kind = Kind.Watch
  val creator: domain.Creator.New =
    domain.Creator.code(InTreeWorkflow.Library, domain.Creator.Workflow.Name("watch-manager"))

  private def watch = lib.managers.watch
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def watchDefaults = WatchManagerDefaults(
    WatchManagerDefaults.MaxRuns(watch.maxRuns.value),
    WatchManagerDefaults.AutoIngestPerDay(watch.autoIngestPerDay.value)
  )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  override def cron: Option[ManagerLight.Cron] = Some(ManagerLight.Cron(watch.cron.value))

  def defaults(cfg: AgentsConfig, m: ManagerDefaults): ManagerConfig = watchDefaults.managerDefaults(m)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def pipelineOf(itemKind: ManagerContext.ItemKind): Pipeline = itemKind.value match
    case AcquisitionSweep => Pipeline.AcquisitionSweep
    case CurrencyCheck    => Pipeline.CurrencyCheck
    case _                => Pipeline.Fallback

  /** A firing re-opens each of the goal's sweeps that has settled (one still open is left to finish). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def fire(c: ManagerContext, m: ManagerInfo, now: Instant)(using WritePermit): List[ManagerKindDefinition.FirePhrase] =
    m.goal.toList.flatMap { g =>
      Sweep.of(m.config).map { sw =>
        val id = ManagerLight.Id(m.id.value)
        val itemKind = ManagerContext.ItemKind(sw.itemKind.value)
        val target = ManagerContext.ItemTarget(g.value)
        val open = c
          .itemFor(id, itemKind, target)
          .exists(i => !ItemStatus.parse(i.status).exists(_.isFinal))
        ManagerKindDefinition.FirePhrase(
          if open then s"${sw.itemKind} still open"
          else
            val _ = c.upsert(
              id,
              itemKind,
              target,
              ItemOrigin.Cron,
              ManagerContext.Priority(0),
              reopen = ManagerContext.UpsertReopen.Reopen
            )
            s"${sw.itemKind} due"
        )
      }
    }

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  override def housework(
      now: Instant,
      draining: => Boolean
  )(using WritePermit)(using Creator.New): List[(ManagerLight.Id, () => List[ManagerContext.TickLine])] =
    def lines(f: => List[String]): () => List[ManagerContext.TickLine] =
      () => f.map(ManagerContext.TickLine(_))
    List(
      // seed before the plan: sourceless goals queue legal open-access candidates (no model), then the drain
      ManagerLight.Id("seed") -> lines(work.seeding.tick(now)),
      // the watchers' ingest backlog, before any sweep is dispatched (a sweep gets what it leaves)
      ManagerLight.Id("ingest-backlog") -> lines(if draining then Nil else work.oaBacklog.drain(now)),
      // the acquisition list's legal re-check: after the backlog, which keeps the shared allowance first
      ManagerLight.Id("acquire-recheck") -> lines(work.acquireRecheck.tick(now).map(_.value))
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def facts(c: ManagerContext, item: ManagerItem, run: Run.Id): StepFacts =
    item.kind.value match
      case AcquisitionSweep | CurrencyCheck =>
        StepFacts.Finished(
          Item.Outcome(closeLine(c, run).getOrElse(s"${item.kind} done — ${c.progress(run)}").take(1000))
        )
      case _ => StepFacts.Finished(Item.Outcome("enriched"))

  /** What the sweep found, as the watcher's own event (the run's worklog line, and the works it picked). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  override def afterDone(
      c: ManagerContext,
      m: ManagerInfo,
      item: ManagerItem,
      run: Option[Run.Id],
      out: Builder[ManagerContext.TickLine, List[ManagerContext.TickLine]]
  )(using WritePermit)(using Creator.New): Unit =
    if item.kind.value == AcquisitionSweep || item.kind.value == CurrencyCheck then
      run.foreach { r =>
        val found = if item.kind.value == AcquisitionSweep then AcquisitionSweepPipeline.picked(c, r) else Nil
        val works = if found.isEmpty then ""
        else
          s"; picked: ${found
              .map(p =>
                s"${p.candidate.title} (https://doi.org/${p.candidate.doi}${
                    if p.candidate.isOa.isOa then ", open access" else ""
                  })"
              )
              .mkString("; ")}"
        c.event(
          ManagerLight.Id(m.id.value),
          Some(ManagerContext.ItemId(item.id.value)),
          Some(r),
          ManagerContext.EventFeed(Feed.Swept),
          ManagerContext.EventText(
            (closeLine(c, r).getOrElse(s"${item.kind} of ${item.target} done") + works).take(2000)
          )
        )
      }

  /** The worklog line a sweep's `close` step wrote (what it found, queued, ingested). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def closeLine(c: ManagerContext, run: Run.Id): Option[String] =
    c.stepOutput(run, AgentRepo.StepRecord.Name("close")).flatMap(o => RawJson.decode[String](o.value).toOption)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def target(
      m: ManagerInfo,
      itemKind: ManagerContext.ItemKind,
      raw: ManagerItem.Target
  ): Either[Api.Error, ManagerContext.ItemTarget] =
    itemKind.value match
      case OaIngest =>
        Doi
          .normalize(raw.value)
          .map(d => ManagerContext.ItemTarget(d.value))
          .toRight(
            Api.Error(
              Problem.Unclassified(
                Legacy.Text(s"'$raw' is not a DOI (an ingest-backlog item is one legal open-access work)")
              )
            )
          )
      case AcquisitionSweep | CurrencyCheck =>
        val g = Option(raw.value.trim).filter(_.nonEmpty).orElse(m.goal.map(_.value)).getOrElse("")
        Goal.Slug
          .option(g)
          .toRight(Api.Error(Problem.NotFound.Entity(Problem.TypeName.of(Goal.Slug), Problem.Raw(s"$g"))))
          .flatMap(work.agents.host.goal(_))
          .map(_.slug)
          .flatMap(s =>
            Either
              .cond(
                m.goal.forall(_ == s),
                ManagerContext.ItemTarget(s.value),
                Api.Error(
                  Problem.Unclassified(Legacy.Text(s"manager ${m.id} watches ${m.goal.fold("?")(_.value)}, not $s"))
                )
              )
          )
      case other => Left(Api.Error(Problem.Unclassified(Legacy.Text(s"unknown item kind '$other'"))))

  /** Each watcher's ingest backlog and the days it takes to drain. */
  override def backlog(): Map[ManagerLight.Id, (IngestBacklog.Lane.Backlog, Option[IngestBacklog.DaysToDrain])] =
    work.oaBacklog.snapshot().perManager

  /** The ingest backlog's size (the page adds the upstreams' quotas and the acquisition list's re-check). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  override def status(st: Autopilot.EngineStatus, now: Instant): Autopilot.EngineStatus =
    val backlog = work.oaBacklog.snapshot(now)
    st.modify(_.backlog)
      .setTo(Autopilot.EngineStatus.Backlog(backlog.total.value))
      .modify(_.backlogDays)
      .setTo(backlog.totalDays.map(d => Autopilot.EngineStatus.BacklogDays(d.value)))

  /** The metered upstreams' budgets the autopilot's page shows. */
  def upstreams(now: Instant): List[UpstreamQuota] = site.upstreamQuotas(now)

  /** The acquisition list's re-check's finds count against the shared ingest cap too. */
  override def ingestedToday(now: Instant): RowCount =
    work.acquireRecheck.fetchedToday(now)

  override def busy: Boolean = work.acquireRecheck.jobRunning

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def dispatch(
      c: ManagerContext,
      m: ManagerInfo,
      s: ReviewSettings,
      item: ManagerItem,
      now: Instant,
      out: Builder[ManagerContext.TickLine, List[ManagerContext.TickLine]]
  )(using WritePermit)(using Creator.New): Option[ManagerLight.Id] =
    item.kind.value match
      case AcquisitionSweep | CurrencyCheck =>
        Goal.Slug.option(item.target.value).flatMap(work.agents.host.goal(_).toOption) match
          case None =>
            c.settleNow(m, item, ItemStatus.Cancelled, ManagerContext.SettleNowWhy("the goal no longer exists"))
          case Some(g) =>
            // the deterministic search first (#3761, no model): no new candidate, no agent run — the schedule
            // stops spending model quota on sweeps that change nothing
            val swept =
              Option.when(item.kind.value == AcquisitionSweep)(
                work.acquisition
                  .sweepCandidates(Goal.Slug.assume(g.slug.value), dryRun = AgentUpstream.SweepCandidatesDryRun.Applied)
              )
            swept.filter(WatchManagerKind.nothingNew).flatMap(_.toOption) match
              case Some(s) =>
                c.settleNow(
                  m,
                  item,
                  ItemStatus.Done,
                  ManagerContext.SettleNowWhy(
                    s"no new candidates from the goal's watches (${Plural.count(Plural.CountN(s.watches.size.toLong), Plural.CountSingular("watch"), Plural.CountPlural("watches"))} " +
                      s"read, ${s.found} found, all known) — no agent run"
                  )
                )
              case None =>
                val sweep = item.kind.value == AcquisitionSweep
                val autoOn = sweep && m.config.autoIngest.contains(ManagerConfig.AutoIngest.Auto)
                val managerId = ManagerLight.Id(m.id.value)
                val own = IngestBacklog.OwnAllowance(
                  if sweep then
                    watchDefaults
                      .ingestAllowance(
                        m.config,
                        WatchManagerDefaults.IngestedToday(
                          c.countEvents(managerId, ManagerContext.EventFeed(Feed.Ingested), ManagerPolicy.dayStart(now))
                            .value
                        )
                      )
                      .value
                  else 0
                )
                // the autopilot's cap shared by every watcher (none while it is off)
                val shared =
                  if autoOn then c.sharedIngestLeft(now).map(n => IngestBacklog.SharedAllowance(n.value)) else None
                // the ingest backlog drains first: a sweep's new finds get what it leaves of the allowance
                val (mine, all) =
                  if autoOn then work.oaBacklog.reserved(managerId)
                  else (IngestBacklog.Lane.Backlog(0), IngestBacklog.TotalBacklog(0))
                val allowed = if autoOn then IngestBacklog.sweepAllowance(own, shared, mine, all).value else 0
                val auto = if !sweep then ""
                else if autoOn then
                  s"; auto-ingest of legal open access: ${Plural.count(Plural.CountN(allowed.toLong), Plural.CountSingular("work"))} left today" +
                    shared.fold("")(n => s" (all watchers: $n left)") +
                    (if all.value > 0 then
                       s"; ${Plural.count(Plural.CountN(all.value.toLong), Plural.CountSingular("work"))} wait in the ingest backlog first" +
                         (if mine.value > 0 then s" (${mine.value} of them its own)" else "")
                     else "") +
                    "; the open-access picks past it wait in the backlog"
                else "; auto-ingest off"
                c.start(
                  m,
                  item,
                  RunRequest(
                    RunRequest.Pipeline(pipelineOf(ManagerContext.ItemKind(item.kind.value)).wire.value),
                    RunRequest.Target(g.slug.value),
                    budgetUsd = m.config.budgetUsd.map(b => RunRequest.BudgetUsd(b.value))
                  ),
                  p =>
                    if autoOn then
                      p.modify(_.autoIngest)
                        .setTo(Option.when(allowed > 0)(RunParams.AutoIngest(allowed)))
                        .modify(_.oaBacklog)
                        .setTo(Some(RunParams.OaBacklog.Backlog))
                    else p,
                  item.state,
                  ManagerContext.StartWhat(s"${item.kind}$auto"),
                  out
                )
      case other =>
        c.settleNow(
          m,
          item,
          ItemStatus.Cancelled,
          ManagerContext.SettleNowWhy(s"'$other' items are not driven by a ${m.kind} manager")
        )

object WatchManagerKind:
  /** Pure: does a candidate sweep say there is nothing for a ranking (#3761)? Only an answered sweep with no unseen
    * candidate does; a search that is not connected (or failed) lets the run start as before.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def nothingNew(sweep: Either[Problems, CandidateSweep]): Boolean = sweep.exists(_.unseen.value == 0)
