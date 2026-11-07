package com.histmersion.util.ocr

import com.histmersion.config.*
import com.histmersion.domain.*

import scala.jdk.OptionConverters.*

/** What `/api/health` says about the OCR sidecar (#692). */
enum ConverterState:
  case Ok

  /** A conversion is in flight and the probe got no answer: the single worker is busy, not down. */
  case Busy(jobId: ConverterState.Busy.JobId)
  case Down(reason: ConverterState.Down.Reason)

  /** The `converter` line of [[Health]]. */
  def render: com.histmersion.util.ocr.ConverterState.Summary = this match
    case Ok           => Summary("ok")
    case Busy(id)     => Summary(s"converter busy: job $id")
    case Down(reason) => Summary(reason.value)

  /** Busy is healthy: the server is doing exactly what it is for. */
  def healthy: Boolean = this match
    case Down(_) => false
    case _       => true

object ConverterState:
  type Probe = Probe.T
  object Probe extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Summary = Summary.T
  object Summary extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  /** From the raw probe answer (`"ok"` or why not) and the job the converter is working on, if any. */
  object Busy:
    type JobId = com.histmersion.util.jobs.Job.Id
    val JobId: _root_.com.histmersion.util.jobs.Job.Id.type = _root_.com.histmersion.util.jobs.Job.Id

  object Down:
    type Reason = Reason.T
    object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  def of(probe: com.histmersion.util.ocr.ConverterState.Probe, busyJob: Option[com.histmersion.util.jobs.Job.Id]): ConverterState =
    if probe.value == "ok" then Ok else busyJob.fold(Down(Down.Reason(probe.value)))(Busy(_))
