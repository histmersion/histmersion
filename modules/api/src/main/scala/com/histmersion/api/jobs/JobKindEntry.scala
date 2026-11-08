package com.histmersion
package api.jobs

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** A kind of job as its owner registers it: its name and meaning for a person, whether a person may pause and cancel
  * its jobs, whether those go to the kind's own runner instead (an agent run's), and what one of its jobs is about,
  * read from the detail its jobs store.
  */
final case class JobKindEntry(
    kind: JobInfo.Kind,
    name: JobKindName,
    meaning: JobKindMeaning,
    stoppable: JobKindEntry.Stoppable = JobKindEntry.Stoppable(false),
    subject: JobDetail => Option[JobKindEntry.SubjectResult] = _ => None,
    ownControl: JobKindEntry.OwnControl = JobKindEntry.OwnControl(false)
)

object JobKindEntry:
  /** An entry from its owner's words. */
  type SubjectResult = JobKinds.Subject
  val SubjectResult: JobKinds.Subject.type = JobKinds.Subject

  export com.histmersion.api.jobs.{JobKindMeaning as OfMeaning}
  type OfStoppable = Stoppable
  val OfStoppable: Stoppable.type = Stoppable
  type OfSubjectResult = JobKinds.Subject
  val OfSubjectResult: JobKinds.Subject.type = JobKinds.Subject
  type OfOwnControl = OwnControl
  val OfOwnControl: OwnControl.type = OwnControl

  type Stoppable = Stoppable.T
  object Stoppable extends RefinedType[Boolean, Pure]
  type OwnControl = OwnControl.T
  object OwnControl extends RefinedType[Boolean, Pure]

  def of(
      kind: JobInfo.Kind,
      name: JobKinds.Wire,
      meaning: JobKindMeaning,
      stoppable: OfStoppable = OfStoppable(false),
      subject: JobDetail => Option[JobKinds.Subject] = _ => None,
      ownControl: OfOwnControl = OfOwnControl(false)
  ): JobKindEntry = JobKindEntry(kind, JobKindName((name).value), JobKindMeaning((meaning).value), stoppable, { val refactorFunction2 = subject; (refactorValue3) => (refactorFunction2(refactorValue3)).map(refactorValue4 => JobKinds.Subject((refactorValue4).value)) }, ownControl)
