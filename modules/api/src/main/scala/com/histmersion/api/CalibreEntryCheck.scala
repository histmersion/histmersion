package com.histmersion.api

/** What the host's Calibre library says of a deleted source's linked entry (#1776). The server cannot see the library,
  * so the CLI looks it up (read-only, `Calibre.byId`) before it prints the answer: the record's stored `calibreId` may
  * name an entry a person has already removed.
  */
enum CalibreEntryCheck:
  /** The entry is in the library. */
  case Present

  /** The library was read and has no entry with that id. */
  case Missing

  /** The library could not be read (no library configured, `calibredb` and `sqlite3` both failed). */
  case Unchecked(reason: String)
