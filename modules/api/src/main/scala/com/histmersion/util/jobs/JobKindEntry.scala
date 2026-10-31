package com.histmersion.util.jobs

import com.histmersion.api.JobDetail
import com.histmersion.values.JobKind

/** A kind of job as its owner registers it: its name and meaning for a person, whether a person may pause and cancel
  * its jobs, and what one of its jobs is about, read from the detail its jobs store.
  */
final case class JobKindEntry(
    kind: JobKind,
    name: JobKindName,
    meaning: JobKindMeaning,
    stoppable: Boolean = false,
    subject: JobDetail => Option[String] = _ => None
)
