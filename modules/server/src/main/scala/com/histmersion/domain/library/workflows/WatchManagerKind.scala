package com.histmersion.domain.library.workflows

import com.histmersion.domain.automation.managers.{ManagerContext, ManagerDefaultsViews, ManagerKindDefinition}

import com.histmersion.api.Plural
import com.histmersion.api.library.{Acquire, CandidateSweep}
import com.histmersion.api.automation.Autopilot
import com.histmersion.api.automation.Agents.*
import com.histmersion.api.automation.Managers.*
import com.histmersion.domain.{IngestBacklog, ManagerPolicy, WatchManagerDefaults}
import com.histmersion.domain.ManagerPolicy.{ReviewSettings, StepFacts}
import com.histmersion.config.{AgentsConfig, LibraryAgentsConfig}
import com.histmersion.domain.ManagerDefaults

import hearth.kindlings.optics.syntax.*

import java.time.Instant
import scala.collection.mutable.Builder
import com.histmersion.api.Api

/** The `watch:<goal>` manager kind (an acquisition-sweep and a currency-check item, re-opened by its cron; a sweep
  * ingests legal open-access picks when it opted in, within its daily cap), registered by this transition adapter until
  * the library contributes it. Its housework at every tick: the goals with no source gather candidates (no model), the
  * ingest backlog drains, and the acquisition list's legal re-check runs.
  */
final class WatchManagerKind(site: LibrarySite, val work: AcquisitionWork, lib: => LibraryAgentsConfig)
    extends ManagerKindDefinition:
  val kind: Kind = Kind.Watch

  private def watch = lib.managers.watch
  private def watchDefaults = WatchManagerDefaults(watch.maxRuns, watch.autoIngestPerDay)

  override def cron: Option[String] = Some(watch.cron)

  def defaults(cfg: AgentsConfig, m: ManagerDefaults): ManagerConfig = watchDefaults.managerDefaults(m)

  def pipelineOf(itemKind: String): Pipeline = itemKind match
    case AcquisitionSweep => Pipeline.AcquisitionSweep
    case CurrencyCheck    => Pipeline.CurrencyCheck
    case _                => Pipeline.Fallback

  /** A firing re-opens each of the goal's sweeps that has settled (one still open is left to finish). */
  def fire(c: ManagerContext, m: ManagerInfo, now: Instant): List[String] =
    m.goal.toList.flatMap { g =>
      Sweep.of(m.config).map { sw =>
        val open = c
          .itemFor(m.id, sw.itemKind, g)
          .exists(i => !ItemStatus.parse(i.status).exists(_.isFinal))
        if open then s"${sw.itemKind} still open"
        else
          val _ = c.upsert(m.id, sw.itemKind, g, ItemOrigin.Cron, 0, reopen = true)
          s"${sw.itemKind} due"
      }
    }

  override def housework(now: Instant, draining: => Boolean): List[(String, () => List[String])] =
    List(
      // seed before the plan: sourceless goals queue legal open-access candidates (no model), then the drain
      "seed" -> (() => work.seeding.tick(now)),
      // the watchers' ingest backlog, before any sweep is dispatched (a sweep gets what it leaves)
      "ingest-backlog" -> (() => if draining then Nil else work.oaBacklog.drain(now)),
      // the acquisition list's legal re-check: after the backlog, which keeps the shared allowance first
      "acquire-recheck" -> (() => work.acquireRecheck.tick(now))
    )

  def facts(c: ManagerContext, item: ManagerItem, run: String): StepFacts =
    item.kind match
      case AcquisitionSweep | CurrencyCheck =>
        StepFacts.Finished(closeLine(c, run).getOrElse(s"${item.kind} done — ${c.progress(run)}").take(1000))
      case _ => StepFacts.Finished("enriched")

  /** What the sweep found, as the watcher's own event (the run's worklog line, and the works it picked). */
  override def afterDone(
      c: ManagerContext,
      m: ManagerInfo,
      item: ManagerItem,
      run: Option[String],
      out: Builder[String, List[String]]
  ): Unit =
    if item.kind == AcquisitionSweep || item.kind == CurrencyCheck then
      run.foreach { r =>
        val found = if item.kind == AcquisitionSweep then AcquisitionSweepPipeline.picked(c, r) else Nil
        val works = if found.isEmpty then ""
        else
          s"; picked: ${found
              .map(p =>
                s"${p.candidate.title} (https://doi.org/${p.candidate.doi}${
                    if p.candidate.isOa then ", open access" else ""
                  })"
              )
              .mkString("; ")}"
        c.event(
          m.id,
          Some(item.id),
          Some(r),
          Feed.Swept,
          (closeLine(c, r).getOrElse(s"${item.kind} of ${item.target} done") + works).take(2000)
        )
      }

  /** The worklog line a sweep's `close` step wrote (what it found, queued, ingested). */
  private def closeLine(c: ManagerContext, run: String): Option[String] =
    c.stepOutput(run, "close").flatMap(o => io.circe.parser.decode[String](o).toOption)

  def target(m: ManagerInfo, itemKind: String, raw: String): Either[Api.Error, String] =
    itemKind match
      case OaIngest =>
        Acquire
          .normalizeDoi(raw)
          .toRight(Api.Error(s"'$raw' is not a DOI (an ingest-backlog item is one legal open-access work)"))
      case AcquisitionSweep | CurrencyCheck =>
        val g = Option(raw.trim).filter(_.nonEmpty).orElse(m.goal).getOrElse("")
        work.agents.host
          .goal(g)
          .map(_.slug)
          .flatMap(s =>
            Either
              .cond(m.goal.forall(_ == s), s, Api.Error(s"manager ${m.id} watches ${m.goal.getOrElse("?")}, not $s"))
          )
      case other => Left(Api.Error(s"unknown item kind '$other'"))

  /** Each watcher's ingest backlog and the days it takes to drain. */
  override def backlog(): Map[String, (Int, Option[Int])] = work.oaBacklog.snapshot().perManager

  /** The ingest backlog's size (the page adds the upstreams' quotas and the acquisition list's re-check). */
  override def status(st: Autopilot.EngineStatus, now: Instant): Autopilot.EngineStatus =
    val backlog = work.oaBacklog.snapshot(now)
    st.modify(_.backlog)
      .setTo(backlog.total)
      .modify(_.backlogDays)
      .setTo(backlog.totalDays)

  /** The metered upstreams' budgets the autopilot's page shows. */
  def upstreams(now: Instant): List[com.histmersion.api.library.UpstreamQuota] = site.upstreamQuotas(now)

  /** The acquisition list's re-check's finds count against the shared ingest cap too. */
  override def ingestedToday(now: Instant): Int = work.acquireRecheck.fetchedToday(now)

  override def busy: Boolean = work.acquireRecheck.jobRunning

  def dispatch(
      c: ManagerContext,
      m: ManagerInfo,
      s: ReviewSettings,
      item: ManagerItem,
      now: Instant,
      out: Builder[String, List[String]]
  ): Option[String] =
    item.kind match
      case AcquisitionSweep | CurrencyCheck =>
        work.agents.host.goal(item.target) match
          case Left(_)  => c.settleNow(m, item, ItemStatus.Cancelled, "the goal no longer exists")
          case Right(g) =>
            // the deterministic search first (#3761, no model): no new candidate, no agent run — the schedule
            // stops spending model quota on sweeps that change nothing
            val swept =
              Option.when(item.kind == AcquisitionSweep)(work.acquisition.sweepCandidates(g.slug, dryRun = false))
            swept.filter(WatchManagerKind.nothingNew).flatMap(_.toOption) match
              case Some(s) =>
                c.settleNow(
                  m,
                  item,
                  ItemStatus.Done,
                  s"no new candidates from the goal's watches (${Plural.count(s.watches.size, "watch", "watches")} " +
                    s"read, ${s.found} found, all known) — no agent run"
                )
              case None =>
                val sweep = item.kind == AcquisitionSweep
                val autoOn = sweep && m.config.autoIngest.contains(true)
                val own = if sweep then
                  watchDefaults.ingestAllowance(
                    m.config,
                    c.countEvents(m.id, Feed.Ingested, ManagerPolicy.dayStart(now))
                  )
                else 0
                // the autopilot's cap shared by every watcher (none while it is off)
                val shared = if autoOn then c.sharedIngestLeft(now) else None
                // the ingest backlog drains first: a sweep's new finds get what it leaves of the allowance
                val (mine, all) = if autoOn then work.oaBacklog.reserved(m.id) else (0, 0)
                val allowed = if autoOn then IngestBacklog.sweepAllowance(own, shared, mine, all) else 0
                val auto = if !sweep then ""
                else if autoOn then
                  s"; auto-ingest of legal open access: ${Plural.count(allowed, "work")} left today" +
                    shared.fold("")(n => s" (all watchers: $n left)") +
                    (if all > 0 then
                       s"; ${Plural.count(all, "work")} wait in the ingest backlog first" +
                         (if mine > 0 then s" ($mine of them its own)" else "")
                     else "") +
                    "; the open-access picks past it wait in the backlog"
                else "; auto-ingest off"
                c.start(
                  m,
                  item,
                  RunRequest(
                    pipelineOf(item.kind).wire,
                    g.slug,
                    budgetUsd = m.config.budgetUsd
                  ),
                  p =>
                    if autoOn then p.copy(autoIngest = Option.when(allowed > 0)(allowed), oaBacklog = Some(true))
                    else p,
                  item.state,
                  s"${item.kind}$auto",
                  out
                )
      case other =>
        c.settleNow(m, item, ItemStatus.Cancelled, s"'$other' items are not driven by a ${m.kind} manager")

object WatchManagerKind:

  /** Pure: does a candidate sweep say there is nothing for a ranking (#3761)? Only an answered sweep with no unseen
    * candidate does; a search that is not connected (or failed) lets the run start as before.
    */
  def nothingNew(sweep: Either[String, CandidateSweep]): Boolean = sweep.exists(_.unseen == 0)
