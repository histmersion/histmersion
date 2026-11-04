package com.histmersion.api.media
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.jobs.JobKindEntry
import com.histmersion.api.jobs.JobDetail

/** The media's imports, as it registers them: pictures asked of a provider. */
object ImportJobKinds:

  /** What a media job does, as a person reads it, in the job's detail. */
  val Media: JobDetail.Field[Option[String]] = JobDetail.Field("media", None)

  val MediaImport: JobKindEntry = JobKindEntry.of(
    JobInfo.Kind("media-import"),
    "Fetch pictures",
    "Asks a picture provider (Wikidata, museums, the Library of Congress…) for pictures of places and events.",
    stoppable = true,
    subject = Media.of
  )
