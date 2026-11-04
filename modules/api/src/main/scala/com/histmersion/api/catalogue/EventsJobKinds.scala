package com.histmersion.api.catalogue
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.jobs.JobKindEntry
import com.histmersion.api.jobs.JobDetail

/** The events' jobs, as the catalogue registers them: a catalogue's import, and the re-dating of its events. */
object EventsJobKinds:

  /** The catalogue an events job reads, as a person names it, in the job's detail. */
  val Catalogue: JobDetail.Field[Option[String]] = JobDetail.Field("events", None)

  val EventsImport: JobKindEntry = JobKindEntry.of(
    JobInfo.Kind("events-import"),
    "Import events",
    "Imports the events of an external event catalogue.",
    stoppable = true,
    subject = Catalogue.of
  )

  val EventsRederive: JobKindEntry = JobKindEntry.of(
    JobInfo.Kind("events-rederive"),
    "Re-date events",
    "Recomputes the dating of catalogue events from their stored records; nothing is downloaded.",
    subject = Catalogue.of
  )
