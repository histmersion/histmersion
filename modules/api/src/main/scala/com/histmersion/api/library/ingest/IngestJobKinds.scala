package com.histmersion.api.library.ingest

import com.histmersion.util.jobs.JobKindEntry
import com.histmersion.values.JobKind

/** The library's ingest jobs, as it registers them: bringing a source in, and reading its text again. */
object IngestJobKinds:
  val Ingest: JobKindEntry = JobKindEntry.of(
    JobKind("ingest"),
    "Bring in a source",
    "Brings in a source: fetches its open-access file or takes your upload, converts it to text (OCR) and stores it."
  )

  val Reocr: JobKindEntry =
    JobKindEntry.of(
      JobKind("reocr"),
      "Read a source's text again",
      "Converts a stored source's original file to text (OCR) again."
    )
