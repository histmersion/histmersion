package com.histmersion.util.storage

/** How the temporary file of a [[Reservation]] beside its key is named. */
enum TempName:
  /** `<name>.tmp`: one writer at a time, and a reader of the directory knows to skip it. */
  case Fixed

  /** `.<name>.<random>.tmp`: unique, hidden. */
  case Unique

  /** `<name><suffix>`: one writer at a time, named as the readers of the directory expect (`.part`). */
  case Suffixed(suffix: String)
