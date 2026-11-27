package com.histmersion
package domain.library.workflows
import com.histmersion.domain.automation.workflows.InTreeWorkflow
import com.histmersion.util.problem.ProblemLog
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence

import io.scalaland.chimney.dsl.*

import com.histmersion.domain.automation.agents.AgentsControllers
import com.histmersion.domain.automation.managers.ManagerContext
import com.histmersion.domain.automation.managers.ManagerKindDefinition
import com.histmersion.domain.automation.managers.ManagerLight
import com.histmersion.domain.library.Citekey

import com.histmersion.api.Write
import com.histmersion.api.automation.Agents.*
import com.histmersion.api.automation.Managers.*
import com.histmersion.domain.AutoEnrich
import com.histmersion.domain.ManagerPolicy.ReviewSettings
import com.histmersion.domain.ManagerPolicy.StepFacts
import com.histmersion.config.AgentsConfig
import com.histmersion.domain.ManagerDefaults

import java.time.Instant
import scala.collection.mutable.Builder
import com.histmersion.api.Api
import com.histmersion.api.automation.Goal
import com.histmersion.domain.automation.Manager
import com.histmersion.domain.automation.Run
import com.histmersion.domain.ManagerPolicy
import com.histmersion.domain.library.sources.SourcesControllers
import com.histmersion.domain.research.Slug
import com.histmersion.domain.ManagerPolicy.Item
import com.histmersion.util.database.WritePermit
import com.histmersion.util.database.Unused
import com.histmersion.domain.Creator
import com.histmersion.lint.{tolerated, LintRule}

/** The `enrich` manager kind (library-wide: source-enrich items, what `agents.auto-enrich` feeds), registered by this
  * transition adapter until the library contributes it: a source already enriched is settled without a run, unless a
  * person asked.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final class EnrichManagerKind(site: LibrarySite, a: AgentsControllers) extends ManagerKindDefinition:
  val kind: Kind = Kind.Enrich
  val creator: domain.Creator.New =
    domain.Creator.code(InTreeWorkflow.Library, domain.Creator.Workflow.Name("enrich-manager"))

  def defaults(cfg: AgentsConfig, m: ManagerDefaults): ManagerConfig =
    ReviewSettings.defaultsFor(kind.transformInto[Manager.Kind], m)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def pipelineOf(itemKind: ManagerContext.ItemKind): Pipeline = itemKind.value match
    case SourceEnrich => Pipeline.EnrichSource
    case _            => Pipeline.Fallback

  def fire(c: ManagerContext, m: ManagerInfo, now: Instant)(using WritePermit): List[ManagerKindDefinition.FirePhrase] =
    Nil

  def facts(c: ManagerContext, item: ManagerItem, run: Run.Id): StepFacts =
    StepFacts.Finished(Item.Outcome("enriched"))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def target(
      m: ManagerInfo,
      itemKind: ManagerContext.ItemKind,
      raw: ManagerItem.Target
  ): Either[Api.Error, ManagerContext.ItemTarget] =
    itemKind.value match
      case SourceEnrich =>
        Citekey.option(raw.value.trim) match
          case None =>
            Left(Api.Error(Problem.NotFound.Entity(Problem.TypeName.of(Citekey), Problem.Raw(s"${raw.value.trim}"))))
          case Some(ck) =>
            site.sources
              .get(ck, body = SourcesControllers.GetBody(false))
              .left
              .map(Api.Error(_))
              .map(s => ManagerContext.ItemTarget(s.source.citekey.value))
      case other => Left(Api.Error(Problem.Unclassified(Legacy.Text(s"unknown item kind '$other'"))))

  /** What an Enrich button names: a source itself (enriched again even if it was), or the sources an article cites / a
    * goal links that are NOT enriched yet ([[AutoEnrich.enrichedBy]]; the others are reported as skipped, with why).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  override def expand(
      c: ManagerContext,
      raw: ManagerItem.Target
  ): Either[Api.Error, (List[ManagerContext.ItemTarget], List[ManagerKindDefinition.ExpandSkipped])] =
    val text = raw.value
    val asSource = Citekey
      .option(text)
      .toRight(Api.Error(Problem.NotFound.Entity(Problem.TypeName.of(Citekey), Problem.Raw(s"$text"))))
      .flatMap(ck => site.sources.get(ck, body = SourcesControllers.GetBody(false)).left.map(Api.Error(_)))
    asSource
      .map(s =>
        (List(ManagerContext.ItemTarget(s.source.citekey.value)), List.empty[ManagerKindDefinition.ExpandSkipped])
      )
      .orElse {
        val cited = Slug
          .option(text)
          .toRight(Api.Error(Problem.Unclassified(Legacy.Text(s"No article '$text'"))))
          .flatMap(slug => site.articleSources(slug))
          .orElse(
            domain.automation.Goal.Slug
              .option(text)
              .toRight(Api.Error(Problem.NotFound.Entity(Problem.TypeName.of(Goal.Slug), Problem.Raw(s"$text"))))
              .flatMap(slug =>
                a.host
                  .goal(slug)
                  .map(g =>
                    c.goalLinks(
                      domain.automation.Goal.Slug.assume(g.slug.value),
                      Goal.Plans.LinkKind.Source
                    ).map(x => Citekey.assume(x.value))
                  )
              )
          )
        cited.left
          .map(_ =>
            Api.Error(
              Problem.Unclassified(Legacy.Text(s"'$text' is no source, article or goal")),
              Some(Api.Error.Code(Write.CodeNotFound))
            )
          )
          .map { cks =>
            val (todo, done) = cks.distinct.map(ck => ck -> enrichedWhy(c, ck)).partition(_._2.isEmpty)
            (
              todo.map(t => ManagerContext.ItemTarget(t._1.value)),
              done.map((ck, why) => ManagerKindDefinition.ExpandSkipped(s"[[$ck]]: ${why.fold("")(_.said)}"))
            )
          }
      }

  /** Why a source counts as enriched already (findings, notes, a live enrich run), if it does. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def enrichedWhy(c: ManagerContext, ck: Citekey): Option[AutoEnrich.Rationale] =
    site.sources.get(ck, body = SourcesControllers.GetBody(false)).left.map(Api.Error(_)).toOption match
      case None      => Some(AutoEnrich.Rationale.NoSource)
      case Some(src) =>
        val runs = priorRuns(c, Citekey.assume(src.source.citekey.value))
        AutoEnrich.enrichedBy(
          AutoEnrich.FindingCount(src.findings.size),
          src.source.notes.map(n => AutoEnrich.Notes(n.value)),
          runs
        )

  /** The earlier non-dry `enrich-source` runs of a source, as [[AutoEnrich]] reads them. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def priorRuns(c: ManagerContext, ck: Citekey): List[AutoEnrich.PriorRun] =
    c.runsFor(Pipeline.EnrichSource, ManagerContext.ItemTarget(ck.value))
      .toList
      .filterNot(_.dryRun.value)
      .flatMap(r =>
        Run.State
          .parse(Run.State.Wire(r.status.value))
          .map(AutoEnrich.PriorRun(AutoEnrich.PriorRun.Id.assume(r.id.value), _))
      )

  // ── auto-enrich ────────────────────────────────────────────────────────────────────────────────

  /** `agents.auto-enrich`: after an ingest job stored `citekey`, it is enriched ([[AutoEnrich]] decides whether it
    * already is or one is on its way) — an item of the enrich manager while the managers are on, else a run of its own.
    * Answers the ingest job's progress note (None: not asked for).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  def autoEnrich(citekey: Citekey)(using WritePermit)(using Creator.New): Option[AutoEnrich.Note] =
    def cfg = a.config
    try
      autoEnrichLock.synchronized {
        site.sources.get(citekey, body = SourcesControllers.GetBody(false)).left.map(Api.Error(_)) match
          case Left(e)    => Some(AutoEnrich.notQueued(e.problems))
          case Right(src) =>
            val ck = Citekey.assume(src.source.citekey.value)
            // a dry run wrote nothing: it neither enriched the source nor stands for a run that will
            val runs = priorRuns(a.managers, ck)
            AutoEnrich.decide(
              cfg.enabled.value,
              cfg.autoEnrich.value,
              AutoEnrich.FindingCount(src.findings.size),
              src.source.notes.map(n => AutoEnrich.Notes(n.value)),
              runs
            ) match
              case Left(skip)                              => AutoEnrich.note(skip)
              case Right(()) if cfg.managers.enabled.value => Some(ingested(a.managers, ck))
              case Right(())                               =>
                Some(
                  a.startRunAs(
                    RunRequest(RunRequest.Pipeline(Pipeline.EnrichSource.wire.value), RunRequest.Target(ck.value)),
                    Origin.FromIngest,
                    None
                  ).fold(
                    e => AutoEnrich.notQueued(e.problems),
                    r => AutoEnrich.queued(Run.Id.assume(r.id.value))
                  )
                )
      }
    catch
      case e: InterruptedException => throw e
      case e: Exception            =>
        Some(AutoEnrich.notQueued(ProblemLog.unexpected(e, Problem.Where(s"the auto-enrich of $citekey"))))

  private val autoEnrichLock = new Object

  /** With the managers on: the ingested source becomes an item of the enrich manager (created paused if missing).
    * Answers the ingest job's progress note.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def ingested(c: ManagerContext, citekey: Citekey)(using WritePermit)(using Creator.New): AutoEnrich.Note =
    val enrichId = ManagerLight.Id(EnrichId)
    Unused(c.ensure(Kind.Review, ManagerLight.Id(ReviewId), None), Unused.Why.Idempotent)
    Unused(c.ensure(Kind.Enrich, enrichId, None), Unused.Why.Idempotent)
    val (itemId, isNew) = c.upsert(
      enrichId,
      ManagerContext.ItemKind(SourceEnrich),
      ManagerContext.ItemTarget(citekey.value),
      ItemOrigin.FromIngest,
      ManagerContext.Priority(0),
      reopen = ManagerContext.UpsertReopen(true)
    )
    c.event(
      enrichId,
      Some(itemId),
      None,
      ManagerContext.EventFeed(Feed.Evaluated),
      ManagerContext.EventText(s"$citekey ${if isNew then "enqueued" else "enqueued again"} by an ingest")
    )
    c.nudge()
    AutoEnrich.managed(
      AutoEnrich.ItemId(itemId.value),
      Manager.State.Wire(c.state(enrichId).fold(State.Paused.wire.value)(_.value))
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def dispatch(
      c: ManagerContext,
      m: ManagerInfo,
      s: ReviewSettings,
      item: ManagerItem,
      now: Instant,
      out: Builder[ManagerContext.TickLine, List[ManagerContext.TickLine]]
  )(using WritePermit)(using Creator.New): Option[ManagerLight.Id] =
    def settle(status: ItemStatus, why: String)(using WritePermit) =
      c.settleNow(m, item, status, ManagerContext.SettleNowWhy(why))
    item.kind.value match
      case SourceEnrich =>
        Citekey
          .option(item.target.value)
          .toRight(())
          .flatMap(ck =>
            site.sources
              .get(ck, body = SourcesControllers.GetBody(false))
              .left
              .map(Api.Error(_))
              .left
              .map(_ => ())
          ) match
          case Left(_)    => settle(ItemStatus.Cancelled, "the source no longer exists")
          case Right(src) =>
            val runs = priorRuns(c, Citekey.assume(src.source.citekey.value))
            // a person's Enrich enriches again; an ingest's or a selection's skips an enriched source
            val enriched = Option
              .when(item.origin != ItemOrigin.Person.wire)(
                AutoEnrich.enrichedBy(
                  AutoEnrich.FindingCount(src.findings.size),
                  src.source.notes.map(n => AutoEnrich.Notes(n.value)),
                  runs
                )
              )
              .flatten
            enriched match
              case Some(why) => settle(ItemStatus.Done, s"already enriched: ${why.said}")
              case None      =>
                c.start(
                  m,
                  item,
                  RunRequest(
                    RunRequest.Pipeline(Pipeline.EnrichSource.wire.value),
                    RunRequest.Target(src.source.citekey.value),
                    budgetUsd = m.config.budgetUsd.map(b => RunRequest.BudgetUsd(b.value))
                  ),
                  identity,
                  item.state,
                  ManagerContext.StartWhat("enrich-source"),
                  out
                )
      case other =>
        settle(ItemStatus.Cancelled, s"'$other' items are not driven by a ${m.kind} manager")
