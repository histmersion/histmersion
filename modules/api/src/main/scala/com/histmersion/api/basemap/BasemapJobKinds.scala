package com.histmersion.api.basemap
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.jobs.JobKindEntry

/** The base map's jobs, as it registers them: the download of its layers for an area. */
object BasemapJobKinds:
  val BasemapFetch: JobKindEntry = JobKindEntry.of(
    JobInfo.Kind("basemap-fetch"),
    _root_.com.histmersion.api.jobs.JobKinds.Wire("Download the background map"),
    _root_.com.histmersion.api.jobs.JobKindMeaning("Downloads and assembles base-map layers (vector map, terrain relief, sea floor) for an area."),
    stoppable = _root_.com.histmersion.api.jobs.JobKindEntry.OfStoppable(true),
    subject = com.histmersion.api.jobs.JobDetail.Purpose.of
  )
