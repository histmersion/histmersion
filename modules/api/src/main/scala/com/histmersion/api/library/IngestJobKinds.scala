package com.histmersion.api.library
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.jobs.JobKindEntry

/** The library's ingest jobs, as it registers them: bringing a source in, and reading its text again. */
object IngestJobKinds:
  val Ingest: JobKindEntry = JobKindEntry.of(
    JobInfo.Kind("ingest"),
    _root_.com.histmersion.api.jobs.JobKinds.Wire("Bring in a source"),
    _root_.com.histmersion.api.jobs.JobKindMeaning("Brings in a source: fetches its open-access file or takes your upload, converts it to text (OCR) and stores it.")
  )

  val Reocr: JobKindEntry =
    JobKindEntry.of(
      JobInfo.Kind("reocr"),
      _root_.com.histmersion.api.jobs.JobKinds.Wire("Read a source's text again"),
      _root_.com.histmersion.api.jobs.JobKindMeaning("Converts a stored source's original file to text (OCR) again.")
    )
