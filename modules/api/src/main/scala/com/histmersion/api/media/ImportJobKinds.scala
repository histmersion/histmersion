package com.histmersion.api.media
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.jobs.JobKindEntry
import com.histmersion.api.jobs.JobDetail

/** The media's imports, as it registers them: pictures asked of a provider. */
object ImportJobKinds:
  type MediaText = MediaText.T
  object MediaText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** What a media job does, as a person reads it, in the job's detail. */
  type Media = MediaValue

  val Media: JobDetail.Field[Option[com.histmersion.api.media.ImportJobKinds.Media]] = JobDetail.Field(_root_.com.histmersion.api.jobs.JobDetail.Field.Name("media"), None)

  val MediaImport: JobKindEntry = JobKindEntry.of(
    JobInfo.Kind("media-import"),
    _root_.com.histmersion.api.jobs.JobKinds.Wire("Fetch pictures"),
    _root_.com.histmersion.api.jobs.JobKindMeaning("Asks a picture provider (Wikidata, museums, the Library of Congress…) for pictures of places and events."),
    stoppable = _root_.com.histmersion.api.jobs.JobKindEntry.OfStoppable(true),
    subject = Media.of
  )
