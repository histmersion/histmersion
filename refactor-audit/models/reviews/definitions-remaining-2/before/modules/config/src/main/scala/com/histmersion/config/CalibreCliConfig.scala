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
final case class CalibreCliConfig(libraryDir: String, calibredb: String, sqlite: String, originalsDir: String)
    derives ConfigReader:

  private def expand(p: String): String =
    if p.startsWith("~") then System.getProperty("user.home") + p.drop(1) else p

  def library: String = expand(libraryDir)
  def originals: Option[String] = Option(originalsDir).map(expand).map(_.trim).filter(_.nonEmpty)
