package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** Host-side Calibre integration (the ONE local integration the CLI is allowed — Calibre lives on the user's machine,
  * not in the container).
  *
  * @param libraryDir
  *   the Calibre library root (`metadata.db` lives here); `~` is expanded
  * @param calibredb
  *   the `calibredb` executable — preferred reader/writer
  * @param sqlite
  *   the `sqlite3` executable — read-only fallback when the Calibre GUI holds the DB
  * @param originalsDir
  *   optional host directory holding the fetched originals as `<citekey>/paper.*`, an override for
  *   `calibre push`/`sync`, which otherwise download the original the server stored
  */
final case class CalibreCliConfig(libraryDir: CalibreCliConfig.LibraryDir, calibredb: CalibreCliConfig.Calibredb, sqlite: CalibreCliConfig.Sqlite, originalsDir: CalibreCliConfig.OriginalsDir)
    derives ConfigReader:

  private def expand(p: String): String =
    if p.startsWith("~") then System.getProperty("user.home") + p.drop(1) else p

  def library: com.histmersion.config.CalibreCliConfig.LibraryDir = CalibreCliConfig.LibraryDir(expand(libraryDir.value))
  def originals: Option[com.histmersion.config.CalibreCliConfig.OriginalsDir] = Option(originalsDir).map(d => expand(d.value).trim).filter(_.nonEmpty).map(CalibreCliConfig.OriginalsDir(_))

object CalibreCliConfig:

  type LibraryDir = LibraryDir.T
  object LibraryDir extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Calibredb = Calibredb.T
  object Calibredb extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Sqlite = Sqlite.T
  object Sqlite extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OriginalsDir = OriginalsDir.T
  object OriginalsDir extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
