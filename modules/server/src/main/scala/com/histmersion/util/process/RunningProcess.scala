package com.histmersion.util.process

import java.io.{InputStream, OutputStream}
import java.util.concurrent.TimeUnit

/** A child process that was started. */
trait RunningProcess:
  def pid: com.histmersion.util.process.RunningProcess.PidResult
  def isAlive: com.histmersion.util.process.RunningProcess.IsAliveResult

  /** Its output, when it is piped. */
  def output: InputStream

  /** Its input. */
  def input: OutputStream

  /** Wait until it ends; its exit code. */
  def waitFor(): com.histmersion.util.process.RunningProcess.WaitForResult

  /** Wait at most `timeout`; whether it ended. */
  def waitFor(timeout: com.histmersion.util.process.RunningProcess.WaitForTimeout, unit: TimeUnit): Boolean

  /** Ask it to end. */
  def destroy(): Unit

  /** End it now. */
  def destroyForcibly(): Unit

object RunningProcess:
  type PidResult = PidResult.T
  object PidResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsAliveResult = IsAliveResult.T
  object IsAliveResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WaitForResult = WaitForResult.T
  object WaitForResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WaitForTimeout = WaitForTimeout.T
  object WaitForTimeout extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
