package com.histmersion
package util.basemap

import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.Api
import com.histmersion.util.jobs.Job
import io.github.iltotore.iron.constraint.any.Pure

/** The background job a base-map fetch runs as: one at a time, behind the server's write guard, its row in the jobs
  * table (`histmersion jobs` follows it). The composition root runs it on the server's database, on the generation the
  * caller is pinned to, and lets a person pause or cancel it.
  */
trait BasemapJobs:
  /** The job running now, named; `None` when none is. */
  def running: Option[BasemapJobs.Description]

  /** `body`, unless the server is refusing writes now (`op` names the write in that refusal). */
  def guarded[A](op: BasemapJobs.Operation)(body: => Either[Api.Error, A]): Either[Api.Error, A]

  /** The refusal of work asked for while a job runs: `what` is the kind of work, `busyCode` its error code. */
  def busy(what: BasemapJobs.Target, busyCode: Option[BasemapJobs.Reason]): Api.Error

  /** `body` started as a job of `kind` described by `about`, given a progress reporter and answering its summary or why
    * it failed; `Left` — [[busy]] — while another runs.
    */
  def start(
      kind: Job.Kind,
      about: BasemapJobs.Description,
      what: BasemapJobs.Target,
      busyCode: Option[BasemapJobs.Reason]
  )(
      body: (BasemapJobs.Target => Unit) => Either[BasemapJobs.Problem, BasemapJobs.Description]
  ): Either[Api.Error, JobInfo]

object BasemapJobs:
  type Description = Description.T
  object Description extends Newtype[String, Pure]
  type Operation = Operation.T
  object Operation extends Newtype[String, Pure]
  type Problem = Problem.T
  object Problem extends Newtype[String, Pure]
  type Reason = Reason.T
  object Reason extends Newtype[String, Pure]
  type Target = Target.T
  object Target extends Newtype[String, Pure]
