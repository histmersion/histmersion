package com.histmersion.util.jobs

import com.histmersion.api.JobInfo
import com.histmersion.values.JobKind

/** Every kind of background job the server runs, as their owners registered them, in the order the jobs window lists
  * them. A kind nobody registered is shown by its identifier.
  */
final class JobKinds(val entries: List[JobKindEntry]):
  private val byWire: Map[String, JobKindEntry] = entries.map(e => e.kind.value -> e).toMap

  def kinds: List[JobKind] = entries.map(_.kind)

  def get(kind: JobKind): Option[JobKindEntry] = byWire.get(kind.value)

  /** The kind a stored or typed identifier names (case and surrounding space ignored). */
  def parse(wire: String): Option[JobKindEntry] = byWire.get(wire.trim.toLowerCase)

  /** May a person pause and cancel a job of the kind `wire` names? */
  def stoppable(wire: String): Boolean = byWire.get(wire).exists(_.stoppable)

  /** Do the pause, resume and cancel of a job of the kind `wire` names go to the kind's own runner? */
  def ownControl(wire: String): Boolean = byWire.get(wire).exists(_.ownControl)

  /** The identifiers of the kinds a person may pause and cancel. */
  def stoppableKinds: Set[String] = entries.filter(_.stoppable).map(_.kind.value).toSet

  /** What a job is about: its source, else what its detail says. */
  def subject(job: JobInfo): Option[String] =
    job.citekey.orElse(job.detail.flatMap(d => entries.iterator.flatMap(_.subject(d)).nextOption()))
