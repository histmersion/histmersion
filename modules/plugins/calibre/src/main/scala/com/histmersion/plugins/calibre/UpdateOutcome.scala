package com.histmersion
package plugins.calibre

import io.github.iltotore.iron.constraint.any.Pure

/** What `calibre update --all` does, or did, to one Calibre entry. */
enum UpdateOutcome:
  /** Dry run: these fields would change. */
  case Change(changes: List[Calibre.Change])
  case Applied(changes: List[Calibre.Change])
  case Failed(changes: List[Calibre.Change], reason: UpdateOutcome.Failed.Reason)
  case UpToDate

  /** Linked from more than one record — which one it should show is a person's decision. */
  case Shared

  /** No such entry in Calibre. */
  case Missing

  /** The record itself fails the metadata guard. */
  case Refused(reason: UpdateOutcome.Refused.Reason)

object UpdateOutcome:
  object Failed:
    type Reason = Reason.T
    object Reason extends Newtype[String, Pure]
  object Refused:
    type Reason = Reason.T
    object Reason extends Newtype[String, Pure]
