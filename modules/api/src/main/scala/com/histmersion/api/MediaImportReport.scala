package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** What an import found and did. The counts per licence come BEFORE anything is stored — a dry run is exactly this
  * report with `stored = updated = linked = 0`.
  *
  * @param targets
  *   places and events asked about
  * @param items
  *   Wikidata items read
  * @param statements
  *   picture statements found on them (P18, P242, …), after the per-property cap
  * @param files
  *   distinct files those statements name
  * @param byLicence
  *   files per licence (SPDX id or `public-domain`), `allowed = false` for the refused
  * @param rejected
  *   files not stored, per reason (licence not allowed, no author to credit, missing…)
  * @param revoked
  *   stored pictures whose licence a re-read no longer allows (kept; listed to review)
  */
final case class MediaImportReport(
    provider: String,
    dryRun: Boolean,
    targets: Int = 0,
    items: Int = 0,
    statements: Int = 0,
    files: Int = 0,
    byLicence: List[MediaCount] = Nil,
    byRole: List[MediaCount] = Nil,
    rejected: List[MediaCount] = Nil,
    stored: Int = 0,
    updated: Int = 0,
    linked: Int = 0,
    categories: Int = 0,
    revoked: List[String] = Nil,
    failed: List[String] = Nil,
    /** Places and events with a Wikidata item that this provider has still not been asked about — what the next run (or
      * the rest of this job) would take, most-cited sites first.
      */
    pending: Int = 0,
    /** Batches a job ran (each recorded before the next, so a stopped job resumes after it). */
    batches: Int = 0,
    /** A collection import's matches (a dry run's, for a person to judge): which record would be tied to which place,
      * and why.
      */
    samples: List[MediaSample] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema

object MediaImportReport:
  given Schema[MediaImportReport] = summon[KindlingsSchema[MediaImportReport]].schema

  /** Pure: two batches' reports as one — the counts added, the lists joined, the later `pending`. */
  def combine(a: MediaImportReport, b: MediaImportReport): MediaImportReport =
    def add(x: List[MediaCount], y: List[MediaCount]) =
      (x ++ y)
        .groupBy(_.key)
        .toList
        .map((k, cs) => MediaCount(k, cs.map(_.n).sum, cs.forall(_.allowed)))
        .sortBy(c => (-c.n, c.key))
    MediaImportReport(
      b.provider,
      b.dryRun,
      a.targets + b.targets,
      a.items + b.items,
      a.statements + b.statements,
      a.files + b.files,
      add(a.byLicence, b.byLicence),
      add(a.byRole, b.byRole),
      add(a.rejected, b.rejected),
      a.stored + b.stored,
      a.updated + b.updated,
      a.linked + b.linked,
      a.categories + b.categories,
      a.revoked ++ b.revoked,
      a.failed ++ b.failed,
      b.pending,
      a.batches + b.batches,
      (a.samples ++ b.samples).take(SampleCap)
    )

  /** The most samples a report carries (a job's summary is not a listing). */
  val SampleCap = 50
