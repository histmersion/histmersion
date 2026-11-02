package com.histmersion.api.media.imports

import com.histmersion.util.jobs.JobKindEntry
import com.histmersion.values.JobKind

/** The media's imports, as it registers them: pictures asked of a provider. */
object ImportJobKinds:
  val MediaImport: JobKindEntry = JobKindEntry.of(
    JobKind("media-import"),
    "Fetch pictures",
    "Asks a picture provider (Wikidata, museums, the Library of Congress…) for pictures of places and events.",
    stoppable = true,
    subject = _.media
  )
