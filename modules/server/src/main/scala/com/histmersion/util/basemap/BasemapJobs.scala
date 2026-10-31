package com.histmersion.util.basemap

import com.histmersion.api.{ApiError, JobInfo}

/** The background job a base-map fetch runs as: one at a time, behind the server's write guard, its row in the jobs
  * table (`histmersion jobs` follows it). The composition root runs it on the server's database, on the generation the
  * caller is pinned to, and lets a person pause or cancel it.
  */
trait BasemapJobs:
  /** The job running now, named; `None` when none is. */
  def running: Option[String]

  /** `body`, unless the server is refusing writes now (`op` names the write in that refusal). */
  def guarded[A](op: String)(body: => Either[ApiError, A]): Either[ApiError, A]

  /** The refusal of work asked for while a job runs: `what` is the kind of work, `busyCode` its error code. */
  def busy(what: String, busyCode: Option[String]): ApiError

  /** `body` started as a job of `kind` described by `about`, given a progress reporter and answering its summary or why
    * it failed; `Left` — [[busy]] — while another runs.
    */
  def start(kind: String, about: String, what: String, busyCode: Option[String])(
      body: (String => Unit) => Either[String, String]
  ): Either[ApiError, JobInfo]
