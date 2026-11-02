package com.histmersion.api.catalogue
import com.histmersion.api.jobs.JobKindEntry
import com.histmersion.util.jobs.Job

/** The evidence's jobs, as the catalogue registers them: the search for the sources that may attest each event. */
object EvidenceJobKinds:
  val EventsCandidates: JobKindEntry = JobKindEntry.of(
    Job.Kind("events-candidates"),
    "Find sources for events",
    "Looks for the sources that may attest each catalogue event: candidate links for you to confirm or reject.",
    subject = com.histmersion.api.catalogue.EventsJobKinds.Catalogue.of
  )
