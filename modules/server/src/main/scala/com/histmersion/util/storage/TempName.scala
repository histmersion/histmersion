package com.histmersion.util.storage

/** How the temporary file of a [[Reservation]] beside its key is named. */
enum TempName:
  /** `<name>.tmp`: one writer at a time, and a reader of the directory knows to skip it. */
  case Fixed

  /** `.<name>.<random>.tmp`: unique, hidden. */
  case Unique
