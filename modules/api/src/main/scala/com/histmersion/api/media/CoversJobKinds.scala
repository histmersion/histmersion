package com.histmersion.api.media
import com.histmersion.api.jobs.JobKindEntry
import com.histmersion.values.JobKind

/** The sources' pictures, as the media registers them: their covers and illustrations. */
object CoversJobKinds:
  val MediaCovers: JobKindEntry = JobKindEntry.of(
    JobKind("media-covers"),
    "Fetch source covers",
    "Pictures of sources: Open Library covers by ISBN, and an illustration picked from a source's own figures.",
    stoppable = true,
    subject = com.histmersion.api.media.ImportJobKinds.Media.of
  )
