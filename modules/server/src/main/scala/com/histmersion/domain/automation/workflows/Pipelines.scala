package com.histmersion
package domain.automation.workflows
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.problem.{Problem, Problems}
import io.scalaland.chimney.dsl.*

import com.histmersion.domain.automation.agents.RunCtx

import com.histmersion.api.automation.Queue.QueueAdd
import com.histmersion.domain.automation.worklists.WorkListControllers
import com.histmersion.api.automation.Queue
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.automation.Agents
import com.histmersion.domain.automation.Goal
import com.histmersion.domain.automation.TargetKind
import com.histmersion.api.SpatialHierarchyResult
import com.histmersion.api.Api

/** What every agent pipeline does the same way (#1946) — each had its own copy:
  *   - [[log]]: a run ends with one line in the worklog (`queue log`), written through the run's write gate, so a dry
  *     run records the write it would have made instead.
  */
private[histmersion] object Pipelines:

  export Queue.QueueItem.{Text as EntryText, Topic as EntryTopic}
  type LogLine = LogLine.T
  object LogLine extends Newtype[String, Pure]

  /** The goal a run works for: its own, else the goal it targets (the run record is our own). */
  def goalOf(run: Agents.RunInfo): Option[Goal.Slug] =
    run.goal
      .map(_.value)
      .orElse(Option.when(run.targetKind.value == TargetKind.Goal.wire.value)(run.target.value))
      .filter(_.nonEmpty)
      .map(Goal.Slug.assume(_))

  /** A bookkeeping item a run writes: it names the run and the goal the run works for. */
  def entry(ctx: RunCtx, text: EntryText, topic: Option[EntryTopic] = None): QueueAdd =
    QueueAdd(
      text,
      topic = topic,
      goal = goalOf(ctx.run).map(g => com.histmersion.api.automation.Goal.Slug(g.value)),
      job = Some(SpatialHierarchyResult.Job.Id(ctx.run.id.value))
    )

  /** Add `line` to the worklog; answers the line. */
  def log(workLists: WorkListControllers, ctx: RunCtx, line: LogLine): Either[Problems, LogLine] =
    val entry = Pipelines.entry(ctx, EntryText(line.value))
    ctx.write("log line", entry.asJson)(
      workLists
        .add(WorkListControllers.ListName("log"), entry)
        .left
        .map(e => e.problems)
        .map(_ => line)
    )(Right(line))
