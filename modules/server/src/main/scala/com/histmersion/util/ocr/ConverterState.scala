package com.histmersion.util.ocr

import com.histmersion.files.*

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

import scala.jdk.OptionConverters.*

/** What `/api/health` says about the OCR sidecar (#692). */
enum ConverterState:
  case Ok

  /** A conversion is in flight and the probe got no answer: the single worker is busy, not down. */
  case Busy(jobId: String)
  case Down(reason: String)

  /** The `converter` line of [[Health]]. */
  def render: String = this match
    case Ok           => "ok"
    case Busy(id)     => s"converter busy: job $id"
    case Down(reason) => reason

  /** Busy is healthy: the server is doing exactly what it is for. */
  def healthy: Boolean = this match
    case Down(_) => false
    case _       => true

object ConverterState:
  /** From the raw probe answer (`"ok"` or why not) and the job the converter is working on, if any. */
  def of(probe: String, busyJob: Option[String]): ConverterState =
    if probe == "ok" then Ok else busyJob.fold(Down(probe))(Busy(_))
