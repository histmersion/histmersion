package com.histmersion
package api.jobs

import io.github.iltotore.iron.constraint.any.Pure

/** Every kind of background job the server runs, as their owners registered them, in the order the jobs window lists
  * them, and the fields their jobs' detail may hold, in the order the detail shows them. A kind nobody registered is
  * shown by its identifier.
  */
final class JobKinds(val entries: List[JobKindEntry], val details: List[JobDetail.Field[?]] = Nil):
  private val byWire: Map[JobKinds.Wire, JobKindEntry] = entries.map(e => e.kind -> e).toMap

  def kinds: List[JobInfo.Kind] = entries.map(_.kind)

  def get(kind: JobInfo.Kind): Option[JobKindEntry] = byWire.get(kind)

  /** The kind a stored or typed identifier names (case and surrounding space ignored). */
  def parse(wire: JobKinds.Wire): Option[JobKindEntry] = byWire.get(JobKinds.Wire((wire).value.trim.toLowerCase))

  /** May a person pause and cancel a job of the kind `wire` names? */
  def stoppable(wire: JobKinds.Wire): Boolean = (byWire.get(JobKinds.Wire((wire).value)).exists((refactorCallback2 => ((refactorCallback2.stoppable) == JobKindEntry.Stoppable(true)))))

  /** Do the pause, resume and cancel of a job of the kind `wire` names go to the kind's own runner? */
  def ownControl(wire: JobKinds.Wire): Boolean = (byWire.get(JobKinds.Wire((wire).value)).exists((refactorCallback2 => ((refactorCallback2.ownControl) == JobKindEntry.OwnControl(true)))))

  /** The identifiers of the kinds a person may pause and cancel. */
  def stoppableKinds: Set[JobKinds.Wire] = entries.filter((refactorCallback => ((refactorCallback.stoppable) == JobKindEntry.Stoppable(true)))).map(_.kind).toSet

  /** What a job is about: its source, else what its detail says. */
  def subject(job: JobInfo): Option[JobKinds.Subject] =
    job.citekey.map(c => JobKinds.Subject(c.value.toString)).orElse(job.detail.flatMap(d => entries.iterator.flatMap(_.subject(d)).nextOption()))

object JobKinds:
  type Subject = Subject.T
  object Subject extends Newtype[String, Pure]
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]
  type SubjectResult = Subject
  val SubjectResult: Subject.type = Subject
