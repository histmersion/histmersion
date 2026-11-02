package com.histmersion.api.catalogue.events

import com.histmersion.util.jobs.JobKindEntry
import com.histmersion.values.JobKind

/** The events' jobs, as the catalogue registers them: a catalogue's import, and the re-dating of its events. */
object EventsJobKinds:
  val EventsImport: JobKindEntry = JobKindEntry.of(
    JobKind("events-import"),
    "Import events",
    "Imports the events of an external event catalogue.",
    stoppable = true,
    subject = _.events
  )

  val EventsRederive: JobKindEntry = JobKindEntry.of(
    JobKind("events-rederive"),
    "Re-date events",
    "Recomputes the dating of catalogue events from their stored records; nothing is downloaded.",
    subject = _.events
  )
