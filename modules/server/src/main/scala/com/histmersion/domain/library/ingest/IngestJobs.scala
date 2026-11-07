package com.histmersion.domain.library.ingest

/** The seam between an acquisition and the ingest's job queue.
  *
  * `histmersion ingest <doi>` fetches the PDF server-side and then hands the FILE to the same job path an upload takes:
  * convert → body → assets. The implementation the server runs is [[QueuedIngest]]; [[SkeletonIngest]] remains the
  * metadata-only half it builds on (and the whole story when no file was acquired).
  */
trait IngestJobs:
  /** @param copy
    *   the acquired file in the acquisition spool, or `None` when no PDF was found (the source is still created from
    *   metadata, so it can be enriched and queued)
    */
  def enqueue(copy: Option[FetchedCopy], citekey: com.histmersion.domain.library.Citekey, meta: IngestMeta): Either[com.histmersion.domain.library.ingest.IngestJobs.Problem, IngestOutcome]

object IngestJobs:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
