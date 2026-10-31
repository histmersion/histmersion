package com.histmersion.plugins.calibre

/** What `calibre update --all` does, or did, to one Calibre entry. */
enum UpdateOutcome:
  /** Dry run: these fields would change. */
  case Change(changes: List[CalibreChange])
  case Applied(changes: List[CalibreChange])
  case Failed(changes: List[CalibreChange], reason: String)
  case UpToDate

  /** Linked from more than one record — which one it should show is a person's decision. */
  case Shared

  /** No such entry in Calibre. */
  case Missing

  /** The record itself fails the metadata guard. */
  case Refused(reason: String)
