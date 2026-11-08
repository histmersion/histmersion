package com.histmersion
package api.catalogue
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.jobs.JobKindEntry
import com.histmersion.api.jobs.JobDetail
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.jobs.JobKinds
import com.histmersion.api.jobs.JobKindMeaning

/** The events' jobs, as the catalogue registers them: a catalogue's import, and the re-dating of its events. */
object EventsJobKinds:

  /** The catalogue an events job reads, as a person names it, in the job's detail. */
  val Catalogue: JobDetail.Field[Option[JobKinds.Subject]] = new JobDetail.Field(JobDetail.Field.Name("events"), None)

  val EventsImport: JobKindEntry = JobKindEntry.of(
    JobInfo.Kind("events-import"),
    JobKinds.Wire("Import events"),
    JobKindMeaning("Imports the events of an external event catalogue."),
    stoppable = JobKindEntry.OfStoppable(true),
    subject = Catalogue.of
  )

  val EventsRederive: JobKindEntry = JobKindEntry.of(
    JobInfo.Kind("events-rederive"),
    JobKinds.Wire("Re-date events"),
    JobKindMeaning("Recomputes the dating of catalogue events from their stored records; nothing is downloaded."),
    subject = Catalogue.of
  )
