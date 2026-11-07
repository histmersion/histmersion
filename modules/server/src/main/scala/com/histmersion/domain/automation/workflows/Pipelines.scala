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

  type EntryText = com.histmersion.api.automation.Queue.QueueItem.Text
  val EntryText: com.histmersion.api.automation.Queue.QueueItem.Text.type = com.histmersion.api.automation.Queue.QueueItem.Text
  type EntryTopic = com.histmersion.api.automation.Queue.QueueItem.Topic
  val EntryTopic: com.histmersion.api.automation.Queue.QueueItem.Topic.type = com.histmersion.api.automation.Queue.QueueItem.Topic
  type LogLine = LogLine.T
  object LogLine extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The goal a run works for: its own, else the goal it targets (the run record is our own). */
  def goalOf(run: com.histmersion.api.automation.Agents.RunInfo): Option[com.histmersion.domain.automation.Goal.Slug] =
    run.goal
      .map(_.value)
      .orElse(Option.when(run.targetKind.value == com.histmersion.domain.automation.TargetKind.Goal.wire.value)(run.target.value))
      .filter(_.nonEmpty)
      .map(com.histmersion.domain.automation.Goal.Slug.assume(_))

  /** A bookkeeping item a run writes: it names the run and the goal the run works for. */
  def entry(ctx: RunCtx, text: EntryText, topic: Option[EntryTopic] = None): QueueAdd =
    QueueAdd(
      text,
      topic = topic,
      goal = goalOf(ctx.run).map(g => com.histmersion.api.automation.Goal.Slug(g.value)),
      job = Some(com.histmersion.api.SpatialHierarchyResult.Job.Id(ctx.run.id.value))
    )

  /** Add `line` to the worklog; answers the line. */
  def log(workLists: WorkListControllers, ctx: RunCtx, line: LogLine): Either[com.histmersion.api.Api.Error.Message, LogLine] =
    val entry = Pipelines.entry(ctx, EntryText(line.value))
    ctx.write("log line", entry.asJson)(workLists.add(com.histmersion.domain.automation.worklists.WorkListControllers.ListName("log"), entry).left.map(_.message).map(_ => line))(Right(line))
