package com.histmersion
package domain

import java.time.Instant

/** What became of a referenced record since the reference was stored. */
enum RecordState derives CanEqual:
  case Live

  /** The record is the same; its key is another now (the old one is an alias). */
  case Renamed(now: ItemKey)

  /** A merge folded the record into another one, which answers for it. */
  case MergedInto(ref: ItemRef)

  /** The record was deleted; the reference stays, shown as gone. */
  case Deleted(at: Instant)
