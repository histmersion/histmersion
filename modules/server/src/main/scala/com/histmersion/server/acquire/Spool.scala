package com.histmersion.server.acquire

import com.histmersion.values.Citekey

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

import java.nio.file.{Files, Path, StandardOpenOption}

/** Where a fetched file waits for the converter. Configured (`acquire.spool-dir`); empty falls back to the JVM temp
  * directory, which is what a container gets.
  */
object Spool:
  /** The configured spool directory (`acquire.spool-dir`), or `<java.io.tmpdir>/histmersion-acquire` when it is empty —
    * absolute and normalised. A fetched file lives at `<base>/<citekey>/paper.<ext>`.
    */
  def base(dir: String): Path =
    Option(dir)
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(Path.of(_))
      .getOrElse(Path.of(System.getProperty("java.io.tmpdir"), "histmersion-acquire"))
      .toAbsolutePath
      .normalize

  /** `base.resolve(citekey)` is a path built from a caller-supplied string, so it gets BOTH guards: the citekey must
    * parse ([[Citekey]]), and the resolved directory must still be under the base. The second is not redundant — it
    * holds whatever a future caller does to the first, and a path-traversal guard should not rest on one regex being
    * right.
    */
  def write(dir: String, citekey: String, extension: String, bytes: Array[Byte]): Either[String, Path] =
    Citekey.parse(citekey).flatMap { _ =>
      scala.util
        .Try {
          val base = Spool.base(dir)
          val out = base.resolve(citekey).toAbsolutePath.normalize
          if !out.startsWith(base) then throw IllegalArgumentException(s"'$citekey' would escape the spool directory")
          Files.createDirectories(out)
          val f = out.resolve(s"paper.${AssetLinks.safeName(extension)}")
          Files.write(f, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
          f
        }
        .toEither
        .left
        .map(e => s"could not spool the file: ${e.getMessage}")
    }
