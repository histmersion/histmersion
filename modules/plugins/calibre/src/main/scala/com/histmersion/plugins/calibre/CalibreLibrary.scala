package com.histmersion.plugins.calibre

/** Where the library is and how it is reached, resolved from the plugin's settings: the library's folder (`~`
  * expanded), the `calibredb` and `sqlite3` to run, and the process whose presence means the Calibre GUI holds the
  * library.
  */
final case class CalibreLibrary(library: String, calibredb: String, sqlite: String, guiProcess: String)
