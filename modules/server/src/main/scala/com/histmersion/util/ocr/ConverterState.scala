package com.histmersion
package util.ocr

import com.histmersion.api.Problem
import com.histmersion.config.*
import com.histmersion.domain.*

import scala.jdk.OptionConverters.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.jobs.Job

/** What `/api/health` says about the OCR sidecar (#692). */
enum ConverterState derives CanEqual:
  case Ok

  /** A conversion is in flight and the probe got no answer: the single worker is busy, not down. */
  case Busy(jobId: ConverterState.Busy.JobId)
  case Down(reason: Problem)

  /** The `converter` line of [[Health]]. */
  def render: ConverterState.Summary = this match
    case Ok           => ConverterState.Summary("ok")
    case Busy(id)     => ConverterState.Summary(s"converter busy: job $id")
    case Down(reason) => ConverterState.Summary(reason.value)

  /** Busy is healthy: the server is doing exactly what it is for. */
  def healthy: Boolean = this match
    case Down(_) => false
    case _       => true

object ConverterState:
  type Probe = Probe.T
  object Probe extends Newtype[String, Pure]
  type Summary = Summary.T
  object Summary extends Newtype[String, Pure]

  /** From the raw probe answer (`"ok"` or why not) and the job the converter is working on, if any. */
  object Busy:
    type JobId = Job.Id
    val JobId: Job.Id.type = Job.Id


  def of(probe: Probe, busyJob: Option[Job.Id]): ConverterState =
    if probe.value == "ok" then Ok else busyJob.fold(Down(Problem(probe.value)))(Busy(_))
