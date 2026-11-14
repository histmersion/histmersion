package com.histmersion
package plugins.calibre
import com.histmersion.util.problem.Problems

import io.github.iltotore.iron.constraint.any.Pure

/** What `calibre update --all` does, or did, to one Calibre entry. */
enum UpdateOutcome derives CanEqual:
  /** Dry run: these fields would change. */
  case Change(changes: List[Calibre.Change])
  case Applied(changes: List[Calibre.Change])
  case Failed(changes: List[Calibre.Change], reason: Problems)
  case UpToDate

  /** Linked from more than one record — which one it should show is a person's decision. */
  case Shared

  /** No such entry in Calibre. */
  case Missing

  /** The record itself fails the metadata guard. */
  case Refused(reason: Problems)
