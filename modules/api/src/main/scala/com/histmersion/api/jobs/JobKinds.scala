package com.histmersion
package api.jobs

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** Every kind of background job the server runs, as their owners registered them, in the order the jobs window lists
  * them, and the fields their jobs' detail may hold, in the order the detail shows them. A kind nobody registered is
  * shown by its identifier.
  */
final class JobKinds(val entries: List[JobKindEntry], val details: List[JobDetail.Field[?]] = Nil):
  private val byWire: Map[JobKinds.Wire, JobKindEntry] = entries.map(e => e.kind -> e).toMap

  def kinds: List[JobInfo.Kind] = entries.map(_.kind)

  def get(kind: JobInfo.Kind): Option[JobKindEntry] = byWire.get(kind)

  /** The kind a stored or typed identifier names (case and surrounding space ignored). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(wire: JobKinds.Wire): Option[JobKindEntry] = byWire.get(JobKinds.Wire((wire).value.trim.toLowerCase))

  /** May a person pause and cancel a job of the kind `wire` names? */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def stoppable(wire: JobKinds.Wire): Boolean = (byWire
    .get(JobKinds.Wire((wire).value))
    .exists((refactorCallback2 => ((refactorCallback2.stoppable) == JobKindEntry.Stoppable(true)))))

  /** Do the pause, resume and cancel of a job of the kind `wire` names go to the kind's own runner? */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def ownControl(wire: JobKinds.Wire): Boolean = (byWire
    .get(JobKinds.Wire((wire).value))
    .exists((refactorCallback2 => ((refactorCallback2.ownControl) == JobKindEntry.OwnControl(true)))))

  /** The identifiers of the kinds a person may pause and cancel. */
  def stoppableKinds: Set[JobKinds.Wire] = entries
    .filter((refactorCallback => ((refactorCallback.stoppable) == JobKindEntry.Stoppable(true))))
    .map(_.kind)
    .toSet

  /** What a job is about: its source, else what its detail says. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def subject(job: JobInfo): Option[JobKinds.Subject] =
    job.citekey
      .map(c => JobKinds.Subject(c.value.toString))
      .orElse(job.detail.flatMap(d => entries.iterator.flatMap(_.subject(d)).nextOption()))

object JobKinds:
  type Subject = Subject.T
  object Subject extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]
