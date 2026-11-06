package com.histmersion.api.catalogue
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.jobs.JobKindEntry

/** The evidence's jobs, as the catalogue registers them: the search for the sources that may attest each event. */
object EvidenceJobKinds:
  val EventsCandidates: JobKindEntry = JobKindEntry.of(
    JobInfo.Kind("events-candidates"),
    _root_.com.histmersion.api.jobs.JobKinds.Wire("Find sources for events"),
    _root_.com.histmersion.api.jobs.JobKindMeaning("Looks for the sources that may attest each catalogue event: candidate links for you to confirm or reject."),
    subject = com.histmersion.api.catalogue.EventsJobKinds.Catalogue.of
  )
