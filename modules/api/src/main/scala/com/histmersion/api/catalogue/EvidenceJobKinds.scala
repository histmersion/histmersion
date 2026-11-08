package com.histmersion
package api.catalogue
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.jobs.JobKindEntry
import com.histmersion.api.jobs.JobKinds
import com.histmersion.api.jobs.JobKindMeaning

/** The evidence's jobs, as the catalogue registers them: the search for the sources that may attest each event. */
object EvidenceJobKinds:
  val EventsCandidates: JobKindEntry = JobKindEntry.of(
    JobInfo.Kind("events-candidates"),
    JobKinds.Wire("Find sources for events"),
    JobKindMeaning("Looks for the sources that may attest each catalogue event: candidate links for you to confirm or reject."),
    subject = EventsJobKinds.Catalogue.of
  )
