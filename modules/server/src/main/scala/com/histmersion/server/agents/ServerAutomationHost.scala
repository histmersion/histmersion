package com.histmersion.server.agents

import com.histmersion.domain.library.workflows.{AgentUpstream, SourceProposals}

import com.histmersion.api.ApiError
import com.histmersion.api.research.Articles
import com.histmersion.api.backup.BackupStatus
import com.histmersion.api.library.Filter
import com.histmersion.api.automation.{Goal, GoalPatch, GoalPut, Queue}
import com.histmersion.api.automation.Agents.{Pipeline, ProbeAnswer, Role, RunRequest}
import com.histmersion.api.automation.GoalPlans.LinkKind
import com.histmersion.domain.AgentVerify
import com.histmersion.domain.automation.worklists.QueueItemId
import com.histmersion.values.TargetKind
import com.histmersion.api.checks.Lint.LintFixResult
import com.histmersion.domain.PlanGoal
import com.histmersion.domain.automation.workflows.AutomationHost
import com.histmersion.server.ServerWiring
import io.scalaland.chimney.dsl.*

/** The engine's [[AutomationHost]] over the composition: the goals through the automation's own controllers, a run's
  * target, options and links checked against the library's sources and research's articles, the plan's works looked up
  * through the library's upstream, the vocabulary of its labels, and the housekeeping of this server.
  */
final class ServerAutomationHost(h: ServerWiring, upstream: () => AgentUpstream) extends AutomationHost:
  def articleExists(slug: String): Boolean = h.research.articleStore.exists(slug)
  def goal(slug: String): Either[ApiError, Goal] = h.goals.get(slug)
  def putGoal(slug: String, in: GoalPut): Either[ApiError, Goal] = h.goals.put(slug, in)
  def patchGoal(slug: String, patch: GoalPatch): Either[ApiError, Goal] = h.goals.patch(slug, patch)
  def runTarget(p: Pipeline, raw: String): Option[Either[ApiError, String]] =
    p.targetKind match
      case TargetKind.Source => Some(h.library.sources.get(raw, body = false).map(_.source.citekey))
      case TargetKind.Article if p == Pipeline.CompileArticle => Some(Articles.slugOf(raw).left.map(ApiError(_)))
      case TargetKind.Article                                 => Some(h.research.articles.get(raw).map(_.article.slug))
      case TargetKind.Goal                                    => Some(h.goals.get(raw.trim).map(_.slug))
      case TargetKind.Manager                                 => None

  def runOptions(p: Pipeline, req: RunRequest, target: String, roundsCap: Int): Either[ApiError, Unit] =
    val wrong = (what: String, owner: Pipeline) =>
      Left(ApiError(s"$what is an option of ${owner.wire}, not of ${p.wire}"))
    val compileOpts =
      req.title.isDefined || req.kind.isDefined || req.query.isDefined || req.topics.nonEmpty || req.tags.nonEmpty
    p match
      case _ if p != Pipeline.ReviewArticle && req.fix.contains(true)   => wrong("fix", Pipeline.ReviewArticle)
      case _ if p != Pipeline.ReviewArticle && req.rounds.isDefined     => wrong("rounds", Pipeline.ReviewArticle)
      case _ if p != Pipeline.EnrichSource && req.acquireItem.isDefined =>
        wrong("an acquisition item", Pipeline.EnrichSource)
      case _ if p != Pipeline.CompileArticle && compileOpts => wrong("a title, kind or filter", Pipeline.CompileArticle)
      case _ if req.rounds.exists(n => n < 1 || n > roundsCap) =>
        Left(ApiError(s"rounds must be between 1 and $roundsCap (agents.review.rounds-cap)"))
      case Pipeline.CompileArticle =>
        val exists = h.research.articleStore.exists(target)
        val filter =
          req.query.exists(_.trim.nonEmpty) || req.topics.exists(_.trim.nonEmpty) || req.tags.exists(_.trim.nonEmpty)
        for
          _ <- req.kind.fold[Either[ApiError, String]](Right(""))(k => Articles.kindOf(k).left.map(ApiError(_)))
          _ <- Either.cond(
            exists || filter,
            (),
            ApiError(
              s"'$target' is a new article: give a filter (query, topics or tags) " +
                "for the sources to compile it from"
            )
          )
          _ <- Either.cond(
            exists || req.title.exists(_.trim.nonEmpty),
            (),
            ApiError(s"'$target' is a new article: give its title")
          )
        yield ()
      case _ =>
        req.acquireItem.fold(Right(()))(id =>
          Queue.fulfillable(id, h.queues.get(QueueItemId(id))).map(_ => ()).left.map(ApiError(_))
        )

  def runRoles(p: Pipeline): List[Role] =
    (p.roles ++ (if p == Pipeline.CompileArticle then Pipeline.ReviewArticle.roles else Nil)).distinct

  def linkRef(kind: LinkKind, raw: String): Either[ApiError, String] =
    if kind == LinkKind.Article then h.research.articles.get(raw).map(_.article.slug)
    else h.library.sources.get(raw, body = false).map(_.source.citekey)

  def probeSubject(citekey: String): Either[ApiError, ProbeAnswer] =
    h.library.sources.get(citekey, body = false).map(s => ProbeAnswer(s.source.citekey, s.source.title, s.source.year))

  def sameTitle(answered: String, record: String): Boolean = AgentVerify.sameTitle(answered, record)

  def workLookup: PlanGoal.Lookup = doi => upstream().meta(doi).map(_.transformInto[PlanGoal.Work])
  def vocabulary(field: String): List[String] =
    h.library.labels.facets(field, None, Filter()).toOption.toList.flatten.sortBy(-_.count).map(_.value)
  def housekeepingBlock: Option[String] = h.upkeep.housekeepingBlock
  def backup(): Either[ApiError, BackupStatus] = h.upkeep.backup()
  def backupStatus: Either[ApiError, BackupStatus] = h.upkeep.backupStatus
  def lintFix(): Either[ApiError, LintFixResult] = h.checks.guardedLintFix(Nil)
