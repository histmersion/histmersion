package com.histmersion
package api.library
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.jobs.JobKindEntry
import com.histmersion.api.jobs.JobKinds
import com.histmersion.api.jobs.JobKindMeaning
import com.histmersion.lint.{LintRule, tolerated}

/** The library's ingest jobs, as it registers them: bringing a source in, and reading its text again. */
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
object IngestJobKinds:
  val Ingest: JobKindEntry = JobKindEntry.of(
    JobInfo.Kind("ingest"),
    JobKinds.Wire("Bring in a source"),
    JobKindMeaning(
      "Brings in a source: fetches its open-access file or takes your upload, converts it to text (OCR) and stores it."
    )
  )

  val Reocr: JobKindEntry =
    JobKindEntry.of(
      JobInfo.Kind("reocr"),
      JobKinds.Wire("Read a source's text again"),
      JobKindMeaning("Converts a stored source's original file to text (OCR) again.")
    )
