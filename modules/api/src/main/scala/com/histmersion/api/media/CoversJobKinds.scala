package com.histmersion.api.media
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.jobs.JobKindEntry

/** The sources' pictures, as the media registers them: their covers and illustrations. */
object CoversJobKinds:
  val MediaCovers: JobKindEntry = JobKindEntry.of(
    JobInfo.Kind("media-covers"),
    _root_.com.histmersion.api.jobs.JobKinds.Wire("Fetch source covers"),
    _root_.com.histmersion.api.jobs.JobKindEntry.OfMeaning("Pictures of sources: Open Library covers by ISBN, and an illustration picked from a source's own figures."),
    stoppable = _root_.com.histmersion.api.jobs.JobKindEntry.OfStoppable(true),
    subject = com.histmersion.api.media.ImportJobKinds.Media.of
  )
