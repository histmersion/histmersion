package com.histmersion
package domain.library.acquisition

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.TwoCases

/** Who ingests by identifier: which fetchers it asks (`legalChain`: never one the config marks unauthorised), and
  * whether a work it found no file for is refused instead of stored from its metadata (`needsFile`).
  */
enum IngestPath(val legalChain: IngestPath.LegalChain, val needsFile: IngestPath.NeedsFile) derives CanEqual:
  /** `histmersion ingest <doi> --allow-unauthorised`: a person's explicit choice. */
  case Person extends IngestPath(IngestPath.LegalChain.AnySource, IngestPath.NeedsFile.Fetchable)

  /** `histmersion ingest <doi>`, and an agent's ingest. */
  case PersonLegal extends IngestPath(IngestPath.LegalChain.Legal, IngestPath.NeedsFile.Fetchable)

  /** A watcher's auto-ingest, the acquisition list's re-check. */
  case Watcher extends IngestPath(IngestPath.LegalChain.Legal, IngestPath.NeedsFile.NeedsFile)

object IngestPath:
  enum LegalChain derives CanEqual:
    case Legal, AnySource
  object LegalChain extends TwoCases[LegalChain]:
    extension (legalChain: LegalChain) def legalChain: Boolean = legalChain == Legal
  enum NeedsFile derives CanEqual:
    case NeedsFile, Fetchable
  object NeedsFile extends TwoCases[NeedsFile]:
    extension (needsFile: NeedsFile) def needsFile: Boolean = needsFile == NeedsFile
