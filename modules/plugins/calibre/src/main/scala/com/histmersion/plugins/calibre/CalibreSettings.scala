package com.histmersion.plugins.calibre

import com.histmersion.plugin.PluginConfig.given
import hearth.kindlings.sconfigderivation.ConfigReader

/** The `calibre` plugin's block (`histmersion.plugins.calibre`).
  *
  * @param libraryPath
  *   the library's folder, the one holding `metadata.db` (`~` is the home directory)
  * @param calibredb
  *   Calibre's command-line tool, an absolute path when it is not on the server's `PATH`
  * @param sqlite
  *   the `sqlite3` the read-only fallback runs while the Calibre GUI holds the library
  * @param writeBack
  *   whether the server may write to the library (push, sync, update, set); off makes the collection read-only
  * @param guiProcess
  *   the process whose presence means the Calibre GUI holds the library
  */
final case class CalibreSettings(
    libraryPath: String,
    calibredb: String,
    sqlite: String,
    writeBack: Boolean,
    guiProcess: String
) derives ConfigReader:
  /** The settings as the library is reached, `~` read against `home`. */
  def library(home: Option[String]): CalibreLibrary =
    val path = libraryPath.trim match
      case "~"                                         => home.getOrElse("~")
      case p if p.startsWith("~/") && home.isDefined => home.getOrElse("") + p.drop(1)
      case p                                           => p
    CalibreLibrary(path, calibredb, sqlite, guiProcess)
