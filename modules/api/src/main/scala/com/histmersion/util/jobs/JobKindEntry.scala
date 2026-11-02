package com.histmersion.util.jobs

import com.histmersion.api.JobDetail
import com.histmersion.values.JobKind

/** A kind of job as its owner registers it: its name and meaning for a person, whether a person may pause and cancel
  * its jobs, whether those go to the kind's own runner instead (an agent run's), and what one of its jobs is about,
  * read from the detail its jobs store.
  */
final case class JobKindEntry(
    kind: JobKind,
    name: JobKindName,
    meaning: JobKindMeaning,
    stoppable: Boolean = false,
    subject: JobDetail => Option[String] = _ => None,
    ownControl: Boolean = false
)

object JobKindEntry:
  /** An entry from its owner's words. */
  def of(
      kind: JobKind,
      name: String,
      meaning: String,
      stoppable: Boolean = false,
      subject: JobDetail => Option[String] = _ => None,
      ownControl: Boolean = false
  ): JobKindEntry = JobKindEntry(kind, JobKindName(name), JobKindMeaning(meaning), stoppable, subject, ownControl)
