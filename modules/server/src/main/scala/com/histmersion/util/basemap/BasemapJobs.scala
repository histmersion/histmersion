package com.histmersion.util.basemap

import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.Api

/** The background job a base-map fetch runs as: one at a time, behind the server's write guard, its row in the jobs
  * table (`histmersion jobs` follows it). The composition root runs it on the server's database, on the generation the
  * caller is pinned to, and lets a person pause or cancel it.
  */
trait BasemapJobs:
  /** The job running now, named; `None` when none is. */
  def running: Option[com.histmersion.util.basemap.BasemapJobs.Description]

  /** `body`, unless the server is refusing writes now (`op` names the write in that refusal). */
  def guarded[A](op: com.histmersion.util.basemap.BasemapJobs.Operation)(body: => Either[Api.Error, A]): Either[Api.Error, A]

  /** The refusal of work asked for while a job runs: `what` is the kind of work, `busyCode` its error code. */
  def busy(what: com.histmersion.util.basemap.BasemapJobs.Target, busyCode: Option[com.histmersion.util.basemap.BasemapJobs.Reason]): Api.Error

  /** `body` started as a job of `kind` described by `about`, given a progress reporter and answering its summary or why
    * it failed; `Left` — [[busy]] — while another runs.
    */
  def start(kind: com.histmersion.util.jobs.Job.Kind, about: com.histmersion.util.basemap.BasemapJobs.Description, what: com.histmersion.util.basemap.BasemapJobs.Target, busyCode: Option[com.histmersion.util.basemap.BasemapJobs.Reason])(
      body: (com.histmersion.util.basemap.BasemapJobs.Target => Unit) => Either[com.histmersion.util.basemap.BasemapJobs.Problem, com.histmersion.util.jobs.Job.Id]
  ): Either[Api.Error, JobInfo]

object BasemapJobs:
  type Description = Description.T
  object Description extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Operation = Operation.T
  object Operation extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Reason = Reason.T
  object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Target = Target.T
  object Target extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
