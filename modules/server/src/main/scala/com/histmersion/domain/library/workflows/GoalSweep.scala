package com.histmersion.domain.library.workflows

import io.scalaland.chimney.dsl.*

import com.histmersion.domain.automation.agents.RunCtx
import com.histmersion.domain.automation.goals.GoalService
import com.histmersion.domain.automation.workflows.Pipelines
import com.histmersion.domain.automation.worklists.QueueScope

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Goal.Plans.LinkKind
import com.histmersion.api.automation.Queue.QueueAdd
import io.circe.syntax.*
import com.histmersion.api.VocabularyCodecs.given
import com.histmersion.api.automation.Queue

/** What both goal sweeps share: the goal's sources, the DOIs already in the acquisition queue, and adding an item there
  * at most once.
  */
private[workflows] trait GoalSweep:
  protected def site: LibrarySite
  protected def goals: GoalService

  /** The sources a goal is about: linked directly, and cited by the articles linked to it. */
  protected def goalSources(goal: com.histmersion.domain.automation.Goal.Slug, max: com.histmersion.domain.library.workflows.GoalSweep.Limit): List[com.histmersion.domain.library.workflows.GoalSweep.GoalSourcesResult] =
    val direct = goals.refs(goal, com.histmersion.domain.automation.Goal.LinkKind.Source)
    val viaArts = goals.refs(goal, com.histmersion.domain.automation.Goal.LinkKind.Article).flatMap(site.citedSources)
    (direct ++ viaArts).distinct.take(math.max(0, max))

  /** Every DOI in the acquisition queue (open or done) → its item id. */
  protected def queuedDois(): Map[com.histmersion.domain.library.Doi, com.histmersion.RowCount] =
    site.queues
      .list(Queue.Kind.Acquire, QueueScope.All)
      .flatMap(i => Scheduling.dois(i.text).map(_ -> i.id))
      .toMap

  /** Queue `text` for acquisition unless `doi` is already queued — in the run's snapshot of the list, or added since
    * (another watcher's sweep: looked up again right before adding); link the item to the goal either way.
    * `Right(true)` = a new item (a dry run: one it would add). Both writes go through the run's gate.
    */
  protected def queueOnce(
      ctx: RunCtx,
      goal: com.histmersion.domain.automation.Goal.Slug,
      doi: com.histmersion.domain.library.Doi,
      text: com.histmersion.domain.library.workflows.GoalSweep.QueueOnceText,
      queued: Map[com.histmersion.domain.library.workflows.GoalSweep.QueueOnceQueuedPart1, com.histmersion.RowCount]
  ): Either[com.histmersion.domain.library.workflows.GoalSweep.Problem, Boolean] =
    queued.get(doi).orElse(site.queues.naming(Queue.Kind.Acquire, doi).headOption.map(_.id)) match
      case Some(id) =>
        ctx.write(
          s"link queue item $id to goal $goal",
          io.circe.Json.obj("goal" -> goal.asJson, "item" -> id.asJson, "doi" -> doi.asJson)
        ) { val _ = goals.link(goal, io.scalaland.chimney.Transformer.derive[com.histmersion.api.automation.Goal.LinkKind, com.histmersion.domain.automation.Goal.LinkKind].transform(LinkKind.QueueItem), id.toString, Some(doi)) }(())
        Right(false)
      case None =>
        val item = QueueAdd(text, topic = Some(s"goal $goal"), goal = Some(goal), job = Some(ctx.run.id))
        ctx.write(s"acquire item for $doi, linked to goal $goal", item.asJson)(
          site.workLists.add("acquire", item).left.map(_.message).map { added =>
            val _ = goals.link(goal, io.scalaland.chimney.Transformer.derive[com.histmersion.api.automation.Goal.LinkKind, com.histmersion.domain.automation.Goal.LinkKind].transform(LinkKind.QueueItem), added.id.toString, Some(doi))
            true
          }
        )(Right(true))

  protected def log(ctx: RunCtx, line: com.histmersion.domain.library.workflows.GoalSweep.LogLine): Either[com.histmersion.domain.library.workflows.GoalSweep.Problem, com.histmersion.domain.library.workflows.GoalSweep.LogResultPart2] = Pipelines.log(site.workLists, ctx, line)

object GoalSweep:
  type Limit = Limit.T
  object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GoalSourcesResult = GoalSourcesResult.T
  object GoalSourcesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueueOnceText = QueueOnceText.T
  object QueueOnceText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueueOnceQueuedPart1 = QueueOnceQueuedPart1.T
  object QueueOnceQueuedPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LogResultPart2 = LogResultPart2.T
  object LogResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LogLine = LogLine.T
  object LogLine extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
