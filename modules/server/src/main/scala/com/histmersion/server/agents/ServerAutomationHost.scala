package com.histmersion.server.agents

import com.histmersion.domain.research.Slug

import com.histmersion.domain.library.workflows.{AgentUpstream, SourceProposals}

import com.histmersion.api.research.Articles
import com.histmersion.api.library.Filter
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

/** The engine's [[AutomationHost]] over the composition: the goals through the automation's own controllers, a run's
  * target, options and links checked against the library's sources and research's articles, the plan's works looked up
  * through the library's upstream, the vocabulary of its labels, and the housekeeping of this server.
  */
final class ServerAutomationHost(h: ServerWiring, upstream: () => AgentUpstream) extends AutomationHost:
  def articleExists(slug: com.histmersion.domain.research.Slug): Boolean = h.research.articleStore.exists(slug)
  def goal(slug: com.histmersion.domain.automation.Goal.Slug): Either[Api.Error, Goal] = h.goals.get(slug)
  def putGoal(slug: com.histmersion.domain.automation.Goal.Slug, in: Goal.Put): Either[Api.Error, Goal] = h.goals.put(slug, in)
  def patchGoal(slug: com.histmersion.domain.automation.Goal.Slug, patch: Goal.Patch): Either[Api.Error, Goal] = h.goals.patch(slug, patch)
  def runTarget(p: Pipeline, raw: com.histmersion.server.agents.ServerAutomationHost.RunTarget): Option[Either[Api.Error, com.histmersion.server.agents.ServerAutomationHost.RunTarget]] =
    p.targetKind match
      case TargetKind.Source => Some(h.library.sources.get(raw, body = false).map(_.source.citekey))
      case TargetKind.Article if p == Pipeline.CompileArticle =>
        Some(Slug.parse(raw).map(_.value).left.map(Api.Error(_)))
      case TargetKind.Article => Some(h.research.articles.get(raw).map(_.article.slug))
      case TargetKind.Goal    => Some(h.goals.get(raw.trim).map(_.slug))
      case TargetKind.Manager => None

  def runOptions(p: Pipeline, req: RunRequest, target: com.histmersion.server.agents.ServerAutomationHost.RunTarget, roundsCap: com.histmersion.server.agents.ServerAutomationHost.RoundsLimit): Either[Api.Error, Unit] =
    val wrong = (what: String, owner: Pipeline) =>
      Left(Api.Error(s"$what is an option of ${owner.wire}, not of ${p.wire}"))
    val compileOpts =
      req.title.isDefined || req.kind.isDefined || req.query.isDefined || req.topics.nonEmpty || req.tags.nonEmpty
    p match
      case _ if p != Pipeline.ReviewArticle && req.fix.contains(true)   => wrong("fix", Pipeline.ReviewArticle)
      case _ if p != Pipeline.ReviewArticle && req.rounds.isDefined     => wrong("rounds", Pipeline.ReviewArticle)
      case _ if p != Pipeline.EnrichSource && req.acquireItem.isDefined =>
        wrong("an acquisition item", Pipeline.EnrichSource)
      case _ if p != Pipeline.CompileArticle && compileOpts => wrong("a title, kind or filter", Pipeline.CompileArticle)
      case _ if req.rounds.exists(n => n < 1 || n > roundsCap) =>
        Left(Api.Error(s"rounds must be between 1 and $roundsCap (agents.review.rounds-cap)"))
      case Pipeline.CompileArticle =>
        val exists = h.research.articleStore.exists(target)
        val filter =
          req.query.exists(_.trim.nonEmpty) || req.topics.exists(_.trim.nonEmpty) || req.tags.exists(_.trim.nonEmpty)
        for
          _ <- req.kind.fold[Either[Api.Error, String]](Right(""))(k => Articles.kindOf(k).left.map(Api.Error(_)))
          _ <- Either.cond(
            exists || filter,
            (),
            Api.Error(
              s"'$target' is a new article: give a filter (query, topics or tags) " +
                "for the sources to compile it from"
            )
          )
          _ <- Either.cond(
            exists || req.title.exists(_.trim.nonEmpty),
            (),
            Api.Error(s"'$target' is a new article: give its title")
          )
        yield ()
      case _ =>
        req.acquireItem.fold(Right(()))(id =>
          Queue.fulfillable(id, h.queues.get(QueueItemId(id))).map(_ => ()).left.map(Api.Error(_))
        )

  def runRoles(p: Pipeline): List[Role] =
    (p.roles ++ (if p == Pipeline.CompileArticle then Pipeline.ReviewArticle.roles else Nil)).distinct

  def linkRef(kind: LinkKind, raw: com.histmersion.server.agents.ServerAutomationHost.LinkRef): Either[Api.Error, com.histmersion.server.agents.ServerAutomationHost.LinkTarget] =
    if kind == LinkKind.Article then h.research.articles.get(raw).map(_.article.slug)
    else h.library.sources.get(raw, body = false).map(_.source.citekey)

  def probeSubject(citekey: com.histmersion.domain.library.Citekey): Either[Api.Error, ProbeAnswer] =
    h.library.sources.get(citekey, body = false).map(s => ProbeAnswer(s.source.citekey, s.source.title, s.source.year))

  def sameTitle(answered: com.histmersion.domain.Title, record: com.histmersion.domain.Title): Boolean = AgentVerify.sameTitle(answered, record)

  def workLookup: PlanGoal.Lookup = doi => upstream().meta(doi).map(_.transformInto[PlanGoal.Work])
  def vocabulary(field: com.histmersion.server.agents.ServerAutomationHost.FacetField): List[com.histmersion.server.agents.ServerAutomationHost.Term] =
    h.library.labels.facets(field, None, Filter()).toOption.toList.flatten.sortBy(-_.count).map(_.value)
  def housekeepingBlock: Option[com.histmersion.server.agents.ServerAutomationHost.Reason] = h.upkeep.housekeepingBlock
  def backup(): Either[Api.Error, Backup.Status] = h.upkeep.backup()
  def backupStatus: Either[Api.Error, Backup.Status] = h.upkeep.backupStatus
  def lintFix(): Either[Api.Error, LintFixResult] = h.checks.guardedLintFix(Nil)

object ServerAutomationHost:
  type FacetField = FacetField.T
  object FacetField extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkRef = LinkRef.T
  object LinkRef extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkTarget = LinkTarget.T
  object LinkTarget extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Reason = Reason.T
  object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RoundsLimit = RoundsLimit.T
  object RoundsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunTarget = RunTarget.T
  object RunTarget extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Term = Term.T
  object Term extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
