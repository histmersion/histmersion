package com.histmersion.domain.library.acquisition

/** Who ingests by identifier: which fetchers it asks (`legalChain`: never one the config marks unauthorised), and
  * whether a work it found no file for is refused instead of stored from its metadata (`needsFile`).
  */
enum IngestPath(val legalChain: Boolean, val needsFile: Boolean):
  /** `histmersion ingest <doi>`. */
  case Person extends IngestPath(false, false)

  /** `histmersion ingest <doi> --legal-only`. */
  case PersonLegal extends IngestPath(true, false)

  /** A watcher's auto-ingest, the acquisition list's re-check. */
  case Watcher extends IngestPath(true, true)
