package com.histmersion.util.basemap

import com.histmersion.util.jobs.JobKindEntry
import com.histmersion.values.JobKind

/** The base map's jobs, as it registers them: the download of its layers for an area. */
object BasemapJobKinds:
  val BasemapFetch: JobKindEntry = JobKindEntry.of(
    JobKind("basemap-fetch"),
    "Download the background map",
    "Downloads and assembles base-map layers (vector map, terrain relief, sea floor) for an area.",
    stoppable = true,
    subject = _.about
  )
