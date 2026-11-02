package com.histmersion.api.catalogue
import com.histmersion.api.jobs.{JobDetailField, JobKindEntry}
import com.histmersion.values.JobKind

/** The events' jobs, as the catalogue registers them: a catalogue's import, and the re-dating of its events. */
object EventsJobKinds:

  /** The catalogue an events job reads, as a person names it, in the job's detail. */
  val Catalogue: JobDetailField[Option[String]] = JobDetailField("events", None)

  val EventsImport: JobKindEntry = JobKindEntry.of(
    JobKind("events-import"),
    "Import events",
    "Imports the events of an external event catalogue.",
    stoppable = true,
    subject = Catalogue.of
  )

  val EventsRederive: JobKindEntry = JobKindEntry.of(
    JobKind("events-rederive"),
    "Re-date events",
    "Recomputes the dating of catalogue events from their stored records; nothing is downloaded.",
    subject = Catalogue.of
  )
