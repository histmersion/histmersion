package com.histmersion
package api.media
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.jobs.JobKindEntry
import com.histmersion.api.jobs.JobDetail
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.jobs.JobKinds
import com.histmersion.api.jobs.JobKindMeaning

/** The media's imports, as it registers them: pictures asked of a provider. */
object ImportJobKinds:
  /** What a media job does, as a person reads it, in the job's detail. */
  val Media: JobDetail.Field[Option[JobKinds.Subject]] = new JobDetail.Field(JobDetail.Field.Name("media"), None)

  val MediaImport: JobKindEntry = JobKindEntry.of(
    JobInfo.Kind("media-import"),
    JobKinds.Wire("Fetch pictures"),
    JobKindMeaning(
      "Asks a picture provider (Wikidata, museums, the Library of Congress…) for pictures of places and events."
    ),
    stoppable = JobKindEntry.OfStoppable.Stoppable,
    subject = Media.of
  )
