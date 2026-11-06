package com.histmersion.api.media
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.jobs.JobKindEntry
import com.histmersion.api.jobs.JobDetail
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

/** The media's imports, as it registers them: pictures asked of a provider. */
object ImportJobKinds:
  /** What a media job does, as a person reads it, in the job's detail. */
  val Media: JobDetail.Field[Option[com.histmersion.api.jobs.JobKinds.Subject]] = new JobDetail.Field(_root_.com.histmersion.api.jobs.JobDetail.Field.Name("media"), None)

  val MediaImport: JobKindEntry = JobKindEntry.of(
    JobInfo.Kind("media-import"),
    _root_.com.histmersion.api.jobs.JobKinds.Wire("Fetch pictures"),
    _root_.com.histmersion.api.jobs.JobKindMeaning("Asks a picture provider (Wikidata, museums, the Library of Congress…) for pictures of places and events."),
    stoppable = _root_.com.histmersion.api.jobs.JobKindEntry.OfStoppable(true),
    subject = Media.of
  )
