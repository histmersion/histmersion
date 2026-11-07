package com.histmersion.util.jobs

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match

object Job:
  type Error = Error.T
  object Error extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** A background job's id: a UUID in its lower-case text form, as the `jobs` table keeps it. */
  type Id = Id.T

  object Id extends RefinedType[String, Match["[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"]]

  /** A kind of background job, as the `jobs` table and the wire name it (`ingest`, `basemap-fetch`, …). Each owner
    * registers the kinds it runs, with how they show ([[com.histmersion.api.jobs.JobKinds]]).
    */
  type Kind = Kind.T

  object Kind extends RefinedType[String, Match["[a-z][a-z_-]*"]]
