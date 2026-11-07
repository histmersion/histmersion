package com.histmersion.domain.library.workflows

import io.scalaland.chimney.dsl.*

import com.histmersion.domain.automation.agents.AgentsControllers
import com.histmersion.domain.automation.managers.ManagerContext
import com.histmersion.domain.automation.managers.ManagerKindDefinition

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

  def pipelineOf(itemKind: com.histmersion.domain.library.workflows.EnrichManagerKind.ItemKind): Pipeline = itemKind match
    case SourceEnrich => Pipeline.EnrichSource
    case _            => Pipeline.Fallback

  def fire(c: ManagerContext, m: ManagerInfo, now: Instant): List[com.histmersion.domain.library.workflows.EnrichManagerKind.FireResult] = Nil

  def facts(c: ManagerContext, item: ManagerItem, run: com.histmersion.domain.automation.Run.Id): StepFacts = StepFacts.Finished("enriched")

  def target(m: ManagerInfo, itemKind: com.histmersion.domain.library.workflows.EnrichManagerKind.ItemKind, raw: com.histmersion.domain.library.workflows.EnrichManagerKind.TargetRaw): Either[Api.Error, com.histmersion.domain.library.workflows.EnrichManagerKind.TargetResultPart2] =
    itemKind match
      case SourceEnrich => site.sources.get(raw.trim, body = false).map(_.source.citekey)
      case other        => Left(Api.Error(s"unknown item kind '$other'"))

  /** What an Enrich button names: a source itself (enriched again even if it was), or the sources an article cites / a
    * goal links that are NOT enriched yet ([[AutoEnrich.enrichedBy]]; the others are reported as skipped, with why).
    */
  override def expand(c: ManagerContext, raw: String): Either[Api.Error, (List[String], List[String])] =
    site.sources.get(raw, body = false).map(s => (List(s.source.citekey), Nil)).orElse {
      val cited = site
        .articleSources(raw)
        .orElse(a.host.goal(raw).map(g => c.goalLinks(g.slug, Goal.Plans.LinkKind.Source)))
      cited.left.map(_ => Api.Error(s"'$raw' is no source, article or goal", Some(Write.CodeNotFound))).map { cks =>
        val (todo, done) = cks.distinct.map(ck => ck -> enrichedWhy(c, ck)).partition(_._2.isEmpty)
        (todo.map(_._1), done.map((ck, why) => s"[[$ck]]: ${why.getOrElse("")}"))
      }
    }

  /** Why a source counts as enriched already (findings, notes, a live enrich run), if it does. */
  private def enrichedWhy(c: ManagerContext, ck: com.histmersion.domain.library.Citekey): Option[com.histmersion.domain.library.workflows.EnrichManagerKind.EnrichedWhyResult] =
    site.sources.get(ck, body = false).toOption match
      case None      => Some("no such source")
      case Some(src) =>
        val runs = c
          .runsFor(Pipeline.EnrichSource, src.source.citekey)
          .toList
          .filterNot(_.dryRun)
          .flatMap(r => com.histmersion.domain.automation.Run.State.parse(r.status).map(AutoEnrich.PriorRun(r.id, _)))
        AutoEnrich.enrichedBy(src.findings.size, src.source.notes, runs)

  // ── auto-enrich ────────────────────────────────────────────────────────────────────────────────

  /** `agents.auto-enrich`: after an ingest job stored `citekey`, it is enriched ([[AutoEnrich]] decides whether it
    * already is or one is on its way) — an item of the enrich manager while the managers are on, else a run of its own.
    * Answers the ingest job's progress note (None: not asked for).
    */
  def autoEnrich(citekey: com.histmersion.domain.library.Citekey): Option[com.histmersion.domain.library.workflows.EnrichManagerKind.AutoEnrichResult] =
    def cfg = a.config
    try
      autoEnrichLock.synchronized {
        site.sources.get(citekey, body = false) match
          case Left(e)    => Some(AutoEnrich.notQueued(e.message))
          case Right(src) =>
            val ck = src.source.citekey
            // a dry run wrote nothing: it neither enriched the source nor stands for a run that will
            val runs = a.managers
              .runsFor(Pipeline.EnrichSource, ck)
              .toList
              .filterNot(_.dryRun)
              .flatMap(r => com.histmersion.domain.automation.Run.State.parse(r.status).map(AutoEnrich.PriorRun(r.id, _)))
            AutoEnrich.decide(cfg.enabled, cfg.autoEnrich, src.findings.size, src.source.notes, runs) match
              case Left(skip)                        => AutoEnrich.note(skip)
              case Right(()) if cfg.managers.enabled => Some(ingested(a.managers, ck))
              case Right(())                         =>
                Some(
                  a.startRunAs(RunRequest(Pipeline.EnrichSource.wire, ck), Origin.FromIngest, None)
                    .fold(e => AutoEnrich.notQueued(e.message), r => AutoEnrich.queued(r.id))
                )
      }
    catch
      case e: InterruptedException => throw e
      case e: Exception            =>
        scribe.warn(s"auto-enrich of $citekey failed: ${e.getClass.getSimpleName}: ${e.getMessage}")
        Some(AutoEnrich.notQueued(s"${e.getClass.getSimpleName}: ${Option(e.getMessage).getOrElse("")}".take(300)))

  private val autoEnrichLock = new Object

  /** With the managers on: the ingested source becomes an item of the enrich manager (created paused if missing).
    * Answers the ingest job's progress note.
    */
  private def ingested(c: ManagerContext, citekey: com.histmersion.domain.library.Citekey): com.histmersion.domain.library.workflows.EnrichManagerKind.IngestedResult =
    val _ = c.ensure(Kind.Review, ReviewId, None)
    val _ = c.ensure(Kind.Enrich, EnrichId, None)
    val (itemId, isNew) = c.upsert(EnrichId, SourceEnrich, citekey, ItemOrigin.FromIngest, 0, reopen = true)
    c.event(
      EnrichId,
      Some(itemId),
      None,
      Feed.Evaluated,
      s"$citekey ${if isNew then "enqueued" else "enqueued again"} by an ingest"
    )
    c.nudge()
    AutoEnrich.managed(itemId, c.state(EnrichId).getOrElse(State.Paused.wire))

  def dispatch(
      c: ManagerContext,
      m: ManagerInfo,
      s: ReviewSettings,
      item: ManagerItem,
      now: Instant,
      out: Builder[com.histmersion.domain.library.workflows.EnrichManagerKind.DispatchOutPart1, List[com.histmersion.domain.library.workflows.EnrichManagerKind.DispatchOutPart2]]
  ): Option[com.histmersion.domain.library.workflows.EnrichManagerKind.DispatchResult] =
    item.kind match
      case SourceEnrich =>
        site.sources.get(item.target, body = false) match
          case Left(_)    => c.settleNow(m, item, ItemStatus.Cancelled, "the source no longer exists")
          case Right(src) =>
            val runs = c
              .runsFor(Pipeline.EnrichSource, src.source.citekey)
              .toList
              .filterNot(_.dryRun)
              .flatMap(r => com.histmersion.domain.automation.Run.State.parse(r.status).map(AutoEnrich.PriorRun(r.id, _)))
            // a person's Enrich enriches again; an ingest's or a selection's skips an enriched source
            val enriched = Option
              .when(item.origin != ItemOrigin.Person.wire)(
                AutoEnrich.enrichedBy(src.findings.size, src.source.notes, runs)
              )
              .flatten
            enriched match
              case Some(why) => c.settleNow(m, item, ItemStatus.Done, s"already enriched: $why")
              case None      =>
                c.start(
                  m,
                  item,
                  RunRequest(Pipeline.EnrichSource.wire, src.source.citekey, budgetUsd = m.config.budgetUsd),
                  identity,
                  item.state,
                  "enrich-source",
                  out
                )
      case other =>
        c.settleNow(m, item, ItemStatus.Cancelled, s"'$other' items are not driven by a ${m.kind} manager")

object EnrichManagerKind:
  type ItemKind = ItemKind.T
  object ItemKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FireResult = FireResult.T
  object FireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetResultPart2 = TargetResultPart2.T
  object TargetResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetRaw = TargetRaw.T
  object TargetRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EnrichedWhyResult = EnrichedWhyResult.T
  object EnrichedWhyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AutoEnrichResult = AutoEnrichResult.T
  object AutoEnrichResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IngestedResult = IngestedResult.T
  object IngestedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DispatchResult = DispatchResult.T
  object DispatchResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DispatchOutPart1 = DispatchOutPart1.T
  object DispatchOutPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DispatchOutPart2 = DispatchOutPart2.T
  object DispatchOutPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
