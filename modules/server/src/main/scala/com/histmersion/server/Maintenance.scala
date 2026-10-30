package com.histmersion.server

import scala.util.chaining.*
import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

import java.util.concurrent.atomic.AtomicBoolean
import scala.jdk.CollectionConverters.*

/** The maintenance lock (plan §1.10). A restore drops and recreates every table, so no other write may run while it
  * does: [[exclusively]] takes the flag, every write handler goes through [[guard]] and is answered 503 while it is
  * held.
  *
  * A plain `AtomicBoolean` is enough because there is exactly one server process per database (§2.5: the server is the
  * only DB client) and a restore is a single, whole-process operation. One instance per [[Handlers]] — i.e. per server
  * — rather than a global `object`, so two servers in one JVM (which is what a test run is) do not freeze each other's
  * writes.
  */
final class Maintenance:
  private val busy = AtomicBoolean(false)

  def active: Boolean = busy.get()

  /** Run `f` unless a restore is in progress, in which case answer 503. */
  def guard[A](op: String)(f: => Either[ApiError, A]): Either[ApiError, A] =
    if active then Left(Backup.maintenance(op)) else f

  /** Run `f` holding the lock; a second concurrent attempt is refused rather than queued. */
  def exclusively[A](op: String)(f: => Either[ApiError, A]): Either[ApiError, A] =
    if !busy.compareAndSet(false, true) then Left(Backup.maintenance(op))
    else
      try f
      finally busy.set(false)
