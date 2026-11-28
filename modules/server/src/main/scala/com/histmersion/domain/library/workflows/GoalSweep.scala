package com.histmersion
package domain.library.workflows

import com.histmersion.api.automation.queue.Kind
import com.histmersion.api.automation.queue.QueueAdd
import com.histmersion.domain.automation.workflows.PipelineDef
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json

import com.histmersion.util.problem.Problems
import com.histmersion.util.problem.Text.{sentence, sentences}
import com.histmersion.domain.automation.agents.RunCtx
import com.histmersion.domain.automation.goals.GoalService
import com.histmersion.domain.automation.worklists.QueueScope

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.VocabularyCodecs.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.automation.Queue
import com.histmersion.api.automation.Goal.Plans.GoalLink
import com.histmersion.domain.automation.Goal
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.research.Slug
import com.histmersion.domain.library.Doi
import com.histmersion.api.jobs.JobInfo
import com.histmersion.domain.automation.worklists.WorkListControllers
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.database.WritePermit
import com.histmersion.domain.Creator
import com.histmersion.lint.{tolerated, LintRule}

/** What both goal sweeps share: the goal's sources, the DOIs already in the acquisition queue, and adding an item there
  * at most once.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
private[workflows] trait GoalSweep:
  protected def site: LibrarySite
  protected def goals: GoalService

  /** The goal a run is about (the run record is our own). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  protected def goalOf(ctx: RunCtx): Goal.Slug =
    Goal.Slug.assume(ctx.run.target.value)

  /** The sources a goal is about: linked directly, and cited by the articles linked to it. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  protected def goalSources(goal: Goal.Slug, max: GoalSweep.Limit): List[Citekey] =
    val direct = goals.refs(goal, Goal.LinkKind.Source).map(r => Citekey.assume(r.value))
    val viaArts = goals
      .refs(goal, Goal.LinkKind.Article)
      .flatMap(r => site.citedSources(Slug.assume(r.value)))
    (direct ++ viaArts).distinct.take(math.max(0, max.value))

  /** Every DOI in the acquisition queue (open or done) → its item id. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  protected def queuedDois(): Map[Doi, api.automation.queue.QueueItem.Id] =
    site.queues
      .list(Kind.Acquire, QueueScope.All)
      .flatMap(i => Scheduling.dois(i.text.value).map(_ -> i.id))
      .toMap

  /** Queue `text` for acquisition unless `doi` is already queued — in the run's snapshot of the list, or added since
    * (another watcher's sweep: looked up again right before adding); link the item to the goal either way.
    * `Right(true)` = a new item (a dry run: one it would add). Both writes go through the run's gate.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  protected def queueOnce(
      ctx: RunCtx,
      goal: Goal.Slug,
      doi: Doi,
      text: api.automation.queue.QueueItem.Text,
      queued: Map[Doi, api.automation.queue.QueueItem.Id]
  )(using WritePermit)(using Creator.New): Either[String, Boolean] =
    queued.get(doi).orElse(site.queues.naming(Kind.Acquire, doi).headOption.map(_.id)) match
      case Some(id) =>
        ctx.write(
          s"link queue item $id to goal $goal",
          Json.obj("goal" -> goal.asJson, "item" -> id.asJson, "doi" -> doi.asJson)
        ) {
          goals.linkIfThere(
            goal,
            Goal.LinkKind.QueueItem,
            GoalLink.Ref(id.value.toString),
            Some(GoalLink.Note(doi.value))
          )
        }(())
        Right(false)
      case None =>
        val item = QueueAdd(
          text,
          topic = Some(api.automation.queue.QueueItem.Topic(s"goal $goal")),
          goal = Some(com.histmersion.api.automation.Goal.Slug(goal.value)),
          job = Some(JobInfo.Id(ctx.run.id.value))
        )
        ctx.write(s"acquire item for $doi, linked to goal $goal", item.asJson)(
          site.workLists
            .record(WorkListControllers.ListName("acquire"), item, ctx.creator)
            .left
            .map(_.problems.sentences.mkString("; "))
            .map { added =>
              goals.linkIfThere(
                goal,
                Goal.LinkKind.QueueItem,
                GoalLink.Ref(added.id.value.toString),
                Some(GoalLink.Note(doi.value))
              )
              true
            }
        )(Right(true))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  protected def log(ctx: RunCtx, line: String): Either[Problems, String] =
    PipelineDef.log(site.workLists, ctx, PipelineDef.LogLine(line)).map(_.value)

object GoalSweep:
  type Limit = Limit.T
  object Limit extends Newtype[Int, Pure]
