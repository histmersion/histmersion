package com.histmersion.domain.library.workflows

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

/** The `enrich` manager kind (library-wide: source-enrich items, what `agents.auto-enrich` feeds), registered by this
  * transition adapter until the library contributes it: a source already enriched is settled without a run, unless a
  * person asked.
  */
final class EnrichManagerKind(site: LibrarySite, a: AgentsControllers) extends ManagerKindDefinition:
  val kind: Kind = Kind.Enrich

  def defaults(cfg: AgentsConfig, m: ManagerDefaults): ManagerConfig =
    ReviewSettings.defaultsFor(kind.transformInto[com.histmersion.domain.automation.Manager.Kind], m)

  def pipelineOf(itemKind: ManagerContext.ItemKind): Pipeline = itemKind.value match
    case SourceEnrich => Pipeline.EnrichSource
    case _            => Pipeline.Fallback

  def fire(c: ManagerContext, m: ManagerInfo, now: Instant): List[ManagerKindDefinition.FireResult] = Nil

  def facts(c: ManagerContext, item: ManagerItem, run: com.histmersion.domain.automation.Run.Id): StepFacts = StepFacts.Finished(com.histmersion.domain.ManagerPolicy.ItemOutcome("enriched"))

  def target(m: ManagerInfo, itemKind: ManagerContext.ItemKind, raw: ManagerKindDefinition.TargetRaw): Either[Api.Error, ManagerKindDefinition.TargetResultPart2] =
    itemKind.value match
      case SourceEnrich =>
        Citekey.option(raw.value.trim) match
          case None     => Left(Api.Error(Api.Error.Message(s"No source '${raw.value.trim}'")))
          case Some(ck) =>
            site.sources
              .get(ck, body = com.histmersion.domain.library.sources.SourcesControllers.GetBody(false))
              .map(s => ManagerKindDefinition.TargetResultPart2(s.source.citekey.value))
      case other => Left(Api.Error(Api.Error.Message(s"unknown item kind '$other'")))

  /** What an Enrich button names: a source itself (enriched again even if it was), or the sources an article cites / a
    * goal links that are NOT enriched yet ([[AutoEnrich.enrichedBy]]; the others are reported as skipped, with why).
    */
  override def expand(c: ManagerContext, raw: ManagerKindDefinition.ExpandRaw): Either[Api.Error, (List[ManagerKindDefinition.ExpandResultPart2Part1], List[ManagerKindDefinition.ExpandResultPart2Part2])] =
    val text = raw.value
    val asSource = Citekey.option(text).toRight(Api.Error(Api.Error.Message(s"No source '$text'"))).flatMap(ck =>
      site.sources.get(ck, body = com.histmersion.domain.library.sources.SourcesControllers.GetBody(false))
    )
    asSource.map(s => (List(ManagerKindDefinition.ExpandResultPart2Part1(s.source.citekey.value)), List.empty[ManagerKindDefinition.ExpandResultPart2Part2])).orElse {
      val cited = com.histmersion.domain.research.Slug.option(text).toRight(Api.Error(Api.Error.Message(s"No article '$text'"))).flatMap(slug =>
        site.articleSources(slug).map(_.map(x => Citekey.assume(x.value)))
      ).orElse(
        com.histmersion.domain.automation.Goal.Slug.option(text).toRight(Api.Error(Api.Error.Message(s"No goal '$text'"))).flatMap(slug =>
          a.host.goal(slug).map(g => c.goalLinks(com.histmersion.domain.automation.Goal.Slug.assume(g.slug.value), Goal.Plans.LinkKind.Source).map(x => Citekey.assume(x.value)))
        )
      )
      cited.left.map(_ => Api.Error(Api.Error.Message(s"'$text' is no source, article or goal"), Some(Api.Error.Code(Write.CodeNotFound)))).map { cks =>
        val (todo, done) = cks.distinct.map(ck => ck -> enrichedWhy(c, ck)).partition(_._2.isEmpty)
        (todo.map(t => ManagerKindDefinition.ExpandResultPart2Part1(t._1.value)), done.map((ck, why) => ManagerKindDefinition.ExpandResultPart2Part2(s"[[$ck]]: ${why.fold("")(_.value)}")))
      }
    }

  /** Why a source counts as enriched already (findings, notes, a live enrich run), if it does. */
  private def enrichedWhy(c: ManagerContext, ck: com.histmersion.domain.library.Citekey): Option[AutoEnrich.Reason] =
    site.sources.get(ck, body = com.histmersion.domain.library.sources.SourcesControllers.GetBody(false)).toOption match
      case None      => Some(AutoEnrich.Reason("no such source"))
      case Some(src) =>
        val runs = priorRuns(c, Citekey.assume(src.source.citekey.value))
        AutoEnrich.enrichedBy(AutoEnrich.FindingCount(src.findings.size), src.source.notes.map(n => AutoEnrich.Notes(n.value)), runs)

  /** The earlier non-dry `enrich-source` runs of a source, as [[AutoEnrich]] reads them. */
  private def priorRuns(c: ManagerContext, ck: com.histmersion.domain.library.Citekey): List[AutoEnrich.PriorRun] =
    c.runsFor(Pipeline.EnrichSource, ManagerContext.ItemTarget(ck.value))
      .toList
      .filterNot(_.dryRun.value)
      .flatMap(r =>
        com.histmersion.domain.automation.Run.State
          .parse(com.histmersion.domain.automation.Run.State.Wire(r.status.value))
          .map(AutoEnrich.PriorRun(AutoEnrich.PriorRun.Id(r.id.value), _))
      )

  // ── auto-enrich ────────────────────────────────────────────────────────────────────────────────

  /** `agents.auto-enrich`: after an ingest job stored `citekey`, it is enriched ([[AutoEnrich]] decides whether it
    * already is or one is on its way) — an item of the enrich manager while the managers are on, else a run of its own.
    * Answers the ingest job's progress note (None: not asked for).
    */
  def autoEnrich(citekey: com.histmersion.domain.library.Citekey): Option[AutoEnrich.Note] =
    def cfg = a.config
    try
      autoEnrichLock.synchronized {
        site.sources.get(citekey, body = com.histmersion.domain.library.sources.SourcesControllers.GetBody(false)) match
          case Left(e)    => Some(AutoEnrich.notQueued(AutoEnrich.Reason(e.message.value)))
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
                    )
                    .fold(
                      e => AutoEnrich.notQueued(AutoEnrich.Reason(e.message.value)),
                      r => AutoEnrich.queued(com.histmersion.domain.automation.Run.Id(r.id.value))
                    )
                )
      }
    catch
      case e: InterruptedException => throw e
      case e: Exception            =>
        scribe.warn(s"auto-enrich of $citekey failed: ${e.getClass.getSimpleName}: ${e.getMessage}")
        Some(AutoEnrich.notQueued(AutoEnrich.Reason(s"${e.getClass.getSimpleName}: ${Option(e.getMessage).getOrElse("")}".take(300))))

  private val autoEnrichLock = new Object

  /** With the managers on: the ingested source becomes an item of the enrich manager (created paused if missing).
    * Answers the ingest job's progress note.
    */
  private def ingested(c: ManagerContext, citekey: com.histmersion.domain.library.Citekey): AutoEnrich.Note =
    val enrichId = ManagerLight.Id(EnrichId)
    val _ = c.ensure(Kind.Review, ManagerLight.Id(ReviewId), None)
    val _ = c.ensure(Kind.Enrich, enrichId, None)
    val (itemId, isNew) = c.upsert(enrichId, ManagerContext.ItemKind(SourceEnrich), ManagerContext.ItemTarget(citekey.value), ItemOrigin.FromIngest, com.histmersion.domain.automation.managers.ManagerContext.Priority(0), reopen = com.histmersion.domain.automation.managers.ManagerContext.UpsertReopen(true))
    c.event(
      enrichId,
      Some(ManagerContext.ItemId(itemId.value)),
      None,
      ManagerContext.EventFeed(Feed.Evaluated),
      ManagerContext.EventText(s"$citekey ${if isNew then "enqueued" else "enqueued again"} by an ingest")
    )
    c.nudge()
    AutoEnrich.managed(
      AutoEnrich.ItemId(itemId.value),
      com.histmersion.domain.automation.Manager.State.Wire(c.state(enrichId).fold(State.Paused.wire.value)(_.value))
    )

  def dispatch(
      c: ManagerContext,
      m: ManagerInfo,
      s: ReviewSettings,
      item: ManagerItem,
      now: Instant,
      out: Builder[ManagerKindDefinition.DispatchOutPart1, List[ManagerKindDefinition.DispatchOutPart2]]
  ): Option[ManagerKindDefinition.DispatchResult] =
    def settle(status: ItemStatus, why: String) =
      c.settleNow(m, item, status, ManagerContext.SettleNowWhy(why)).map(r => ManagerKindDefinition.DispatchResult(r.value))
    item.kind.value match
      case SourceEnrich =>
        Citekey
          .option(item.target.value)
          .toRight(())
          .flatMap(ck =>
            site.sources
              .get(ck, body = com.histmersion.domain.library.sources.SourcesControllers.GetBody(false))
              .left
              .map(_ => ())
          ) match
          case Left(_)    => settle(ItemStatus.Cancelled, "the source no longer exists")
          case Right(src) =>
            val runs = priorRuns(c, Citekey.assume(src.source.citekey.value))
            // a person's Enrich enriches again; an ingest's or a selection's skips an enriched source
            val enriched = Option
              .when(item.origin != ItemOrigin.Person.wire)(
                AutoEnrich.enrichedBy(AutoEnrich.FindingCount(src.findings.size), src.source.notes.map(n => AutoEnrich.Notes(n.value)), runs)
              )
              .flatten
            enriched match
              case Some(why) => settle(ItemStatus.Done, s"already enriched: $why")
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
                    com.histmersion.domain.automation.managers.ManagerContext.StartWhat("enrich-source"),
                    out
                  )
                  .map(r => ManagerKindDefinition.DispatchResult(r.value))
      case other =>
        settle(ItemStatus.Cancelled, s"'$other' items are not driven by a ${m.kind} manager")
