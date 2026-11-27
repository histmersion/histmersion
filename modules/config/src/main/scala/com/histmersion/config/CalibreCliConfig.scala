package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{movesTo, FeaturePlace}
import com.histmersion.lint.{tolerated, LintRule}

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
@movesTo(FeaturePlace.LibraryCalibre)
final case class CalibreCliConfig(
    libraryDir: CalibreCliConfig.LibraryDir,
    calibredb: CalibreCliConfig.Calibredb,
    sqlite: CalibreCliConfig.Sqlite,
    originalsDir: CalibreCliConfig.OriginalsDir
) derives ConfigReader:

  @tolerated(LintRule.Global, "pending G15 (no globals; everything from config)")
  private def expand(p: String): String =
    if p.startsWith("~") then System.getProperty("user.home") + p.drop(1) else p

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def library: CalibreCliConfig.LibraryDir = CalibreCliConfig.LibraryDir(expand(libraryDir.value))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def originals: Option[CalibreCliConfig.OriginalsDir] =
    Option(originalsDir).map(d => expand(d.value).trim).filter(_.nonEmpty).map(CalibreCliConfig.OriginalsDir(_))

object CalibreCliConfig:

  type LibraryDir = LibraryDir.T
  object LibraryDir extends Newtype[String, Pure]
  type Calibredb = Calibredb.T
  object Calibredb extends Newtype[String, Pure]
  type Sqlite = Sqlite.T
  object Sqlite extends Newtype[String, Pure]
  type OriginalsDir = OriginalsDir.T
  object OriginalsDir extends Newtype[String, Pure]
