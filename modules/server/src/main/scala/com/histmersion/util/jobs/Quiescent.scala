package com.histmersion
package util.jobs

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.database.WritePermit
import com.histmersion.domain.Creator

/** A long-running worker of the server (a pool, a loop, a background job's thread) as a restore holds it. A restore
  * replaces the whole database, so every worker registers with the server's [[Workers]]: the restore asks each to
  * [[quiesce]], waits a bounded time for it to come to rest, restores, has each take up the restored rows
  * ([[restored]]: what it holds in memory is reconciled with them), and [[resume]]s them. A worker still at work when
  * the wait runs out is fenced (its units get no connection during the restore nor after it), so a slow one delays
  * nothing and writes nothing.
  */
trait Quiescent:

  /** What the restore's report and refusal call it. */
  def name: Quiescent.Name

  /** Stop taking new work, and ask the work in flight to come to rest (a run pauses at its next step). Returns at once.
    */
  def quiesce(): Unit

  /** The work still in flight, each described; empty once the worker is at rest. */
  def busy: Vector[Quiescent.Busy]

  /** After a restore, on the restore's own thread (the only one the database answers then): drop what this worker holds
    * in memory about rows the restore replaced, and take up the restored ones as a start does.
    */
  def restored()(using WritePermit)(using Creator.New): Unit

  /** Take work again. */
  def resume(): Unit

object Quiescent:
  type Name = Name.T
  object Name extends Newtype[String, Pure]

  type Busy = Busy.T
  object Busy extends Newtype[String, Pure]
