package com.histmersion.util.process

import java.io.{InputStream, OutputStream}
import java.util.concurrent.TimeUnit

/** A child process that was started. */
trait RunningProcess:
  def pid: com.histmersion.util.process.Processes.Pid
  def isAlive: Boolean

  /** Its output, when it is piped. */
  def output: InputStream

  /** Its input. */
  def input: OutputStream

  /** Wait until it ends; its exit code. */
  def waitFor(): com.histmersion.util.process.ProcessResult.ExitCode

  /** Wait at most `timeout`; whether it ended. */
  def waitFor(timeout: com.histmersion.util.process.Processes.WaitTimeout, unit: TimeUnit): Boolean

  /** Ask it to end. */
  def destroy(): Unit

  /** End it now. */
  def destroyForcibly(): Unit

object RunningProcess:
