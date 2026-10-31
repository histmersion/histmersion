package com.histmersion.util.jobs

/** How a job's work keeps the generation (database, settings) the code that started it was on, when it runs on a thread
  * of its own: the composition root's hot graph pins it; without one, work runs as it is.
  */
trait WorkCarrier:
  /** `work`, to be run later on another thread on the generation the calling thread sees now. */
  def carryWork(work: () => Unit): () => Unit

object WorkCarrier:
  val none: WorkCarrier = work => work
