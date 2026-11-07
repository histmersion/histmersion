package com.histmersion.domain.library.acquisition

/** Who ingests by identifier: which fetchers it asks (`legalChain`: never one the config marks unauthorised), and
  * whether a work it found no file for is refused instead of stored from its metadata (`needsFile`).
  */
enum IngestPath(val legalChain: IngestPath.LegalChain, val needsFile: IngestPath.NeedsFile):
  /** `histmersion ingest <doi>`. */
  case Person extends IngestPath(com.histmersion.domain.library.acquisition.IngestPath.LegalChain(false), com.histmersion.domain.library.acquisition.IngestPath.NeedsFile(false))

  /** `histmersion ingest <doi> --legal-only`. */
  case PersonLegal extends IngestPath(com.histmersion.domain.library.acquisition.IngestPath.LegalChain(true), com.histmersion.domain.library.acquisition.IngestPath.NeedsFile(false))

  /** A watcher's auto-ingest, the acquisition list's re-check. */
  case Watcher extends IngestPath(com.histmersion.domain.library.acquisition.IngestPath.LegalChain(true), com.histmersion.domain.library.acquisition.IngestPath.NeedsFile(true))

object IngestPath:
  type LegalChain = LegalChain.T
  object LegalChain extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NeedsFile = NeedsFile.T
  object NeedsFile extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
