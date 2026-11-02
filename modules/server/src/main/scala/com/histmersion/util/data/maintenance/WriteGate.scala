package com.histmersion.util.data.maintenance

import com.histmersion.api.ApiError
import com.histmersion.api.backup.Backup

import java.util.concurrent.atomic.AtomicBoolean

/** The maintenance lock. A restore drops and recreates every table, so no other write may run while it does:
  * [[exclusively]] takes the flag, every write goes through [[guard]] and is answered 503 while it is held.
  *
  * A plain `AtomicBoolean` is enough because there is exactly one server process per database (the server is the only
  * database client) and a restore is a single, whole-process operation. One instance per server rather than a global,
  * so two servers in one JVM (which is what a test run is) do not freeze each other's writes.
  */
final class WriteGate:
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
