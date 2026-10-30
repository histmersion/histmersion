package com.histmersion.server.acquire

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

import java.nio.file.Path

/** The seam to **stream 2.4's ingest job queue**.
  *
  * `histmersion ingest <doi>` fetches the PDF server-side and then hands the FILE to the same job path an upload takes:
  * convert → body → assets. That queue landed with §1.8, so the implementation wired in `Main` is [[QueuedIngest]];
  * [[SkeletonIngest]] remains the metadata-only half it builds on (and the whole story when no file was acquired).
  */
trait IngestJobs:
  /** @param pdfPath
    *   the acquired file on the SERVER's filesystem, or `None` when no PDF was found (the source is still created from
    *   metadata, so it can be enriched and queued)
    */
  def enqueue(pdfPath: Option[Path], citekey: String, meta: IngestMeta): Either[String, IngestOutcome]
