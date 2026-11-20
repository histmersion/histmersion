package com.histmersion
package domain.library.acquisition

import io.github.iltotore.iron.constraint.any.Pure

/** Who ingests by identifier: which fetchers it asks (`legalChain`: never one the config marks unauthorised), and
  * whether a work it found no file for is refused instead of stored from its metadata (`needsFile`).
  */
enum IngestPath(val legalChain: IngestPath.LegalChain, val needsFile: IngestPath.NeedsFile) derives CanEqual:
  /** `histmersion ingest <doi> --allow-unauthorised`: a person's explicit choice. */
  case Person extends IngestPath(IngestPath.LegalChain(false), IngestPath.NeedsFile(false))

  /** `histmersion ingest <doi>`, and an agent's ingest. */
  case PersonLegal extends IngestPath(IngestPath.LegalChain(true), IngestPath.NeedsFile(false))

  /** A watcher's auto-ingest, the acquisition list's re-check. */
  case Watcher extends IngestPath(IngestPath.LegalChain(true), IngestPath.NeedsFile(true))

object IngestPath:
  type LegalChain = LegalChain.T
  object LegalChain extends Newtype[Boolean, Pure]
  type NeedsFile = NeedsFile.T
  object NeedsFile extends Newtype[Boolean, Pure]
