package com.histmersion.domain.automation.workflows

import com.histmersion.domain.automation.agents.RunCtx

import com.histmersion.api.automation.Queue.QueueAdd
import com.histmersion.domain.automation.worklists.WorkListControllers
import io.circe.syntax.*

/** What every agent pipeline does the same way (#1946) — each had its own copy:
  *   - [[log]]: a run ends with one line in the worklog (`queue log`), written through the run's write gate, so a dry
  *     run records the write it would have made instead.
  */
private[histmersion] object Pipelines:

  /** The goal a run works for: its own, else the goal it targets. */
  def goalOf(run: com.histmersion.api.automation.Agents.RunInfo): Option[String] =
    run.goal
      .orElse(Option.when(run.targetKind == com.histmersion.domain.automation.TargetKind.Goal.wire)(run.target))
      .filter(_.nonEmpty)

  /** A bookkeeping item a run writes: it names the run and the goal the run works for. */
  def entry(ctx: RunCtx, text: String, topic: Option[String] = None): QueueAdd =
    QueueAdd(text, topic = topic, goal = goalOf(ctx.run), job = Some(ctx.run.id))

  /** Add `line` to the worklog; answers the line. */
  def log(workLists: WorkListControllers, ctx: RunCtx, line: String): Either[String, String] =
    val entry = Pipelines.entry(ctx, line)
    ctx.write("log line", entry.asJson)(workLists.add("log", entry).left.map(_.message).map(_ => line))(Right(line))
