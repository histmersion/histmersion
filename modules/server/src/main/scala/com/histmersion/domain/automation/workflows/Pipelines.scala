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
  type EntryText = EntryText.T
  object EntryText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EntryTopic = EntryTopic.T
  object EntryTopic extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LogResultPart1 = LogResultPart1.T
  object LogResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LogResultPart2 = LogResultPart2.T
  object LogResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LogLine = LogLine.T
  object LogLine extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def goalOf(run: com.histmersion.api.automation.Agents.RunInfo): Option[com.histmersion.domain.automation.Goal.Slug] =
    run.goal
      .orElse(Option.when(run.targetKind == com.histmersion.domain.automation.TargetKind.Goal.wire)(run.target))
      .filter(_.nonEmpty)

  /** A bookkeeping item a run writes: it names the run and the goal the run works for. */
  def entry(ctx: RunCtx, text: com.histmersion.domain.automation.workflows.Pipelines.EntryText, topic: Option[com.histmersion.domain.automation.workflows.Pipelines.EntryTopic] = None): QueueAdd =
    QueueAdd(text, topic = topic, goal = goalOf(ctx.run), job = Some(ctx.run.id))

  /** Add `line` to the worklog; answers the line. */
  def log(workLists: WorkListControllers, ctx: RunCtx, line: com.histmersion.domain.automation.workflows.Pipelines.LogLine): Either[com.histmersion.domain.automation.workflows.Pipelines.LogResultPart1, com.histmersion.domain.automation.workflows.Pipelines.LogResultPart2] =
    val entry = Pipelines.entry(ctx, line)
    ctx.write("log line", entry.asJson)(workLists.add(com.histmersion.domain.automation.worklists.WorkListControllers.ListName("log"), entry).left.map(_.message).map(_ => line))(Right(line))
