package com.histmersion
package domain.automation.workflows

import io.scalaland.chimney.dsl.*
import com.histmersion.api.automation.Agents
import com.histmersion.domain.automation.Goal
import com.histmersion.domain.automation.TargetKind
import com.histmersion.api.automation.agents.RunInfo
import com.histmersion.api.automation.Queue
import com.histmersion.api.SpatialHierarchyResult
import com.histmersion.api.automation.queue.QueueAdd
import com.histmersion.api.automation.queue.QueueItem
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.problem.Problems
import com.histmersion.domain.automation.worklists.WorkListControllers

import com.histmersion.domain.automation.agents.{Halt, RunCtx}

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.*
import com.histmersion.api.VocabularyCodecs.given
import sttp.ai.core.agent.*

import scala.jdk.CollectionConverters.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.database.WritePermit
import com.histmersion.domain.Creator
import com.histmersion.lint.{tolerated, LintRule}

/** A pipeline: Scala code that calls [[RunCtx.code]] and [[RunCtx.llm]] steps in a FIXED order (the step number is the
  * order, so a resumed run lines its steps up with its checkpoints). Returns a one-line summary for the run's log
  * entry.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
trait PipelineDef:
  def pipeline: Pipeline
  def run(ctx: RunCtx)(using WritePermit)(using Creator.New): Either[Halt, PipelineDef.Summary]

object PipelineDef:
  type Summary = Summary.T
  object Summary extends Newtype[String, Pure]

  type LogLine = LogLine.T
  object LogLine extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def goalOf(run: RunInfo): Option[Goal.Slug] =
    run.goal
      .map(_.value)
      .orElse(Option.when(run.targetKind.value == TargetKind.Goal.wire.value)(run.target.value))
      .filter(_.nonEmpty)
      .map(Goal.Slug.assume(_))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def entry(ctx: RunCtx, text: QueueItem.Text, topic: Option[QueueItem.Topic] = None): QueueAdd =
    QueueAdd(
      text,
      topic = topic,
      goal = goalOf(ctx.run).map(g => com.histmersion.api.automation.Goal.Slug(g.value)),
      job = Some(SpatialHierarchyResult.Job.Id(ctx.run.id.value))
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def log(workLists: WorkListControllers, ctx: RunCtx, line: LogLine): Either[Problems, LogLine] =
    val item = entry(ctx, QueueItem.Text(line.value))
    ctx.write("log line", item.asJson)(
      workLists
        .record(WorkListControllers.ListName("log"), item, ctx.creator)
        .left
        .map(e => e.problems)
        .map(_ => line)
    )(Right(line))
