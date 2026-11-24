package com.histmersion
package api.basemap
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.jobs.JobKindEntry
import com.histmersion.api.jobs.JobKinds
import com.histmersion.api.jobs.JobKindMeaning
import com.histmersion.api.jobs.JobDetail
import com.histmersion.lint.{LintRule, tolerated}

/** The base map's jobs, as it registers them: the download of its layers for an area. */
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
object BasemapJobKinds:
  val BasemapFetch: JobKindEntry = JobKindEntry.of(
    JobInfo.Kind("basemap-fetch"),
    JobKinds.Wire("Download the background map"),
    JobKindMeaning("Downloads and assembles base-map layers (vector map, terrain relief, sea floor) for an area."),
    stoppable = JobKindEntry.OfStoppable(true),
    subject = JobDetail.Purpose.of
  )
