package com.histmersion.server.agents

import com.histmersion.domain.automation.agents.RunCtx
import com.histmersion.domain.automation.workflows.Pipelines

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.GoalPlans.LinkKind
import com.histmersion.api.Queue.QueueAdd
import com.histmersion.db.GoalLinkRepo
import com.histmersion.server.Handlers
import io.circe.syntax.*

import javax.sql.DataSource
import com.histmersion.api.VocabularyCodecs.given

/** What both goal sweeps share: the goal's sources, the DOIs already in the acquisition queue, and adding an item there
  * at most once.
  */
private[agents] trait GoalSweep:
  protected def h: Handlers
  protected def ds: DataSource

  /** The sources a goal is about: linked directly, and cited by the articles linked to it. */
  protected def goalSources(goal: String, max: Int): List[String] =
    val direct = GoalLinkRepo.refs(ds, goal, LinkKind.Source)
    val viaArts = GoalLinkRepo
      .refs(ds, goal, LinkKind.Article)
      .flatMap(a => h.getArticle(a).toOption.toList)
      .flatMap(_.sourcesCited.filter(_.title.isDefined).map(_.citekey))
    (direct ++ viaArts).distinct.take(math.max(0, max))

  /** Every DOI in the acquisition queue (open or done) → its item id. */
  protected def queuedDois(): Map[String, Int] =
    h.ops
      .queueList("acquire", all = true)
      .toOption
      .toList
      .flatMap(_.items)
      .flatMap(i => Scheduling.dois(i.text).map(_ -> i.id))
      .toMap

  /** Queue `text` for acquisition unless `doi` is already queued — in the run's snapshot of the list, or added since
    * (another watcher's sweep: looked up again right before adding); link the item to the goal either way.
    * `Right(true)` = a new item (a dry run: one it would add). Both writes go through the run's gate.
    */
  protected def queueOnce(
      ctx: RunCtx,
      goal: String,
      doi: String,
      text: String,
      queued: Map[String, Int]
  ): Either[String, Boolean] =
    queued.get(doi).orElse(h.acquisitionItemNaming(doi)) match
      case Some(id) =>
        ctx.write(
          s"link queue item $id to goal $goal",
          io.circe.Json.obj("goal" -> goal.asJson, "item" -> id.asJson, "doi" -> doi.asJson)
        ) { val _ = GoalLinkRepo.link(ds, goal, LinkKind.QueueItem, id.toString, Some(doi)) }(())
        Right(false)
      case None =>
        val item = QueueAdd(text, topic = Some(s"goal $goal"), goal = Some(goal), job = Some(ctx.run.id))
        ctx.write(s"acquire item for $doi, linked to goal $goal", item.asJson)(
          h.ops.queueAdd("acquire", item).left.map(_.message).map { added =>
            val _ = GoalLinkRepo.link(ds, goal, LinkKind.QueueItem, added.id.toString, Some(doi))
            true
          }
        )(Right(true))

  protected def log(ctx: RunCtx, line: String): Either[String, String] = Pipelines.log(h, ctx, line)
