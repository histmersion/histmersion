package com.histmersion.api.basemap
import com.histmersion.api.jobs.JobKindEntry
import com.histmersion.util.jobs.Job

/** The base map's jobs, as it registers them: the download of its layers for an area. */
object BasemapJobKinds:
  val BasemapFetch: JobKindEntry = JobKindEntry.of(
    Job.Kind("basemap-fetch"),
    "Download the background map",
    "Downloads and assembles base-map layers (vector map, terrain relief, sea floor) for an area.",
    stoppable = true,
    subject = com.histmersion.api.jobs.JobDetail.Purpose.of
  )
