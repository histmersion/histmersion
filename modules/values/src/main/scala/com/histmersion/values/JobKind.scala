package com.histmersion.values

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match

/** A kind of background job, as the `jobs` table and the wire name it (`ingest`, `basemap-fetch`, …). Each owner
  * registers the kinds it runs, with how they show ([[com.histmersion.util.jobs.JobKinds]]).
  */
type JobKind = JobKind.T

object JobKind extends RefinedType[String, Match["[a-z][a-z_-]*"]]
