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
  def render: com.histmersion.util.ocr.ConverterState.RenderResult = this match
    case Ok           => "ok"
    case Busy(id)     => s"converter busy: job $id"
    case Down(reason) => reason

  /** Busy is healthy: the server is doing exactly what it is for. */
  def healthy: com.histmersion.util.ocr.ConverterState.HealthyResult = this match
    case Down(_) => false
    case _       => true

object ConverterState:
  /** From the raw probe answer (`"ok"` or why not) and the job the converter is working on, if any. */
  object Busy:
    type JobId = JobId.T
    object JobId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  object Down:
    type Reason = Reason.T
    object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type RenderResult = RenderResult.T
  object RenderResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HealthyResult = HealthyResult.T
  object HealthyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfProbe = OfProbe.T
  object OfProbe extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfBusyJob = OfBusyJob.T
  object OfBusyJob extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def of(probe: com.histmersion.util.ocr.ConverterState.OfProbe, busyJob: Option[com.histmersion.util.ocr.ConverterState.OfBusyJob]): ConverterState =
    if probe == "ok" then Ok else busyJob.fold(Down(probe))(Busy(_))
