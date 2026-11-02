package com.histmersion.api.catalogue.evidence

import com.histmersion.util.jobs.JobKindEntry
import com.histmersion.values.JobKind

/** The evidence's jobs, as the catalogue registers them: the search for the sources that may attest each event. */
object EvidenceJobKinds:
  val EventsCandidates: JobKindEntry = JobKindEntry.of(
    JobKind("events-candidates"),
    "Find sources for events",
    "Looks for the sources that may attest each catalogue event: candidate links for you to confirm or reject.",
    subject = com.histmersion.api.catalogue.events.EventsJobKinds.Catalogue.of
  )
