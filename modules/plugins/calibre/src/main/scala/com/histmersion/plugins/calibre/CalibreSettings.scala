package com.histmersion.plugins.calibre

import com.histmersion.plugin.PluginConfig.given
import hearth.kindlings.sconfigderivation.ConfigReader

import java.nio.file.{Files, Path}

/** The `calibre` plugin's block (`histmersion.plugins.calibre`).
  *
  * @param libraryPath
  *   the library's folder, the one holding `metadata.db` (`~` is the home directory)
  * @param calibredb
  *   Calibre's command-line tool: a bare name is looked up on the server's `PATH`, then where Calibre installs it
  *   ([[CalibreSettings.InstallDirs]]), since a LaunchAgent's `PATH` holds only the system directories
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
  /** The settings as the library is reached, `~` read against `home`, a bare `calibredb` found on `searchPath` or in
    * Calibre's install directories (kept bare when it is in none, so the failure names it as configured).
    */
  def library(
      home: Option[String],
      searchPath: Option[String] = None,
      executable: Path => Boolean = Files.isExecutable(_)
  ): CalibreLibrary =
    val path = libraryPath.trim match
      case "~"                                       => home.getOrElse("~")
      case p if p.startsWith("~/") && home.isDefined => home.getOrElse("") + p.drop(1)
      case p                                         => p
    CalibreLibrary(path, CalibreSettings.locate(calibredb, home, searchPath, executable), sqlite, guiProcess)

object CalibreSettings:

  /** Where Calibre's installers put `calibredb`: the macOS app (system-wide or the user's), Homebrew on Apple silicon
    * and Intel, and the Linux installer's `/opt/calibre`.
    */
  val InstallDirs: List[String] = List(
    "/Applications/calibre.app/Contents/MacOS",
    "~/Applications/calibre.app/Contents/MacOS",
    "/opt/homebrew/bin",
    "/usr/local/bin",
    "/opt/calibre",
    "/usr/bin"
  )

  /** `name` as configured when it holds a `/`; otherwise the first executable `name` on `searchPath`, then in
    * [[InstallDirs]].
    */
  def locate(name: String, home: Option[String], searchPath: Option[String], executable: Path => Boolean): String =
    if name.contains('/') || name.isBlank then name
    else
      val dirs = searchPath.toList.flatMap(_.split(':').toList).filter(_.nonEmpty) ++
        InstallDirs.flatMap(d => if d.startsWith("~/") then home.map(_ + d.drop(1)).toList else List(d))
      dirs.iterator.map(d => Path.of(d, name)).find(executable).fold(name)(_.toString)
