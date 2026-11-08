package com.histmersion
package api.media
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.jobs.JobKindEntry
import com.histmersion.api.jobs.JobKinds
import com.histmersion.api.jobs.JobKindMeaning

/** The sources' pictures, as the media registers them: their covers and illustrations. */
object CoversJobKinds:
  val MediaCovers: JobKindEntry = JobKindEntry.of(
    JobInfo.Kind("media-covers"),
    JobKinds.Wire("Fetch source covers"),
    JobKindMeaning(
      "Pictures of sources: Open Library covers by ISBN, and an illustration picked from a source's own figures."
    ),
    stoppable = JobKindEntry.OfStoppable(true),
    subject = ImportJobKinds.Media.of
  )
