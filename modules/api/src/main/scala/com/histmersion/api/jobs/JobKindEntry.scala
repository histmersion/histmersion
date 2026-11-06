package com.histmersion.api.jobs

/** A kind of job as its owner registers it: its name and meaning for a person, whether a person may pause and cancel
  * its jobs, whether those go to the kind's own runner instead (an agent run's), and what one of its jobs is about,
  * read from the detail its jobs store.
  */
final case class JobKindEntry(
    kind: JobInfo.Kind,
    name: JobKindName,
    meaning: JobKindMeaning,
    stoppable: JobKindEntry.Stoppable = _root_.com.histmersion.api.jobs.JobKindEntry.Stoppable(false),
    subject: JobDetail => Option[JobKindEntry.SubjectResult] = _ => None,
    ownControl: JobKindEntry.OwnControl = _root_.com.histmersion.api.jobs.JobKindEntry.OwnControl(false)
)

object JobKindEntry:
  /** An entry from its owner's words. */
  type SubjectResult = SubjectResult.T
  object SubjectResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type OfName = OfName.T
  object OfName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfMeaning = OfMeaning.T
  object OfMeaning extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfStoppable = _root_.com.histmersion.api.jobs.JobKindEntry.Stoppable
  val OfStoppable: _root_.com.histmersion.api.jobs.JobKindEntry.Stoppable.type = _root_.com.histmersion.api.jobs.JobKindEntry.Stoppable
  type OfSubjectResult = OfSubjectResult.T
  object OfSubjectResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfOwnControl = _root_.com.histmersion.api.jobs.JobKindEntry.OwnControl
  val OfOwnControl: _root_.com.histmersion.api.jobs.JobKindEntry.OwnControl.type = _root_.com.histmersion.api.jobs.JobKindEntry.OwnControl

  type Stoppable = Stoppable.T
  object Stoppable extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OwnControl = OwnControl.T
  object OwnControl extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def of(
      kind: JobInfo.Kind,
      name: com.histmersion.api.jobs.JobKindEntry.OfName,
      meaning: com.histmersion.api.jobs.JobKindEntry.OfMeaning,
      stoppable: com.histmersion.api.jobs.JobKindEntry.OfStoppable = _root_.com.histmersion.api.jobs.JobKindEntry.OfStoppable(false),
      subject: JobDetail => Option[com.histmersion.api.jobs.JobKindEntry.OfSubjectResult] = _ => None,
      ownControl: com.histmersion.api.jobs.JobKindEntry.OfOwnControl = _root_.com.histmersion.api.jobs.JobKindEntry.OfOwnControl(false)
  ): JobKindEntry = JobKindEntry(kind, JobKindName((name).value), JobKindMeaning((meaning).value), stoppable, { val refactorFunction2 = subject; (refactorValue3) => (refactorFunction2(refactorValue3)).map(refactorValue4 => _root_.com.histmersion.api.jobs.JobKindEntry.SubjectResult((refactorValue4).value)) }, ownControl)
