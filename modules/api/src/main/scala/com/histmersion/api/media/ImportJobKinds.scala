package com.histmersion.api.media
import com.histmersion.api.jobs.{JobDetailField, JobKindEntry}
import com.histmersion.util.jobs.Job

/** The media's imports, as it registers them: pictures asked of a provider. */
object ImportJobKinds:

  /** What a media job does, as a person reads it, in the job's detail. */
  val Media: JobDetailField[Option[String]] = JobDetailField("media", None)

  val MediaImport: JobKindEntry = JobKindEntry.of(
    Job.Kind("media-import"),
    "Fetch pictures",
    "Asks a picture provider (Wikidata, museums, the Library of Congress…) for pictures of places and events.",
    stoppable = true,
    subject = Media.of
  )
