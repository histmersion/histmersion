package com.histmersion.api.catalogue
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.jobs.JobKindEntry
import com.histmersion.api.jobs.JobDetail

/** The events' jobs, as the catalogue registers them: a catalogue's import, and the re-dating of its events. */
object EventsJobKinds:

  /** The catalogue an events job reads, as a person names it, in the job's detail. */
  type Catalogue = CatalogueValue
  type CatalogueValue = CatalogueValue.T
  object CatalogueValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Catalogue: JobDetail.Field[Option[com.histmersion.api.catalogue.EventsJobKinds.Catalogue]] = JobDetail.Field(_root_.com.histmersion.api.jobs.JobDetail.Field.Name("events"), None)

  val EventsImport: JobKindEntry = JobKindEntry.of(
    JobInfo.Kind("events-import"),
    _root_.com.histmersion.api.jobs.JobKindEntry.OfName("Import events"),
    _root_.com.histmersion.api.jobs.JobKindEntry.OfMeaning("Imports the events of an external event catalogue."),
    stoppable = _root_.com.histmersion.api.jobs.JobKindEntry.OfStoppable(true),
    subject = Catalogue.of
  )

  val EventsRederive: JobKindEntry = JobKindEntry.of(
    JobInfo.Kind("events-rederive"),
    _root_.com.histmersion.api.jobs.JobKindEntry.OfName("Re-date events"),
    _root_.com.histmersion.api.jobs.JobKindEntry.OfMeaning("Recomputes the dating of catalogue events from their stored records; nothing is downloaded."),
    subject = Catalogue.of
  )
