package com.histmersion.api.jobs

/** A kind of job as its owner registers it: its name and meaning for a person, whether a person may pause and cancel
  * its jobs, whether those go to the kind's own runner instead (an agent run's), and what one of its jobs is about,
  * read from the detail its jobs store.
  */
final case class JobKindEntry(
    kind: JobInfo.Kind,
    name: JobKindName,
    meaning: JobKindMeaning,
    stoppable: JobKindEntry.Stoppable = false,
    subject: JobDetail => Option[String] = _ => None,
    ownControl: JobKindEntry.OwnControl = false
)

object JobKindEntry:
  /** An entry from its owner's words. */
  type Stoppable = Stoppable.T
  object Stoppable extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OwnControl = OwnControl.T
  object OwnControl extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def of(
      kind: JobInfo.Kind,
      name: String,
      meaning: String,
      stoppable: Boolean = false,
      subject: JobDetail => Option[String] = _ => None,
      ownControl: Boolean = false
  ): JobKindEntry = JobKindEntry(kind, JobKindName(name), JobKindMeaning(meaning), stoppable, subject, ownControl)
