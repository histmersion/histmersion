package com.histmersion.util.basemap

import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.Api

/** The background job a base-map fetch runs as: one at a time, behind the server's write guard, its row in the jobs
  * table (`histmersion jobs` follows it). The composition root runs it on the server's database, on the generation the
  * caller is pinned to, and lets a person pause or cancel it.
  */
trait BasemapJobs:
  /** The job running now, named; `None` when none is. */
  def running: Option[com.histmersion.util.basemap.BasemapJobs.RunningResult]

  /** `body`, unless the server is refusing writes now (`op` names the write in that refusal). */
  def guarded[A](op: com.histmersion.util.basemap.BasemapJobs.GuardedOp)(body: => Either[Api.Error, A]): Either[Api.Error, A]

  /** The refusal of work asked for while a job runs: `what` is the kind of work, `busyCode` its error code. */
  def busy(what: com.histmersion.util.basemap.BasemapJobs.BusyWhat, busyCode: Option[com.histmersion.util.basemap.BasemapJobs.BusyBusyCode]): Api.Error

  /** `body` started as a job of `kind` described by `about`, given a progress reporter and answering its summary or why
    * it failed; `Left` — [[busy]] — while another runs.
    */
  def start(kind: com.histmersion.util.basemap.BasemapJobs.StartKind, about: com.histmersion.util.basemap.BasemapJobs.StartAbout, what: com.histmersion.util.basemap.BasemapJobs.StartWhat, busyCode: Option[com.histmersion.util.basemap.BasemapJobs.StartBusyCode])(
      body: (com.histmersion.util.basemap.BasemapJobs.StartBodyArg1Arg1 => Unit) => Either[com.histmersion.util.basemap.BasemapJobs.StartBodyResultPart1, com.histmersion.util.basemap.BasemapJobs.StartBodyResultPart2]
  ): Either[Api.Error, JobInfo]

object BasemapJobs:
  type RunningResult = RunningResult.T
  object RunningResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GuardedOp = GuardedOp.T
  object GuardedOp extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BusyWhat = BusyWhat.T
  object BusyWhat extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BusyBusyCode = BusyBusyCode.T
  object BusyBusyCode extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartKind = StartKind.T
  object StartKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartAbout = StartAbout.T
  object StartAbout extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartWhat = StartWhat.T
  object StartWhat extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartBusyCode = StartBusyCode.T
  object StartBusyCode extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartBodyArg1Arg1 = StartBodyArg1Arg1.T
  object StartBodyArg1Arg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartBodyResultPart1 = StartBodyResultPart1.T
  object StartBodyResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StartBodyResultPart2 = StartBodyResultPart2.T
  object StartBodyResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
