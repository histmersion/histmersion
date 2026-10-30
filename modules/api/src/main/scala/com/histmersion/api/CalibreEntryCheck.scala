package com.histmersion.api

/** Calibre lives on the host, out of the server's sight, so the CLI checks the entry: a person may have removed it
  * already.
  */
enum CalibreEntryCheck:
  case Present

  case Missing

  case Unchecked(reason: String)
