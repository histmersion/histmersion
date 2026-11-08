package com.histmersion
package server.agents

import com.histmersion.api.Problem
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

/** The engine's [[AutomationHost]] over the composition: the goals through the automation's own controllers, a run's
  * target, options and links checked against the library's sources and research's articles, the plan's works looked up
  * through the library's upstream, the vocabulary of its labels, and the housekeeping of this server.
  */
final class ServerAutomationHost(h: ServerWiring, upstream: () => AgentUpstream) extends AutomationHost:
  def articleExists(slug: Slug): Boolean = h.research.articleStore.exists(slug)
  def goal(slug: com.histmersion.domain.automation.Goal.Slug): Either[Api.Error, Goal] = h.goals.get(slug)
  def putGoal(slug: com.histmersion.domain.automation.Goal.Slug, in: Goal.Put): Either[Api.Error, Goal] =
    h.goals.put(slug, in)
  def patchGoal(slug: com.histmersion.domain.automation.Goal.Slug, patch: Goal.Patch): Either[Api.Error, Goal] =
    h.goals.patch(slug, patch)
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
            .map(p => refused(p.value))
            .flatMap(ck => h.library.sources.get(ck, body = SourcesControllers.GetBody(false)))
            .map(w => AutomationHost.CanonicalKey(w.source.citekey.value))
        )
      case TargetKind.Article if p == Pipeline.CompileArticle =>
        Some(
          Slug.parse(raw.value).map(sl => AutomationHost.CanonicalKey(sl.value)).left.map(p => refused(p.value))
        )
      case TargetKind.Article =>
        Some(
          Slug
            .parse(raw.value)
            .left
            .map(p => refused(p.value))
            .flatMap(sl => h.research.articles.get(sl))
            .map(v => AutomationHost.CanonicalKey(v.article.slug.value))
        )
      case TargetKind.Goal =>
        Some(
          com.histmersion.domain.automation.Goal.Slug
            .either(raw.value.trim)
            .left
            .map(refused)
            .flatMap(gs => h.goals.get(gs))
            .map(g => AutomationHost.CanonicalKey(g.slug.value))
        )
      case TargetKind.Manager => None

  private def refused(why: String): Api.Error = Api.Error(Problem(why))

  def runOptions(
      p: Pipeline,
      req: RunRequest,
      target: ManagerContext.ItemTarget,
      roundsCap: AutomationHost.RunOptionsRoundsCap
  ): Either[Api.Error, Unit] =
    val wrong = (what: String, owner: Pipeline) =>
      Left(Api.Error(Problem(s"$what is an option of ${owner.wire}, not of ${p.wire}")))
    val compileOpts =
      req.title.isDefined || req.kind.isDefined || req.query.isDefined || req.topics.nonEmpty || req.tags.nonEmpty
    p match
      case _ if p != Pipeline.ReviewArticle && req.fix.contains(RunRequest.Fix(true)) =>
        wrong("fix", Pipeline.ReviewArticle)
      case _ if p != Pipeline.ReviewArticle && req.rounds.isDefined     => wrong("rounds", Pipeline.ReviewArticle)
      case _ if p != Pipeline.EnrichSource && req.acquireItem.isDefined =>
        wrong("an acquisition item", Pipeline.EnrichSource)
      case _ if p != Pipeline.CompileArticle && compileOpts => wrong("a title, kind or filter", Pipeline.CompileArticle)
      case _ if req.rounds.exists(n => n.value < 1 || n.value > roundsCap.value) =>
        Left(Api.Error(Problem(s"rounds must be between 1 and $roundsCap (agents.review.rounds-cap)")))
      case Pipeline.CompileArticle =>
        val exists = Slug.parse(target.value).exists(h.research.articleStore.exists)
        val filter =
          req.query.exists(_.value.trim.nonEmpty) || req.topics.exists(_.value.trim.nonEmpty) ||
            req.tags.exists(_.value.trim.nonEmpty)
        for
          _ <- req.kind.fold[Either[Api.Error, Unit]](Right(()))(k =>
            Articles.kindOf(Article.Kind.Wire(k.value)).left.map(p => refused(p.value)).map(_ => ())
          )
          _ <- Either.cond(
            exists || filter,
            (),
            Api.Error(
              Problem(
                s"'$target' is a new article: give a filter (query, topics or tags) " +
                  "for the sources to compile it from"
              )
            )
          )
          _ <- Either.cond(
            exists || req.title.exists(_.value.trim.nonEmpty),
            (),
            Api.Error(Problem(s"'$target' is a new article: give its title"))
          )
        yield ()
      case _ =>
        req.acquireItem.fold(Right(()))(id =>
          Queue.fulfillable(id, h.queues.get(QueueItemId(id.value))).map(_ => ()).left.map(p => refused(p.value))
        )

  def runRoles(p: Pipeline): List[Role] =
    (p.roles ++ (if p == Pipeline.CompileArticle then Pipeline.ReviewArticle.roles else Nil)).distinct

  def linkRef(kind: LinkKind, raw: AutomationHost.LinkRefRaw): Either[Api.Error, AutomationHost.CanonicalKey] =
    if kind == LinkKind.Article then
      Slug
        .parse(raw.value)
        .left
        .map(p => refused(p.value))
        .flatMap(sl => h.research.articles.get(sl))
        .map(v => AutomationHost.CanonicalKey(v.article.slug.value))
    else
      Citekey
        .parse(raw.value)
        .left
        .map(p => refused(p.value))
        .flatMap(ck => h.library.sources.get(ck, body = SourcesControllers.GetBody(false)))
        .map(w => AutomationHost.CanonicalKey(w.source.citekey.value))

  def probeSubject(citekey: Citekey): Either[Api.Error, ProbeAnswer] =
    h.library.sources
      .get(citekey, body = SourcesControllers.GetBody(false))
      .map(s => ProbeAnswer(s.source.citekey, s.source.title, s.source.year))

  def sameTitle(answered: Title, record: Title): Boolean = AgentVerify.sameTitle(answered, record)

  def workLookup: PlanGoal.Lookup = doi =>
    upstream()
      .meta(doi)
      .left
      .map(p => domain.Problem(p.value))
      .map(w =>
        PlanGoal.Work(
          w.title.map(t => PlanGoal.Work.Title(t.value)),
          w.year.map(y => Year(y.value.toInt)),
          w.authors.map(a => PlanGoal.Work.Authors(a.value)),
          w.citekey.flatMap(c => Citekey.option(c.value)),
          PlanGoal.Work.InDb(w.inDb.value),
          PlanGoal.Work.IsOa(w.isOa.value),
          PlanGoal.Work.IsRetracted(w.isRetracted.value)
        )
      )
  def vocabulary(field: AutomationHost.VocabularyField): List[AutomationHost.VocabularyResult] =
    h.library.labels
      .facets(Facet.Field.Wire(field.value), None, Filter())
      .toOption
      .toList
      .flatten
      .sortBy(-_.count.value)
      .map(f => AutomationHost.VocabularyResult(f.value.value))
  def housekeepingBlock: Option[AutomationHost.HousekeepingBlockResult] =
    h.upkeep.housekeepingBlock.map(r => AutomationHost.HousekeepingBlockResult(r.value))
  def backup(): Either[Api.Error, Backup.Status] = h.upkeep.backup()
  def backupStatus: Either[Api.Error, Backup.Status] = h.upkeep.backupStatus
  def lintFix(): Either[Api.Error, LintFixResult] = h.checks.guardedLintFix(Nil)
