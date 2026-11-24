package com.histmersion
package api.jobs

import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.sentences
import com.histmersion.util.nonempty.NonEmptyVector

import io.github.iltotore.iron.constraint.any.Pure
import io.github.iltotore.iron.*
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.SpatialHierarchyResult
import com.histmersion.api.library.Citekey
import com.histmersion.api.Sha256
import com.histmersion.api.automation.Queue
import com.histmersion.lint.{tolerated, LintRule}

/** A job's row. `problems`: every problem a job that failed (or stopped) keeps, in order; none for one that did not. */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
final case class JobInfo(
    id: SpatialHierarchyResult.Job.Id,
    kind: JobKinds.Wire,
    status: JobInfo.Status,
    citekey: Option[Citekey] = None,
    progress: Option[JobInfo.Progress] = None,
    problems: Option[NonEmptyVector[Problem]] = None,
    sha256: Option[Sha256] = None,
    createdAt: JobInfo.CreatedAt = JobInfo.CreatedAt(""),
    updatedAt: JobInfo.UpdatedAt = JobInfo.UpdatedAt(""),
    acquireItem: Option[Queue.QueueItem.Id] = None,
    detail: Option[JobDetail] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema,
      CanEqual:
  def state: JobState = JobState.parse(status).getOrElse(JobState.Failed)
  def isTerminal: Boolean = state.isTerminal
  def succeeded: Boolean = (state == JobState.Done)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def stateLine: String =
    val detail =
      progress.map(p => s"$p").orElse(problems.map(_.sentences.mkString("; "))).map(t => s" — $t").getOrElse("")
    s"$status$detail"
object JobInfo:

  /** A job's kind as the wire names it (`ingest`, `merge-apply`); the server checks it against its registered kinds. */

  export JobState.Wire as Status
  type Progress = Progress.T
  object Progress extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type CreatedAt = CreatedAt.T
  object CreatedAt extends Newtype[String, Pure]
  type UpdatedAt = UpdatedAt.T
  object UpdatedAt extends Newtype[String, Pure]

  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type Kind = JobKinds.Wire
  val Kind: JobKinds.Wire.type = JobKinds.Wire

  /** A job's id as the wire names it; the server checks it is one. */
  export SpatialHierarchyResult.Job.Id
