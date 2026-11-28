package com.histmersion
package server.agents
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.sentence

import com.histmersion.domain.research.Slug

import com.histmersion.domain.library.workflows.{AgentUpstream, SourceProposals}

import com.histmersion.api.research.Articles
import com.histmersion.api.library.{Facet, Filter}
import com.histmersion.api.research.Article
import com.histmersion.domain.library.Citekey
import com.histmersion.api.automation.{Goal, Queue}
import com.histmersion.api.automation.Agents.{Pipeline, ProbeAnswer, Role, RunRequest}
import com.histmersion.api.automation.Goal.Plans.LinkKind
import com.histmersion.domain.AgentVerify
import com.histmersion.domain.automation.worklists.QueueItemId
import com.histmersion.api.automation.TargetKind
import com.histmersion.api.checks.Lint.LintFixResult
import com.histmersion.domain.PlanGoal
import com.histmersion.domain.automation.workflows.AutomationHost
import com.histmersion.server.ServerWiring
import io.scalaland.chimney.dsl.*
import com.histmersion.api.Api
import com.histmersion.api.backup.Backup
import com.histmersion.domain.library.sources.SourcesControllers
import com.histmersion.domain.automation.managers.ManagerContext
import com.histmersion.domain.Title
import com.histmersion.domain.Creator
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

/** The engine's [[AutomationHost]] over the composition: the goals through the automation's own controllers, a run's
  * target, options and links checked against the library's sources and research's articles, the plan's works looked up
  * through the library's upstream, the vocabulary of its labels, and the housekeeping of this server.
  */
@movesTo(FeaturePlace.Server)
final class ServerAutomationHost(h: ServerWiring, upstream: () => AgentUpstream) extends AutomationHost:
  def articleExists(slug: Slug): Boolean = h.research.articleStore.exists(slug)
  def goal(slug: domain.automation.Goal.Slug): Either[Api.Error, Goal] = h.goals.get(slug)
  def putGoal(slug: domain.automation.Goal.Slug, in: Goal.Put)(using Creator.New): Either[Api.Error, Goal] =
    h.goals.put(slug, in)
  def patchGoal(slug: domain.automation.Goal.Slug, patch: Goal.Patch): Either[Api.Error, Goal] =
    h.goals.patch(slug, patch)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def runTarget(
      p: Pipeline,
      raw: AutomationHost.RunTargetRaw
  ): Option[Either[Api.Error, AutomationHost.CanonicalKey]] =
    p.targetKind match
      case TargetKind.Source =>
        Some(
          Citekey
            .parse(raw.value)
            .left
            .map(Api.Error(_))
            .flatMap(ck => h.library.sources.get(ck, body = SourcesControllers.GetBody(false)).left.map(Api.Error(_)))
            .map(w => AutomationHost.CanonicalKey(w.source.citekey.value))
        )
      case TargetKind.Article if p == Pipeline.CompileArticle =>
        Some(
          Slug.parse(raw.value).map(sl => AutomationHost.CanonicalKey(sl.value)).left.map(Api.Error(_))
        )
      case TargetKind.Article =>
        Some(
          Slug
            .parse(raw.value)
            .left
            .map(Api.Error(_))
            .flatMap(sl => h.research.articles.get(sl))
            .map(v => AutomationHost.CanonicalKey(v.article.slug.value))
        )
      case TargetKind.Goal =>
        Some(
          domain.automation.Goal.Slug
            .parse(raw.value.trim)
            .left
            .map(Api.Error(_))
            .flatMap(gs => h.goals.get(gs))
            .map(g => AutomationHost.CanonicalKey(g.slug.value))
        )
      case TargetKind.Manager => None

  private def broken(rule: Problem.Invalid.Rule): Api.Error = Api.Error(Problem.Invalid.Broken.here(rule))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def runOptions(
      p: Pipeline,
      req: RunRequest,
      target: ManagerContext.ItemTarget,
      roundsCap: AutomationHost.RunOptionsRoundsCap
  ): Either[Api.Error, Unit] =
    val wrong = (what: String, owner: Pipeline) =>
      Left(
        broken(Problem.Invalid.Rule.OptionOf(Problem.Raw(what), Problem.Raw.of(owner.wire), Problem.Raw.of(p.wire)))
      )
    val compileOpts =
      req.title.isDefined || req.kind.isDefined || req.query.isDefined || req.topics.nonEmpty || req.tags.nonEmpty
    p match
      case _ if p != Pipeline.ReviewArticle && req.fix.contains(RunRequest.Fix.Fix) =>
        wrong("fix", Pipeline.ReviewArticle)
      case _ if p != Pipeline.ReviewArticle && req.rounds.isDefined     => wrong("rounds", Pipeline.ReviewArticle)
      case _ if p != Pipeline.EnrichSource && req.acquireItem.isDefined =>
        wrong("an acquisition item", Pipeline.EnrichSource)
      case _ if p != Pipeline.CompileArticle && compileOpts => wrong("a title, kind or filter", Pipeline.CompileArticle)
      case _ if req.rounds.exists(n => n.value < 1 || n.value > roundsCap.value) =>
        Left(
          broken(
            Problem.Invalid.Rule.RoundsWithin(
              req.rounds.fold(Problem.Count(0L))(Problem.Count.of(_)),
              Problem.Count(1L),
              Problem.Count.of(roundsCap)
            )
          )
        )
      case Pipeline.CompileArticle =>
        val exists = Slug.parse(target.value).exists(h.research.articleStore.exists)
        val filter =
          req.query.exists(_.value.trim.nonEmpty) || req.topics.exists(_.value.trim.nonEmpty) ||
            req.tags.exists(_.value.trim.nonEmpty)
        for
          _ <- req.kind.fold[Either[Api.Error, Unit]](Right(()))(k =>
            Articles.kindOf(Article.Kind.Wire(k.value)).left.map(Api.Error(_)).map(_ => ())
          )
          _ <- Either.cond(
            exists || filter,
            (),
            broken(Problem.Invalid.Rule.NewArticleFilter(Problem.Raw.of(target)))
          )
          _ <- Either.cond(
            exists || req.title.exists(_.value.trim.nonEmpty),
            (),
            broken(Problem.Invalid.Rule.NewArticleTitle(Problem.Raw.of(target)))
          )
        yield ()
      case _ =>
        req.acquireItem.fold(Right(()))(id =>
          Queue.fulfillable(id, h.queues.get(QueueItemId(id.value))).map(_ => ()).left.map(Api.Error(_))
        )

  def runRoles(p: Pipeline): List[Role] =
    (p.roles ++ (if p == Pipeline.CompileArticle then Pipeline.ReviewArticle.roles else Nil)).distinct

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def linkRef(kind: LinkKind, raw: AutomationHost.LinkRefRaw): Either[Api.Error, AutomationHost.CanonicalKey] =
    if kind == LinkKind.Article then
      Slug
        .parse(raw.value)
        .left
        .map(Api.Error(_))
        .flatMap(sl => h.research.articles.get(sl))
        .map(v => AutomationHost.CanonicalKey(v.article.slug.value))
    else
      Citekey
        .parse(raw.value)
        .left
        .map(Api.Error(_))
        .flatMap(ck => h.library.sources.get(ck, body = SourcesControllers.GetBody(false)).left.map(Api.Error(_)))
        .map(w => AutomationHost.CanonicalKey(w.source.citekey.value))

  def probeSubject(citekey: Citekey): Either[Api.Error, ProbeAnswer] =
    h.library.sources
      .get(citekey, body = SourcesControllers.GetBody(false))
      .left
      .map(Api.Error(_))
      .map(s => ProbeAnswer(s.source.citekey, s.source.title, s.source.year))

  def sameTitle(answered: Title, record: Title): Boolean = AgentVerify.sameTitle(answered, record)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def workLookup: PlanGoal.Lookup = doi =>
    upstream()
      .meta(doi)
      .map(w =>
        PlanGoal.Work(
          w.title.map(t => PlanGoal.Work.Title(t.value)),
          w.year.map(y => Year(y.value.toInt)),
          w.authors.map(a => PlanGoal.Work.Authors(a.value)),
          w.citekey.flatMap(c => Citekey.option(c.value)),
          (if w.inDb.inDb then PlanGoal.Work.InDb.InCorpus else PlanGoal.Work.InDb.Outside),
          (if w.isOa.isOa then PlanGoal.Work.IsOa.Open else PlanGoal.Work.IsOa.Closed),
          (if w.isRetracted.isRetracted then PlanGoal.Work.IsRetracted.Retracted
           else PlanGoal.Work.IsRetracted.Standing)
        )
      )
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def vocabulary(field: AutomationHost.VocabularyField): List[AutomationHost.Term] =
    h.library.labels
      .facets(Facet.Field.Wire(field.value), None, Filter())
      .left
      .map(Api.Error(_))
      .toOption
      .toList
      .flatten
      .sortBy(-_.count.value)
      .map(f => AutomationHost.Term(f.value.value))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def housekeepingBlock: Option[AutomationHost.HousekeepingBlock] =
    h.upkeep.housekeepingBlock.map(r => AutomationHost.HousekeepingBlock(r.sentence))
  def backup(): Either[Api.Error, Backup.Status] = h.upkeep.backup()
  def backupStatus: Either[Api.Error, Backup.Status] = h.upkeep.backupStatus
  def lintFix(): Either[Api.Error, LintFixResult] = h.checks.guardedLintFix(Nil)
